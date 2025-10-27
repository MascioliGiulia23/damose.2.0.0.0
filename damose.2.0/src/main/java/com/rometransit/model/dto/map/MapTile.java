package com.rometransit.model.dto.map;

import javafx.scene.image.Image;

/**
 * Represents a map tile with its coordinates and image data
 */
public class MapTile {
    private final int zoom;
    private final int x;
    private final int y;
    private Image image;
    private boolean loading;
    private boolean failed;
    private long loadedTime; // For fade-in animation
    private double opacity = 0.0; // For smooth fade-in

    public MapTile(int zoom, int x, int y) {
        this.zoom = zoom;
        this.x = x;
        this.y = y;
        this.loading = false;
        this.failed = false;
        this.loadedTime = 0;
    }

    public int getZoom() {
        return zoom;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Image getImage() {
        return image;
    }

    public void setImage(Image image) {
        this.image = image;
        this.loading = false;
        this.loadedTime = System.currentTimeMillis();
        this.opacity = 0.0; // Start fade-in from 0
    }

    public double getOpacity() {
        return opacity;
    }

    public void updateOpacity() {
        if (loadedTime > 0 && opacity < 1.0) {
            long elapsed = System.currentTimeMillis() - loadedTime;
            // Fade in over 200ms
            opacity = Math.min(1.0, elapsed / 200.0);
        }
    }

    public long getLoadedTime() {
        return loadedTime;
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public boolean isFailed() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed = failed;
        this.loading = false;
    }

    public boolean isLoaded() {
        return image != null && !failed;
    }

    public String getTileKey() {
        return String.format("%d/%d/%d", zoom, x, y);
    }

    @Override
    public String toString() {
        return String.format("MapTile{zoom=%d, x=%d, y=%d, loaded=%s}", zoom, x, y, isLoaded());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MapTile mapTile = (MapTile) obj;
        return zoom == mapTile.zoom && x == mapTile.x && y == mapTile.y;
    }

    @Override
    public int hashCode() {
        return (zoom << 20) | (x << 10) | y;
    }
}