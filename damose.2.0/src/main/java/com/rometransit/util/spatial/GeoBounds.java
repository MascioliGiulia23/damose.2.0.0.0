package com.rometransit.util.spatial;

/**
 * Represents geographic bounds (bounding box)
 */
public class GeoBounds {
    public final double minLat;
    public final double minLon;
    public final double maxLat;
    public final double maxLon;

    public GeoBounds(double minLat, double minLon, double maxLat, double maxLon) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
    }

    public GeoPoint getCenter() {
        return new GeoPoint(
            (minLat + maxLat) / 2,
            (minLon + maxLon) / 2
        );
    }

    public boolean contains(double lat, double lon) {
        return lat >= minLat && lat <= maxLat &&
               lon >= minLon && lon <= maxLon;
    }

    public double getWidth() {
        return maxLon - minLon;
    }

    public double getHeight() {
        return maxLat - minLat;
    }

    @Override
    public String toString() {
        return String.format("GeoBounds[(%f,%f) to (%f,%f)]", minLat, minLon, maxLat, maxLon);
    }
}
