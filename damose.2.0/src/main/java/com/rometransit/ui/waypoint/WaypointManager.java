package com.rometransit.ui.waypoint;

import javafx.scene.canvas.GraphicsContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages waypoints (stops, routes, markers) on the map
 * Waypoints stay fixed at their geographic coordinates during map pan/zoom
 */
public class WaypointManager {
    private final Map<String, Waypoint> waypoints;
    private final List<Waypoint> renderOrder;

    public WaypointManager() {
        this.waypoints = new ConcurrentHashMap<>();
        this.renderOrder = new ArrayList<>();
    }

    /**
     * Add a waypoint to the map
     * @param waypoint The waypoint to add
     */
    public void addWaypoint(Waypoint waypoint) {
        if (waypoint == null) return;

        waypoints.put(waypoint.getId(), waypoint);

        // Update render order
        synchronized (renderOrder) {
            renderOrder.clear();

            // Render order (bottom to top):
            // 1. RoutePathWaypoint (red lines - base layer)
            // 2. RouteVisualizationWaypoint (complete route visualization)
            // 3. StopMarkerWaypoint (blue dots for stops)
            // 4. StopWaypoint (selected stops)
            // 5. VehicleMarkerWaypoint (green dots for vehicles - top layer)

            waypoints.values().stream()
                .filter(w -> w instanceof RoutePathWaypoint)
                .forEach(renderOrder::add);

            waypoints.values().stream()
                .filter(w -> w instanceof RouteVisualizationWaypoint)
                .forEach(renderOrder::add);

            waypoints.values().stream()
                .filter(w -> w instanceof StopMarkerWaypoint)
                .forEach(renderOrder::add);

            waypoints.values().stream()
                .filter(w -> w instanceof StopWaypoint)
                .forEach(renderOrder::add);

            waypoints.values().stream()
                .filter(w -> w instanceof VehicleMarkerWaypoint)
                .forEach(renderOrder::add);
        }
    }

    /**
     * Remove a waypoint by ID
     * @param waypointId The waypoint ID
     */
    public void removeWaypoint(String waypointId) {
        Waypoint removed = waypoints.remove(waypointId);
        if (removed != null) {
            synchronized (renderOrder) {
                renderOrder.remove(removed);
            }
        }
    }

    /**
     * Clear all waypoints
     */
    public void clearWaypoints() {
        waypoints.clear();
        synchronized (renderOrder) {
            renderOrder.clear();
        }
    }

    /**
     * Clear waypoints of a specific type
     * @param waypointClass The waypoint class to remove
     */
    public void clearWaypointsByType(Class<? extends Waypoint> waypointClass) {
        List<String> toRemove = new ArrayList<>();

        waypoints.forEach((id, waypoint) -> {
            if (waypointClass.isInstance(waypoint)) {
                toRemove.add(id);
            }
        });

        toRemove.forEach(this::removeWaypoint);
    }

    /**
     * Get a waypoint by ID
     * @param waypointId The waypoint ID
     * @return The waypoint or null if not found
     */
    public Waypoint getWaypoint(String waypointId) {
        return waypoints.get(waypointId);
    }

    /**
     * Get all waypoints
     * @return List of all waypoints
     */
    public List<Waypoint> getAllWaypoints() {
        synchronized (renderOrder) {
            return new ArrayList<>(renderOrder);
        }
    }

    /**
     * Render all visible waypoints
     * @param gc Graphics context
     * @param zoom Current zoom level
     * @param converter Geo-to-screen coordinate converter
     */
    public void renderWaypoints(GraphicsContext gc, int zoom, Waypoint.GeoToScreenConverter converter) {
        synchronized (renderOrder) {
            for (Waypoint waypoint : renderOrder) {
                if (waypoint.isVisible()) {
                    waypoint.render(gc, zoom, converter);
                }
            }
        }
    }

    /**
     * Set visibility for a waypoint
     * @param waypointId The waypoint ID
     * @param visible Visibility flag
     */
    public void setWaypointVisible(String waypointId, boolean visible) {
        Waypoint waypoint = waypoints.get(waypointId);
        if (waypoint != null) {
            waypoint.setVisible(visible);
        }
    }

    /**
     * Get the number of waypoints
     * @return Waypoint count
     */
    public int getWaypointCount() {
        return waypoints.size();
    }

    /**
     * Check if a waypoint exists
     * @param waypointId The waypoint ID
     * @return True if exists
     */
    public boolean hasWaypoint(String waypointId) {
        return waypoints.containsKey(waypointId);
    }
}
