package com.rometransit.ui.component;

import com.rometransit.service.map.TileManager;
import com.rometransit.ui.waypoint.Waypoint;
import com.rometransit.ui.waypoint.WaypointManager;
import com.rometransit.util.logging.Logger;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Native JavaFX map view component with OpenStreetMap tiles support
 * Features:
 * - Zoom and pan (drag) support
 * - Tile caching
 * - Waypoint rendering for routes
 * - Offline mode support
 */
public class NativeMapView extends StackPane {

    private static final String CACHE_DIR = System.getProperty("user.home") + "/.damose/map_cache";
    private static final int TILE_SIZE = 256;

    // Rome coordinates
    private static final double ROME_LAT = 41.9028;
    private static final double ROME_LON = 12.4964;

    private Canvas canvas;
    private GraphicsContext gc;
    private TileManager tileManager;
    private WaypointManager waypointManager;

    // Map state
    private double centerLat = ROME_LAT;
    private double centerLon = ROME_LON;
    private int zoom = 12;
    private int minZoom = 5;
    private int maxZoom = 18;

    // Tile cache with LRU eviction (keeps most recently used tiles)
    // Reduced to 500 for better memory management
    private static final int MAX_TILE_CACHE_SIZE = 500;
    private Map<String, Image> tileCache = new LinkedHashMap<String, Image>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_TILE_CACHE_SIZE;
        }
    };

    // Mouse dragging
    private double lastMouseX;
    private double lastMouseY;
    private boolean isDragging = false;
    private long lastRepaintTime = 0;
    private static final long REPAINT_THROTTLE_MS = 16; // ~60 FPS

    // View dimensions
    private double viewWidth;
    private double viewHeight;

    // Rendering optimization
    private boolean needsRepaint = false;

    /**
     * Create a new map view with specified dimensions
     */
    public NativeMapView(double width, double height) {
        this.viewWidth = width;
        this.viewHeight = height;

        // Initialize main canvas
        canvas = new Canvas(width, height);
        gc = canvas.getGraphicsContext2D();

        // Initialize managers
        try {
            this.tileManager = new TileManager();
            this.waypointManager = new WaypointManager();
        } catch (Exception e) {
            Logger.log("Error initializing map managers: " + e.getMessage());
        }

        // Add canvas to pane
        getChildren().add(canvas);

        // Setup mouse handlers
        setupMouseHandlers();

        // Initial render
        repaint();

        // Preload visible area after initialization
        preloadVisibleArea();

        Logger.log("NativeMapView initialized: " + width + "x" + height + " @ zoom " + zoom + " (advanced rendering enabled)");
    }

    /**
     * Setup mouse event handlers for pan and zoom
     */
    private void setupMouseHandlers() {
        // Mouse press - start drag
        canvas.setOnMousePressed(this::handleMousePressed);

        // Mouse drag - pan map
        canvas.setOnMouseDragged(this::handleMouseDragged);

        // Mouse release - end drag
        canvas.setOnMouseReleased(this::handleMouseReleased);

        // Scroll - zoom
        canvas.setOnScroll(this::handleScroll);
    }

    private void handleMousePressed(MouseEvent event) {
        lastMouseX = event.getX();
        lastMouseY = event.getY();
        isDragging = true;
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!isDragging) return;

        double deltaX = event.getX() - lastMouseX;
        double deltaY = event.getY() - lastMouseY;

        // Update mouse position first for accurate delta in next frame
        lastMouseX = event.getX();
        lastMouseY = event.getY();

        // Convert pixel delta to map coordinate delta
        // Mouse drag RIGHT (positive deltaX) should move map LEFT (decrease centerLon)
        // Mouse drag DOWN (positive deltaY) should move map UP (decrease centerLat)
        double tilesAtZoom = Math.pow(2, zoom);
        double degreesPerPixel = 360.0 / (TILE_SIZE * tilesAtZoom);

        // Apply delta (inverted because dragging map != moving viewport)
        centerLon -= deltaX * degreesPerPixel;
        centerLat += deltaY * degreesPerPixel / Math.cos(Math.toRadians(centerLat));

        // Clamp coordinates
        centerLat = Math.max(-85, Math.min(85, centerLat));
        centerLon = ((centerLon + 180) % 360) - 180;

        // Throttled repaint for smooth drag (60 FPS max)
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRepaintTime >= REPAINT_THROTTLE_MS) {
            repaint();
            lastRepaintTime = currentTime;
        } else {
            needsRepaint = true;
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        isDragging = false;

        // Final repaint if needed
        if (needsRepaint) {
            needsRepaint = false;
            repaint();
        }

        // Preload surrounding tiles after drag completes
        preloadVisibleArea();
    }

    private void handleScroll(ScrollEvent event) {
        // Zoom in/out on scroll
        int oldZoom = zoom;

        if (event.getDeltaY() > 0) {
            zoom = Math.min(maxZoom, zoom + 1);
        } else {
            zoom = Math.max(minZoom, zoom - 1);
        }

        if (zoom != oldZoom) {
            Logger.log("Zoom changed: " + oldZoom + " -> " + zoom);
            repaint();
            // Preload tiles for adjacent zoom levels for smoother zoom transitions
            preloadAdjacentZoomLevels();
        }

        event.consume();
    }

    /**
     * Repaint the map view with optimized rendering
     * Direct rendering to canvas for better memory efficiency
     */
    public void repaint() {
        try {
            // STEP 1: Clear canvas with background color
            gc.setFill(Color.web("#E0E0E0"));
            gc.fillRect(0, 0, viewWidth, viewHeight);

            // STEP 2: Enable image smoothing for better quality
            gc.setImageSmoothing(true);

            // STEP 3: Draw tiles directly on canvas
            drawTiles(gc);

            // STEP 4: Draw waypoints on canvas (same layer as tiles)
            drawWaypoints(gc);

        } catch (OutOfMemoryError e) {
            // Handle OOM gracefully
            Logger.log("Out of memory during repaint - clearing cache");
            clearTileCache();
            System.gc(); // Suggest garbage collection
        } catch (Exception e) {
            Logger.log("Error repainting map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Draw map tiles on specified graphics context with optimized rendering
     * @param g Graphics context to draw on
     */
    private void drawTiles(GraphicsContext g) {
        double scale = getScale();

        // Calculate center position in tile coordinates (with fractional part for smooth scrolling)
        double centerTileX = lonToTileXDouble(centerLon, zoom);
        double centerTileY = latToTileYDouble(centerLat, zoom);

        // Get integer tile coordinates
        int centerTileXInt = (int) Math.floor(centerTileX);
        int centerTileYInt = (int) Math.floor(centerTileY);

        // Calculate fractional offset for sub-pixel positioning
        double fracX = centerTileX - centerTileXInt;
        double fracY = centerTileY - centerTileYInt;

        // Calculate how many tiles we need to cover the view (with 1 tile padding)
        int tilesX = (int) Math.ceil(viewWidth / (TILE_SIZE * scale)) + 2;
        int tilesY = (int) Math.ceil(viewHeight / (TILE_SIZE * scale)) + 2;

        // Render tiles from back to front, center outward for better perceived performance
        for (int dx = -tilesX/2; dx <= tilesX/2; dx++) {
            for (int dy = -tilesY/2; dy <= tilesY/2; dy++) {
                int tileX = centerTileXInt + dx;
                int tileY = centerTileYInt + dy;

                // Skip tiles outside valid range
                int maxTile = (1 << zoom);
                if (tileY < 0 || tileY >= maxTile) continue;

                // Wrap longitude tiles
                tileX = ((tileX % maxTile) + maxTile) % maxTile;

                // Calculate screen position with sub-pixel precision
                double screenX = viewWidth/2 + (dx - fracX) * TILE_SIZE * scale;
                double screenY = viewHeight/2 + (dy - fracY) * TILE_SIZE * scale;

                // Skip tiles outside visible area (culling optimization)
                if (screenX + TILE_SIZE * scale < 0 || screenX > viewWidth ||
                    screenY + TILE_SIZE * scale < 0 || screenY > viewHeight) {
                    continue;
                }

                // Load and draw tile (try memory cache first, then disk)
                Image tile = loadTile(zoom, tileX, tileY);
                if (tile != null && !tile.isError()) {
                    g.drawImage(tile, screenX, screenY, TILE_SIZE * scale, TILE_SIZE * scale);
                } else {
                    // Draw placeholder for missing tile with loading indicator
                    g.setFill(Color.web("#F5F5F5"));
                    g.fillRect(screenX, screenY, TILE_SIZE * scale, TILE_SIZE * scale);
                    g.setStroke(Color.web("#CCCCCC"));
                    g.setLineWidth(1);
                    g.strokeRect(screenX, screenY, TILE_SIZE * scale, TILE_SIZE * scale);

                    // Trigger async tile load from TileManager (high priority for visible tiles)
                    if (tileManager != null) {
                        int finalTileX = tileX;
                        int finalTileY = tileY;
                        tileManager.getTile(zoom, tileX, tileY, 100).thenAccept(mapTile -> {
                            // When tile loads, add to cache and trigger repaint
                            if (mapTile != null && mapTile.isLoaded()) {
                                String key = zoom + "/" + finalTileX + "/" + finalTileY;
                                tileCache.put(key, mapTile.getImage());
                                // Schedule repaint on JavaFX thread
                                javafx.application.Platform.runLater(this::repaint);
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Draw waypoints (stops, routes, vehicles) directly on map canvas
     * @param g Graphics context to draw on
     *
     * Waypoints (red lines, blue stops, green vehicles) are rendered
     * DIRECTLY on the same canvas as the map tiles, ensuring they move
     * smoothly with the map and are not separate overlay layers
     */
    private void drawWaypoints(GraphicsContext g) {
        if (waypointManager == null) return;

        // Render all waypoints on the same canvas as tiles
        // This ensures smooth integrated rendering during drag operations
        waypointManager.renderWaypoints(g, zoom, this::latLonToScreen);
    }

    /**
     * Load a tile from cache or disk with LRU caching
     * Fast synchronous load - only returns already cached/loaded tiles
     * @return Image tile or null if not available
     */
    private Image loadTile(int z, int x, int y) {
        String tileKey = z + "/" + x + "/" + y;

        // FAST PATH: Check memory cache first (LRU - auto-evicts old tiles)
        if (tileCache.containsKey(tileKey)) {
            return tileCache.get(tileKey);
        }

        // MEDIUM PATH: Try to load from disk cache (synchronous for visible tiles)
        Path tilePath = Paths.get(CACHE_DIR, z + "/" + x + "/" + y + ".png");
        if (Files.exists(tilePath)) {
            try {
                // Load with memory-efficient settings
                // preserveRatio=true, smooth=true, backgroundLoading=false for better memory control
                Image image = new Image(tilePath.toUri().toString(), TILE_SIZE, TILE_SIZE, true, true, false);

                // Only add to cache if loaded successfully
                if (!image.isError()) {
                    tileCache.put(tileKey, image);
                    return image;
                }
            } catch (OutOfMemoryError e) {
                // Out of memory - clear cache and retry once
                Logger.log("OOM loading tile - clearing cache");
                clearTileCache();
                System.gc();
                return null;
            } catch (Exception e) {
                // Failed to load tile - silent fail
            }
        }

        return null; // Tile not available yet
    }

    /**
     * Convert latitude/longitude to screen coordinates
     */
    private double[] latLonToScreen(double lat, double lon) {
        double scale = getScale();

        // Convert to tile coordinates
        double tileX = lonToTileXDouble(lon, zoom);
        double tileY = latToTileYDouble(lat, zoom);

        double centerTileX = lonToTileXDouble(centerLon, zoom);
        double centerTileY = latToTileYDouble(centerLat, zoom);

        // Calculate screen position
        double screenX = viewWidth/2 + (tileX - centerTileX) * TILE_SIZE * scale;
        double screenY = viewHeight/2 + (tileY - centerTileY) * TILE_SIZE * scale;

        return new double[]{screenX, screenY};
    }

    /**
     * Get current zoom scale factor
     */
    private double getScale() {
        return 1.0; // Can be adjusted for retina displays or custom scaling
    }

    /**
     * Coordinate conversion utilities
     */
    private int lonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * (1 << zoom));
    }

    private int latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom));
    }

    private double lonToTileXDouble(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * (1 << zoom);
    }

    private double latToTileYDouble(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << zoom);
    }

    /**
     * Public API methods
     */

    public void centerOnRome() {
        centerLat = ROME_LAT;
        centerLon = ROME_LON;
        zoom = 12;
        repaint();
        Logger.log("Map centered on Rome");
    }

    public void setCenter(double lat, double lon) {
        this.centerLat = lat;
        this.centerLon = lon;
        repaint();
    }

    public void setZoom(int zoom) {
        this.zoom = Math.max(minZoom, Math.min(maxZoom, zoom));
        repaint();
    }

    public void addWaypoint(Waypoint waypoint) {
        if (waypointManager != null) {
            waypointManager.addWaypoint(waypoint);
            repaint();
        }
    }

    public void removeWaypoint(Waypoint waypoint) {
        if (waypointManager != null && waypoint != null) {
            waypointManager.removeWaypoint(waypoint.getId());
            repaint();
        }
    }

    public void clearWaypoints() {
        if (waypointManager != null) {
            waypointManager.clearWaypoints();
            repaint();
        }
        Logger.log("Waypoints cleared");
    }

    public void fitWaypointsInView() {
        if (waypointManager == null) return;

        // This method fits all waypoints into the current view
        // For now, just repaint - can be enhanced to calculate bounds
        repaint();
        Logger.log("Fitting waypoints in view");
    }

    public void markForRepaint() {
        // Mark the map for repaint on next frame
        repaint();
    }

    /**
     * Clear the tile cache to free memory
     */
    private void clearTileCache() {
        int size = tileCache.size();
        tileCache.clear();
        Logger.log("Cleared tile cache: " + size + " tiles removed");
    }

    public void shutdown() {
        // Clear caches
        clearTileCache();

        if (waypointManager != null) {
            waypointManager.clearWaypoints();
        }

        if (tileManager != null) {
            tileManager.shutdown();
        }

        Logger.log("NativeMapView shutdown complete");
    }

    // Getters
    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLon() {
        return centerLon;
    }

    public int getZoom() {
        return zoom;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }

    /**
     * Preload tiles for adjacent zoom levels for smoother zoom transitions
     * This runs asynchronously in the background
     */
    private void preloadAdjacentZoomLevels() {
        // Disabled to reduce memory usage - tiles will load on-demand
        // Preloading adjacent zoom levels was causing OOM errors
    }

    /**
     * Preload visible tiles and surrounding area for current zoom
     * This improves panning performance
     */
    public void preloadVisibleArea() {
        if (tileManager == null) return;

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                int centerTileX = lonToTileX(centerLon, zoom);
                int centerTileY = latToTileY(centerLat, zoom);

                // Reduced preload area - only immediate visible tiles, no border
                int tilesX = (int) Math.ceil(viewWidth / TILE_SIZE) + 1;
                int tilesY = (int) Math.ceil(viewHeight / TILE_SIZE) + 1;

                // Preload only visible tiles with low priority to avoid OOM
                int preloadCount = 0;
                int maxPreload = 20; // Limit concurrent preloads

                for (int dx = -tilesX/2; dx <= tilesX/2 && preloadCount < maxPreload; dx++) {
                    for (int dy = -tilesY/2; dy <= tilesY/2 && preloadCount < maxPreload; dy++) {
                        int tileX = centerTileX + dx;
                        int tileY = centerTileY + dy;
                        tileManager.getTile(zoom, tileX, tileY, 30); // Lower priority
                        preloadCount++;
                    }
                }

                Logger.log("Preloading visible area: limited to " + preloadCount + " tiles at zoom " + zoom);
            } catch (Exception e) {
                Logger.log("Error preloading visible area: " + e.getMessage());
            }
        });
    }

    /**
     * Warmup cache for Rome at multiple zoom levels
     * Call this during initialization for better offline experience
     */
    public void warmupCache() {
        if (tileManager != null) {
            tileManager.warmupCacheForRome();
        }
    }
}
