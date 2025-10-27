package com.rometransit.ui.waypoint;

import javafx.scene.canvas.GraphicsContext;

/**
 * Base class for map waypoints (stops, routes, markers)
 * Waypoints stay fixed at their geographic coordinates during map pan/zoom
 */
public abstract class Waypoint {
    protected final String id;
    protected boolean visible = true;

    public Waypoint(String id) {
        this.id = id;
    }

    /**
     * Render this waypoint on the map canvas
     * @param gc Graphics context
     * @param zoom Current zoom level
     * @param geoToScreenConverter Function to convert (lat, lon) to (screenX, screenY)
     */
    public abstract void render(GraphicsContext gc, int zoom, GeoToScreenConverter geoToScreenConverter);

    /**
     * Check if this waypoint is visible at current zoom level
     */
    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public String getId() {
        return id;
    }

    /**
     * Functional interface to convert geographic coordinates to screen coordinates
     */
    @FunctionalInterface
    public interface GeoToScreenConverter {
        double[] convert(double lat, double lon);
    }
}
