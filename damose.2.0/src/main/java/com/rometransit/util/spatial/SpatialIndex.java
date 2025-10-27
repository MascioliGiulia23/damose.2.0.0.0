package com.rometransit.util.spatial;

import com.rometransit.model.entity.Stop;
import com.rometransit.util.math.GeoUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Spatial index using Geohash for efficient geographic queries.
 * Provides O(1) lookup for nearby stops instead of O(n) full scans.
 */
public class SpatialIndex {
    private final Map<String, List<Stop>> geohashIndex;
    private final int precision;

    public SpatialIndex() {
        this(6); // Default precision ~1.2km cells
    }

    public SpatialIndex(int precision) {
        this.precision = precision;
        this.geohashIndex = new ConcurrentHashMap<>();
    }

    /**
     * Index a stop by its coordinates
     */
    public void index(Stop stop) {
        if (stop == null) {
            return;
        }

        double lat = stop.getStopLat();
        double lon = stop.getStopLon();

        // Skip invalid coordinates
        if (!isValidCoordinate(lat, lon)) {
            return;
        }

        String geohash = Geohash.encode(lat, lon, precision);
        geohashIndex.computeIfAbsent(geohash, k -> new ArrayList<>()).add(stop);
    }

    private boolean isValidCoordinate(double lat, double lon) {
        return lat >= -90.0 && lat <= 90.0 &&
               lon >= -180.0 && lon <= 180.0 &&
               !(lat == 0.0 && lon == 0.0); // Exclude null island
    }

    /**
     * Index multiple stops
     */
    public void indexAll(Collection<Stop> stops) {
        stops.forEach(this::index);
    }

    /**
     * Remove a stop from the index
     */
    public void remove(Stop stop) {
        if (stop == null) {
            return;
        }

        double lat = stop.getStopLat();
        double lon = stop.getStopLon();

        // Skip invalid coordinates
        if (!isValidCoordinate(lat, lon)) {
            return;
        }

        String geohash = Geohash.encode(lat, lon, precision);
        List<Stop> stopsInCell = geohashIndex.get(geohash);
        if (stopsInCell != null) {
            stopsInCell.remove(stop);
            if (stopsInCell.isEmpty()) {
                geohashIndex.remove(geohash);
            }
        }
    }

    /**
     * Find all stops within radius (in kilometers) from a point
     * This is now O(k) where k is the number of stops in relevant geohash cells,
     * instead of O(n) where n is total number of stops.
     */
    public List<Stop> findNearby(double latitude, double longitude, double radiusKm) {
        // Get the geohash cell for the center point
        String centerGeohash = Geohash.encode(latitude, longitude, precision);

        // Get all neighboring cells (9 cells total including center)
        String[] searchCells = Geohash.getNeighbors(centerGeohash);

        // Collect all stops from relevant cells
        List<Stop> candidates = new ArrayList<>();
        for (String geohash : searchCells) {
            List<Stop> stopsInCell = geohashIndex.get(geohash);
            if (stopsInCell != null) {
                candidates.addAll(stopsInCell);
            }
        }

        // Filter by exact distance
        return candidates.stream()
            .filter(stop -> GeoUtils.isWithinRadius(latitude, longitude,
                                                     stop.getStopLat(), stop.getStopLon(),
                                                     radiusKm))
            .collect(Collectors.toList());
    }

    /**
     * Find all stops within a bounding box
     */
    public List<Stop> findInBounds(double minLat, double minLon, double maxLat, double maxLon) {
        try {
            String[] geohashes = Geohash.getGeohashesInBounds(minLat, minLon, maxLat, maxLon, precision);

            List<Stop> results = new ArrayList<>();
            for (String geohash : geohashes) {
                List<Stop> stopsInCell = geohashIndex.get(geohash);
                if (stopsInCell != null) {
                    results.addAll(stopsInCell);
                }
            }

            return results;
        } catch (IllegalArgumentException e) {
            // Bounds too large, fall back to all stops with filtering
            return geohashIndex.values().stream()
                .flatMap(List::stream)
                .filter(stop -> stop.getStopLat() >= minLat && stop.getStopLat() <= maxLat &&
                              stop.getStopLon() >= minLon && stop.getStopLon() <= maxLon)
                .collect(Collectors.toList());
        }
    }

    /**
     * Find k nearest stops to a point
     */
    public List<Stop> findKNearest(double latitude, double longitude, int k) {
        // Start with nearby search with increasing radius
        double radius = 0.5; // Start with 500m
        List<Stop> results;

        while (true) {
            results = findNearby(latitude, longitude, radius);

            if (results.size() >= k || radius > 50) { // Max 50km search
                break;
            }

            radius *= 2; // Double the radius
        }

        // Sort by distance and return top k
        return results.stream()
            .sorted(Comparator.comparingDouble(stop ->
                GeoUtils.calculateDistance(latitude, longitude, stop.getStopLat(), stop.getStopLon())))
            .limit(k)
            .collect(Collectors.toList());
    }

    /**
     * Rebuild the entire index
     */
    public void rebuild(Collection<Stop> allStops) {
        clear();
        indexAll(allStops);
    }

    /**
     * Clear the index
     */
    public void clear() {
        geohashIndex.clear();
    }

    /**
     * Get index statistics
     */
    public IndexStats getStats() {
        int totalCells = geohashIndex.size();
        int totalStops = geohashIndex.values().stream()
            .mapToInt(List::size)
            .sum();
        double avgStopsPerCell = totalCells > 0 ? (double) totalStops / totalCells : 0;

        return new IndexStats(totalCells, totalStops, avgStopsPerCell, precision);
    }

    public static class IndexStats {
        public final int totalCells;
        public final int totalStops;
        public final double avgStopsPerCell;
        public final int precision;

        public IndexStats(int totalCells, int totalStops, double avgStopsPerCell, int precision) {
            this.totalCells = totalCells;
            this.totalStops = totalStops;
            this.avgStopsPerCell = avgStopsPerCell;
            this.precision = precision;
        }

        @Override
        public String toString() {
            return String.format("IndexStats[cells=%d, stops=%d, avg=%.2f, precision=%d]",
                totalCells, totalStops, avgStopsPerCell, precision);
        }
    }
}
