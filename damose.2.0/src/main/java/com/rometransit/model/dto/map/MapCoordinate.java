package com.rometransit.model.dto.map;

/**
 * Represents a geographic coordinate with latitude and longitude
 */
public class MapCoordinate {
    private final double latitude;
    private final double longitude;

    public MapCoordinate(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public String toString() {
        return String.format("MapCoordinate{lat=%.6f, lon=%.6f}", latitude, longitude);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MapCoordinate that = (MapCoordinate) obj;
        return Double.compare(that.latitude, latitude) == 0 &&
               Double.compare(that.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(latitude);
        long lonBits = Double.doubleToLongBits(longitude);
        return (int) (latBits ^ (latBits >>> 32) ^ lonBits ^ (lonBits >>> 32));
    }
}