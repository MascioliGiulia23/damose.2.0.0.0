package com.rometransit.service.map;

import com.rometransit.model.dto.map.MapCoordinate;
import com.rometransit.model.dto.map.MapTile;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing OpenStreetMap integration with offline caching
 */
public class MapService {

    private static final String TILE_SERVER_URL = "https://tile.openstreetmap.org";
    private static final String CACHE_DIR = System.getProperty("user.home") + "/.damose/map_cache";
    private static final int MAX_ZOOM = 18;

    // Rome coordinates
    private static final double ROME_LAT = 41.9028;
    private static final double ROME_LON = 12.4964;
    private static final int DEFAULT_ZOOM = 12;

    private Path cacheDirectory;
    private boolean offlineMode = false;

    public MapService() {
        initializeCache();
    }

    private void initializeCache() {
        try {
            cacheDirectory = Paths.get(CACHE_DIR);
            Files.createDirectories(cacheDirectory);
            System.out.println("🗺️ Map cache initialized at: " + cacheDirectory.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Failed to initialize map cache: " + e.getMessage());
            cacheDirectory = null;
        }
    }



    private String getStopColor(Stop stop) {
        if (stop.getStopName() == null) return "#e74c3c"; // Red default

        String stopName = stop.getStopName().toUpperCase();

        // Metro lines
        if (stopName.contains("(MA)") || stopName.contains("METRO A")) {
            return "#f39c12"; // Orange for Metro A
        } else if (stopName.contains("(MB)") || stopName.contains("METRO B") || stopName.contains("(MB1)")) {
            return "#3498db"; // Blue for Metro B/B1
        } else if (stopName.contains("(MC)") || stopName.contains("METRO C")) {
            return "#27ae60"; // Green for Metro C
        }

        // Trams
        if (stopName.contains("TRAM") || stopName.contains("(TR)")) {
            return "#9b59b6"; // Purple for trams
        }

        // Trains
        if (stopName.contains("(FL)") || stopName.contains("STAZIONE") || stopName.contains("(FS)")) {
            return "#34495e"; // Dark blue for trains
        }

        return "#e74c3c"; // Red for bus stops
    }

    private int getStopRadius(Stop stop) {
        if (stop.getStopName() == null) return 4;

        String stopName = stop.getStopName().toUpperCase();

        // Metro stations are larger
        if (stopName.contains("(MA)") || stopName.contains("(MB)") || stopName.contains("(MC)") ||
            stopName.contains("METRO")) {
            return 6;
        }

        // Train stations are also larger
        if (stopName.contains("STAZIONE") || stopName.contains("(FL)") || stopName.contains("(FS)")) {
            return 6;
        }

        return 4; // Default size for bus stops
    }

    private String escapeJavaScript(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                  .replace("'", "\\'")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }

    /**
     * Generate offline map HTML
     */


    private String getOfflineStopType(Stop stop) {
        if (stop.getStopName() == null) return "Bus";

        String stopName = stop.getStopName().toUpperCase();

        if (stopName.contains("(MA)") || stopName.contains("METRO A")) return "Metro A";
        if (stopName.contains("(MB)") || stopName.contains("METRO B")) return "Metro B";
        if (stopName.contains("(MC)") || stopName.contains("METRO C")) return "Metro C";
        if (stopName.contains("(FL)") || stopName.contains("STAZIONE")) return "Treno";
        if (stopName.contains("TRAM")) return "Tram";

        return "Bus";
    }

    private String getOfflineTypeClass(Stop stop) {
        String type = getOfflineStopType(stop);
        switch (type) {
            case "Metro A": return "metro-a";
            case "Metro B": return "metro-b";
            case "Metro C": return "metro-c";
            case "Treno": return "train";
            default: return "bus";
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#x27;");
    }

    /**
     * Download and cache map tiles for offline use
     */
    public CompletableFuture<Integer> cacheMapTiles(double lat, double lon, int zoom, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                System.out.println("🗺️ Starting map tile caching for zoom " + zoom + " with radius " + radius);

                int tilesDownloaded = 0;
                int totalTiles = 0;

                // Calculate tile bounds
                int centerX = lonToTileX(lon, zoom);
                int centerY = latToTileY(lat, zoom);

                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int y = centerY - radius; y <= centerY + radius; y++) {
                        totalTiles++;
                        if (downloadTile(zoom, x, y)) {
                            tilesDownloaded++;
                        }

                        // Progress update every 25 tiles
                        if (totalTiles % 25 == 0) {
                            System.out.println("  📦 Downloaded " + tilesDownloaded + "/" + totalTiles + " tiles");
                        }
                    }
                }

                System.out.println("✅ Map caching completed: " + tilesDownloaded + "/" + totalTiles + " tiles cached");
                return tilesDownloaded;

            } catch (Exception e) {
                System.err.println("❌ Map caching failed: " + e.getMessage());
                return 0;
            }
        });
    }

    private boolean downloadTile(int zoom, int x, int y) {
        try {
            // Check if tile already cached
            Path tilePath = getTilePath(zoom, x, y);
            if (Files.exists(tilePath) && Files.size(tilePath) > 0) {
                return true; // Already cached
            }

            // Download tile
            String tileUrl = String.format("%s/%d/%d/%d.png", TILE_SERVER_URL, zoom, x, y);
            URL url = new URL(tileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "Damose Rome Transit App/2.0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);

            if (connection.getResponseCode() == 200) {
                // Create directories if needed
                Files.createDirectories(tilePath.getParent());

                // Save tile
                try (InputStream in = connection.getInputStream();
                     OutputStream out = Files.newOutputStream(tilePath)) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                // Small delay to be respectful to the tile server
                Thread.sleep(50);
                return true;
            }
        } catch (Exception e) {
            // Silently fail for individual tiles
        }
        return false;
    }

    private Path getTilePath(int zoom, int x, int y) {
        return cacheDirectory.resolve(String.format("%d/%d/%d.png", zoom, x, y));
    }

    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * Math.pow(2.0, zoom));
    }

    private int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - Math.sinh(Math.tan(latRad)) / Math.PI) / 2.0 * Math.pow(2.0, zoom));
    }

    /**
     * Check internet connectivity
     */
    public boolean hasInternetConnection() {
        try {
            URL url = new URL("https://www.google.com");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestMethod("HEAD");

            int responseCode = connection.getResponseCode();
            return responseCode == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        if (cacheDirectory == null) {
            return "Cache non inizializzata";
        }

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

            return String.format("%d tiles, %.2f MB", fileCount, totalSize / (1024.0 * 1024.0));
        } catch (IOException e) {
            return "Errore lettura cache";
        }
    }

    /**
     * Clear map cache
     */
    public void clearCache() {
        if (cacheDirectory == null) return;

        try {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore individual file errors
                        }
                    });
            System.out.println("✅ Map cache cleared");
        } catch (IOException e) {
            System.err.println("❌ Failed to clear cache: " + e.getMessage());
        }
    }

    // Getters and setters
    public boolean isOfflineMode() {
        return offlineMode;
    }

    public void setOfflineMode(boolean offlineMode) {
        this.offlineMode = offlineMode;
    }
}