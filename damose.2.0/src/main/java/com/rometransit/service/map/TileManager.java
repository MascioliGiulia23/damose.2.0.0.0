package com.rometransit.service.map;

import com.rometransit.model.dto.map.MapTile;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.image.Image;

import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manages loading, caching and rendering of map tiles with high performance
 * Features: Priority queue, WeakReference caching, connection caching, tile expiration
 */
public class TileManager {

    private static final String TILE_SERVER_URL = "https://tile.openstreetmap.org";
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.damose/map_cache";
    private static final int MAX_CACHE_SIZE = 500; // Reduced for better memory management
    private static final int TILE_SIZE = 256;
    private static final long TILE_EXPIRATION_DAYS = 30;
    private static final long CONNECTION_CHECK_CACHE_MS = 30000; // 30s

    private final Path cacheDirectory;
    private final Map<String, MapTile> tileCache;
    private final Map<String, WeakReference<Image>> imageCache;
    private final PriorityBlockingQueue<TileRequest> downloadQueue;
    private final ExecutorService downloadExecutor;
    private final ExecutorService diskExecutor;
    private volatile boolean offlineMode = false;

    // Connection check cache
    private volatile boolean lastConnectionStatus = true;
    private volatile long lastConnectionCheckTime = 0;

    // Priority tile request
    private static class TileRequest implements Comparable<TileRequest> {
        final int zoom, x, y;
        final int priority;
        final CompletableFuture<MapTile> future;

        TileRequest(int zoom, int x, int y, int priority) {
            this.zoom = zoom;
            this.x = x;
            this.y = y;
            this.priority = priority;
            this.future = new CompletableFuture<>();
        }

        @Override
        public int compareTo(TileRequest other) {
            return Integer.compare(other.priority, this.priority); // Higher priority first
        }
    }

    public TileManager() {
        // LRU cache with access-order (true parameter)
        this.tileCache = Collections.synchronizedMap(
            new LinkedHashMap<String, MapTile>(MAX_CACHE_SIZE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MapTile> eldest) {
                    return size() > MAX_CACHE_SIZE;
                }
            }
        );

        // WeakReference image cache for memory management
        this.imageCache = new ConcurrentHashMap<>();

        // Priority queue for tile downloads
        this.downloadQueue = new PriorityBlockingQueue<>();

        // Reduced thread pools to limit concurrent memory usage
        this.downloadExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "TileDownloader");
            t.setDaemon(false);
            return t;
        });
        this.diskExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "TileDiskLoader");
            t.setDaemon(false);
            return t;
        });

        // Initialize cache directory
        try {
            cacheDirectory = Paths.get(CACHE_DIR);
            Files.createDirectories(cacheDirectory);
            System.out.println("🗺️ Tile cache initialized at: " + cacheDirectory.toAbsolutePath());

            // Start download queue processor
            startDownloadQueueProcessor();

            // Clean expired tiles in background
            cleanExpiredTiles();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize tile cache", e);
        }
    }

    private void startDownloadQueueProcessor() {
        // Reduced concurrent downloads from 4 to 2 to limit memory usage
        for (int i = 0; i < 2; i++) {
            downloadExecutor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        TileRequest request = downloadQueue.take();
                        processTileDownload(request);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (OutOfMemoryError e) {
                        System.err.println("OOM in tile download processor - clearing caches");
                        imageCache.clear();
                        System.gc();
                    }
                }
            });
        }
    }

    /**
     * Get a tile synchronously (for rendering loop)
     */
    public MapTile getTileSync(int zoom, int x, int y) {
        String tileKey = String.format("%d/%d/%d", zoom, x, y);

        // Check memory cache
        MapTile cachedTile = tileCache.get(tileKey);
        if (cachedTile != null && cachedTile.isLoaded()) {
            return cachedTile;
        }

        // Try to get from weak reference cache
        WeakReference<Image> weakRef = imageCache.get(tileKey);
        if (weakRef != null) {
            Image image = weakRef.get();
            if (image != null) {
                if (cachedTile == null) {
                    cachedTile = new MapTile(zoom, x, y);
                    tileCache.put(tileKey, cachedTile);
                }
                cachedTile.setImage(image);
                return cachedTile;
            }
        }

        // Create new tile if needed
        if (cachedTile == null) {
            cachedTile = new MapTile(zoom, x, y);
            tileCache.put(tileKey, cachedTile);

            // Trigger async load with high priority
            getTile(zoom, x, y, 100); // High priority for visible tiles
        }

        return cachedTile;
    }

    /**
     * Get a tile asynchronously with priority
     */
    public CompletableFuture<MapTile> getTile(int zoom, int x, int y) {
        return getTile(zoom, x, y, 50); // Medium priority
    }

    /**
     * Get a tile with specified priority
     */
    public CompletableFuture<MapTile> getTile(int zoom, int x, int y, int priority) {
        String tileKey = String.format("%d/%d/%d", zoom, x, y);

        // Check if tile is already in memory cache
        MapTile cachedTile = tileCache.get(tileKey);
        if (cachedTile != null && cachedTile.isLoaded()) {
            return CompletableFuture.completedFuture(cachedTile);
        }

        // Create new tile if not exists
        if (cachedTile == null) {
            cachedTile = new MapTile(zoom, x, y);
            tileCache.put(tileKey, cachedTile);
        }

        final MapTile tile = cachedTile;

        // Avoid loading the same tile multiple times
        if (tile.isLoading()) {
            return waitForTileLoad(tile);
        }

        tile.setLoading(true);

        TileRequest request = new TileRequest(zoom, x, y, priority);

        // Try loading from disk cache first
        diskExecutor.submit(() -> {
            try {
                Image diskImage = loadTileFromDisk(zoom, x, y);
                if (diskImage != null) {
                    tile.setImage(diskImage);
                    imageCache.put(tileKey, new WeakReference<>(diskImage));
                    request.future.complete(tile);
                    return;
                }

                // If not in disk cache and not in offline mode, queue for download
                if (!offlineMode && hasInternetConnection()) {
                    downloadQueue.offer(request);
                } else {
                    tile.setFailed(true);
                    request.future.complete(tile);
                }
            } catch (Exception e) {
                tile.setFailed(true);
                request.future.complete(tile);
            }
        });

        return request.future;
    }

    private void processTileDownload(TileRequest request) {
        try {
            MapTile tile = tileCache.get(String.format("%d/%d/%d", request.zoom, request.x, request.y));
            if (tile == null || tile.isLoaded()) {
                request.future.complete(tile);
                return;
            }

            String tileUrl = String.format("%s/%d/%d/%d.png",
                TILE_SERVER_URL, request.zoom, request.x, request.y);

            URL url = new URL(tileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Damose Rome Transit App/2.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            if (connection.getResponseCode() == 200) {
                try (InputStream inputStream = connection.getInputStream()) {
                    // Limit max size to prevent OOM
                    byte[] imageData = inputStream.readNBytes(512 * 1024); // Max 512KB per tile

                    // Save to disk cache
                    saveTileToDisk(request.zoom, request.x, request.y, imageData);

                    // Create JavaFX Image with maximum memory efficiency
                    // smooth=false reduces memory usage but slightly reduces quality
                    ByteArrayInputStream byteStream = new ByteArrayInputStream(imageData);
                    Image image = new Image(byteStream, TILE_SIZE, TILE_SIZE, false, false);

                    if (!image.isError()) {
                        tile.setImage(image);
                        imageCache.put(tile.getTileKey(), new WeakReference<>(image));
                    } else {
                        tile.setFailed(true);
                    }
                    request.future.complete(tile);
                }
            } else {
                tile.setFailed(true);
                request.future.complete(tile);
            }
        } catch (OutOfMemoryError e) {
            System.err.println("OOM downloading tile " + request.zoom + "/" + request.x + "/" + request.y);
            imageCache.clear();
            System.gc();
            MapTile tile = tileCache.get(String.format("%d/%d/%d", request.zoom, request.x, request.y));
            if (tile != null) {
                tile.setFailed(true);
            }
            request.future.complete(tile);
        } catch (Exception e) {
            MapTile tile = tileCache.get(String.format("%d/%d/%d", request.zoom, request.x, request.y));
            if (tile != null) {
                tile.setFailed(true);
            }
            request.future.complete(tile);
        }
    }

    private CompletableFuture<MapTile> waitForTileLoad(MapTile tile) {
        CompletableFuture<MapTile> future = new CompletableFuture<>();

        // Poll until tile is loaded
        Task<Void> waitTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                while (tile.isLoading() && !isCancelled()) {
                    Thread.sleep(50);
                }
                return null;
            }

            @Override
            protected void succeeded() {
                future.complete(tile);
            }

            @Override
            protected void failed() {
                future.complete(tile);
            }
        };

        Thread waitThread = new Thread(waitTask);
        waitThread.setDaemon(true);
        waitThread.start();

        return future;
    }


    private Image loadTileFromDisk(int zoom, int x, int y) {
        try {
            Path tilePath = getTilePath(zoom, x, y);
            if (Files.exists(tilePath) && Files.size(tilePath) > 0) {
                // Skip files that are too large (corrupted or invalid)
                long fileSize = Files.size(tilePath);
                if (fileSize > 512 * 1024) { // Max 512KB
                    Files.deleteIfExists(tilePath);
                    return null;
                }

                // Check if tile is expired
                FileTime fileTime = Files.getLastModifiedTime(tilePath);
                long ageDays = java.time.Duration.between(
                    fileTime.toInstant(), Instant.now()).toDays();

                if (ageDays > TILE_EXPIRATION_DAYS) {
                    // Tile expired, delete it
                    Files.deleteIfExists(tilePath);
                    return null;
                }

                try (InputStream inputStream = Files.newInputStream(tilePath)) {
                    // Memory-efficient loading: smooth=false
                    return new Image(inputStream, TILE_SIZE, TILE_SIZE, false, false);
                }
            }
        } catch (OutOfMemoryError e) {
            System.err.println("OOM loading tile from disk: " + zoom + "/" + x + "/" + y);
            imageCache.clear();
            System.gc();
        } catch (Exception e) {
            // Silently fail for disk loading
        }
        return null;
    }

    private void saveTileToDisk(int zoom, int x, int y, byte[] imageData) {
        try {
            Path tilePath = getTilePath(zoom, x, y);
            Files.createDirectories(tilePath.getParent());
            Files.write(tilePath, imageData);
        } catch (Exception e) {
            // Silently fail for disk saving
        }
    }

    private Path getTilePath(int zoom, int x, int y) {
        return cacheDirectory.resolve(String.format("%d/%d/%d.png", zoom, x, y));
    }

    private boolean hasInternetConnection() {
        // Use cached connection status with TTL
        long now = System.currentTimeMillis();
        if ((now - lastConnectionCheckTime) < CONNECTION_CHECK_CACHE_MS) {
            return lastConnectionStatus;
        }

        // Check connection in background
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1000); // Reduced from 3000
            connection.setReadTimeout(1000);
            connection.setRequestMethod("HEAD");
            lastConnectionStatus = connection.getResponseCode() == 200;
        } catch (Exception e) {
            lastConnectionStatus = false;
        }

        lastConnectionCheckTime = now;
        return lastConnectionStatus;
    }

    private void cleanExpiredTiles() {
        diskExecutor.submit(() -> {
            try {
                Instant cutoff = Instant.now().minus(TILE_EXPIRATION_DAYS, java.time.temporal.ChronoUnit.DAYS);
                Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        try {
                            FileTime fileTime = Files.getLastModifiedTime(path);
                            return fileTime.toInstant().isBefore(cutoff);
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
                System.out.println("🧹 Cleaned expired tiles from cache");
            } catch (IOException e) {
                System.err.println("⚠️ Failed to clean expired tiles: " + e.getMessage());
            }
        });
    }

    /**
     * Cache warmup - preload tiles for Rome at multiple zoom levels
     * Reduced to prevent memory issues
     */
    public void warmupCacheForRome() {
        diskExecutor.submit(() -> {
            System.out.println("🔥 Starting cache warmup for Rome (reduced for memory efficiency)...");
            int totalTiles = 0;

            // Rome coordinates
            double romeLat = 41.9028;
            double romeLon = 12.4964;

            // Reduced preload - only essential zoom levels with smaller radius
            int[] zoomLevels = {11, 12, 13}; // Most used zoom levels
            int[] radiuses = {2, 2, 3}; // Smaller radius to reduce memory usage

            for (int i = 0; i < zoomLevels.length; i++) {
                int zoom = zoomLevels[i];
                int radius = radiuses[i];

                System.out.println("  📦 Warming up zoom " + zoom + " (radius " + radius + ")...");

                int centerX = lonToTileX(romeLon, zoom);
                int centerY = latToTileY(romeLat, zoom);

                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int y = centerY - radius; y <= centerY + radius; y++) {
                        if (x >= 0 && y >= 0 && y < (1 << zoom)) {
                            // Very low priority background download
                            getTile(zoom, x, y, 5);
                            totalTiles++;

                            // Add small delay to avoid overwhelming memory
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            }

            System.out.println("✅ Cache warmup completed: " + totalTiles + " tiles queued");
        });
    }

    /**
     * Preload tiles for a given area
     */
    public CompletableFuture<Integer> preloadTiles(double lat, double lon, int zoom, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            int centerX = lonToTileX(lon, zoom);
            int centerY = latToTileY(lat, zoom);
            int tilesLoaded = 0;

            CompletableFuture<?>[] futures = new CompletableFuture[(radius * 2 + 1) * (radius * 2 + 1)];
            int futureIndex = 0;

            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int y = centerY - radius; y <= centerY + radius; y++) {
                    futures[futureIndex++] = getTile(zoom, x, y);
                }
            }

            // Wait for all tiles to complete
            CompletableFuture.allOf(futures).join();

            // Count successful loads
            for (CompletableFuture<?> future : futures) {
                try {
                    MapTile tile = (MapTile) future.get();
                    if (tile.isLoaded()) {
                        tilesLoaded++;
                    }
                } catch (Exception e) {
                    // Count failed tiles
                }
            }

            return tilesLoaded;
        });
    }

    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * Math.pow(2.0, zoom));
    }

    private int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - asinh(Math.tan(latRad)) / Math.PI) / 2.0 * Math.pow(2.0, zoom));
    }

    public void setOfflineMode(boolean offline) {
        this.offlineMode = offline;
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    public int getTileSize() {
        return TILE_SIZE;
    }

    public String getCacheStats() {
        try {
            long totalSize = Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();

            long fileCount = Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .count();

            return String.format("%d tiles, %.2f MB (Memory: %d)",
                fileCount, totalSize / (1024.0 * 1024.0), tileCache.size());
        } catch (IOException e) {
            return "Error reading cache stats";
        }
    }

    public void clearCache() {
        tileCache.clear();
        try {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    });
            System.out.println("✅ Tile cache cleared");
        } catch (IOException e) {
            System.err.println("❌ Failed to clear cache: " + e.getMessage());
        }
    }

    public void shutdown() {
        downloadExecutor.shutdown();
        diskExecutor.shutdown();
        try {
            if (!downloadExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                downloadExecutor.shutdownNow();
            }
            if (!diskExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                diskExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            diskExecutor.shutdownNow();
        }
    }

    private static double asinh(double x) {
        return Math.log(x + Math.sqrt(x * x + 1.0));
    }
}