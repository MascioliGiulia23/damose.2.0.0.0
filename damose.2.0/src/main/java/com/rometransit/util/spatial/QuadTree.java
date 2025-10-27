package com.rometransit.util.spatial;

import com.rometransit.model.entity.Stop;
import java.util.ArrayList;
import java.util.List;

/**
 * QuadTree spatial index for efficient stop queries
 * Optimizes stop rendering by avoiding linear iteration
 */
public class QuadTree {
    private static final int MAX_CAPACITY = 10;
    private static final int MAX_DEPTH = 8;

    private final Bounds bounds;
    private final int depth;
    private final List<Stop> stops;
    private QuadTree[] children;
    private boolean divided;

    public record Bounds(double minLat, double maxLat, double minLon, double maxLon) {

        public boolean contains(double lat, double lon) {
                return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
            }

            public boolean intersects(Bounds other) {
                return !(other.maxLat < minLat || other.minLat > maxLat ||
                        other.maxLon < minLon || other.minLon > maxLon);
            }

            public double getCenterLat() {
                return (minLat + maxLat) / 2.0;
            }

            public double getCenterLon() {
                return (minLon + maxLon) / 2.0;
            }
        }

    public QuadTree(Bounds bounds) {
        this(bounds, 0);
    }

    private QuadTree(Bounds bounds, int depth) {
        this.bounds = bounds;
        this.depth = depth;
        this.stops = new ArrayList<>();
        this.children = null;
        this.divided = false;
    }

    public void insert(Stop stop) {
        if (!bounds.contains(stop.getStopLat(), stop.getStopLon())) {
            return; // Stop outside bounds
        }

        if (stops.size() < MAX_CAPACITY || depth >= MAX_DEPTH) {
            stops.add(stop);
            return;
        }

        if (!divided) {
            subdivide();
        }

        // Insert into appropriate child
        for (QuadTree child : children) {
            if (child.bounds.contains(stop.getStopLat(), stop.getStopLon())) {
                child.insert(stop);
                return;
            }
        }
    }

    private void subdivide() {
        double centerLat = bounds.getCenterLat();
        double centerLon = bounds.getCenterLon();

        children = new QuadTree[4];

        // Northeast
        children[0] = new QuadTree(
            new Bounds(centerLat, bounds.maxLat, centerLon, bounds.maxLon),
            depth + 1
        );

        // Northwest
        children[1] = new QuadTree(
            new Bounds(centerLat, bounds.maxLat, bounds.minLon, centerLon),
            depth + 1
        );

        // Southwest
        children[2] = new QuadTree(
            new Bounds(bounds.minLat, centerLat, bounds.minLon, centerLon),
            depth + 1
        );

        // Southeast
        children[3] = new QuadTree(
            new Bounds(bounds.minLat, centerLat, centerLon, bounds.maxLon),
            depth + 1
        );

        divided = true;

        // Redistribute existing stops to children
        List<Stop> currentStops = new ArrayList<>(stops);
        stops.clear();

        for (Stop stop : currentStops) {
            insert(stop);
        }
    }

    public List<Stop> query(Bounds range) {
        List<Stop> found = new ArrayList<>();

        if (!bounds.intersects(range)) {
            return found; // No intersection
        }

        // Add stops in this node that fall within range
        for (Stop stop : stops) {
            if (range.contains(stop.getStopLat(), stop.getStopLon())) {
                found.add(stop);
            }
        }

        // Query children if divided
        if (divided) {
            for (QuadTree child : children) {
                found.addAll(child.query(range));
            }
        }

        return found;
    }

    public int size() {
        int count = stops.size();
        if (divided) {
            for (QuadTree child : children) {
                count += child.size();
            }
        }
        return count;
    }

    public void clear() {
        stops.clear();
        if (divided) {
            for (QuadTree child : children) {
                child.clear();
            }
            children = null;
            divided = false;
        }
    }
}
