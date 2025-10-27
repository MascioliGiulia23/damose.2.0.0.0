package com.rometransit.util.spatial;

/**
 * Geohash implementation for spatial indexing.
 * Converts geographic coordinates to base32 encoded strings for efficient spatial queries.
 */
public class Geohash {
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int[] BITS = {16, 8, 4, 2, 1};

    // Precision levels (geohash length -> approximate area)
    // 4 chars ~ 39km x 20km
    // 5 chars ~ 4.9km x 4.9km
    // 6 chars ~ 1.2km x 0.6km
    // 7 chars ~ 152m x 152m
    // 8 chars ~ 38m x 19m
    private static final int DEFAULT_PRECISION = 6; // ~1.2km cells

    /**
     * Encode latitude and longitude to geohash with default precision
     */
    public static String encode(double latitude, double longitude) {
        return encode(latitude, longitude, DEFAULT_PRECISION);
    }

    /**
     * Encode latitude and longitude to geohash with specified precision
     */
    public static String encode(double latitude, double longitude, int precision) {
        if (!isValidCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid coordinates: " + latitude + ", " + longitude);
        }

        double[] latInterval = {-90.0, 90.0};
        double[] lonInterval = {-180.0, 180.0};

        StringBuilder geohash = new StringBuilder();
        boolean isEven = true;
        int bit = 0;
        int ch = 0;

        while (geohash.length() < precision) {
            double mid;

            if (isEven) {
                mid = (lonInterval[0] + lonInterval[1]) / 2;
                if (longitude > mid) {
                    ch |= BITS[bit];
                    lonInterval[0] = mid;
                } else {
                    lonInterval[1] = mid;
                }
            } else {
                mid = (latInterval[0] + latInterval[1]) / 2;
                if (latitude > mid) {
                    ch |= BITS[bit];
                    latInterval[0] = mid;
                } else {
                    latInterval[1] = mid;
                }
            }

            isEven = !isEven;

            if (bit < 4) {
                bit++;
            } else {
                geohash.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }

        return geohash.toString();
    }

    /**
     * Decode geohash to latitude and longitude bounds
     */
    public static GeoBounds decode(String geohash) {
        if (geohash == null || geohash.isEmpty()) {
            throw new IllegalArgumentException("Geohash cannot be null or empty");
        }

        double[] latInterval = {-90.0, 90.0};
        double[] lonInterval = {-180.0, 180.0};

        boolean isEven = true;

        for (int i = 0; i < geohash.length(); i++) {
            char c = geohash.charAt(i);
            int cd = BASE32.indexOf(c);

            if (cd == -1) {
                throw new IllegalArgumentException("Invalid geohash character: " + c);
            }

            for (int mask : BITS) {
                if (isEven) {
                    if ((cd & mask) != 0) {
                        lonInterval[0] = (lonInterval[0] + lonInterval[1]) / 2;
                    } else {
                        lonInterval[1] = (lonInterval[0] + lonInterval[1]) / 2;
                    }
                } else {
                    if ((cd & mask) != 0) {
                        latInterval[0] = (latInterval[0] + latInterval[1]) / 2;
                    } else {
                        latInterval[1] = (latInterval[0] + latInterval[1]) / 2;
                    }
                }
                isEven = !isEven;
            }
        }

        double minLat = latInterval[0];
        double maxLat = latInterval[1];
        double minLon = lonInterval[0];
        double maxLon = lonInterval[1];

        return new GeoBounds(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Get center point of a geohash
     */
    public static GeoPoint decodeToPoint(String geohash) {
        GeoBounds bounds = decode(geohash);
        return bounds.getCenter();
    }

    /**
     * Get all neighboring geohashes (8 neighbors + center = 9 total)
     */
    public static String[] getNeighbors(String geohash) {
        return new String[] {
            geohash,                    // Center
            getNeighbor(geohash, 0, 1), // North
            getNeighbor(geohash, 1, 1), // North-East
            getNeighbor(geohash, 1, 0), // East
            getNeighbor(geohash, 1, -1),// South-East
            getNeighbor(geohash, 0, -1),// South
            getNeighbor(geohash, -1, -1),// South-West
            getNeighbor(geohash, -1, 0), // West
            getNeighbor(geohash, -1, 1)  // North-West
        };
    }

    /**
     * Get neighbor in specific direction
     */
    private static String getNeighbor(String geohash, int dx, int dy) {
        GeoBounds bounds = decode(geohash);
        double centerLat = (bounds.minLat + bounds.maxLat) / 2;
        double centerLon = (bounds.minLon + bounds.maxLon) / 2;
        double latHeight = bounds.maxLat - bounds.minLat;
        double lonWidth = bounds.maxLon - bounds.minLon;

        double newLat = centerLat + (dy * latHeight);
        double newLon = centerLon + (dx * lonWidth);

        // Handle wrap-around
        if (newLat > 90) newLat = 90;
        if (newLat < -90) newLat = -90;
        if (newLon > 180) newLon -= 360;
        if (newLon < -180) newLon += 360;

        return encode(newLat, newLon, geohash.length());
    }

    /**
     * Get all geohashes that cover a bounding box
     */
    public static String[] getGeohashesInBounds(double minLat, double minLon, double maxLat, double maxLon, int precision) {
        // Estimate number of cells needed
        double latCellSize = 180.0 / Math.pow(2, precision * 2.5);
        double lonCellSize = 360.0 / Math.pow(2, precision * 2.5);

        int latCells = (int) Math.ceil((maxLat - minLat) / latCellSize) + 1;
        int lonCells = (int) Math.ceil((maxLon - minLon) / lonCellSize) + 1;

        // Limit to prevent excessive memory usage
        if (latCells * lonCells > 1000) {
            throw new IllegalArgumentException("Bounding box too large for precision " + precision);
        }

        java.util.Set<String> geohashes = new java.util.HashSet<>();

        for (double lat = minLat; lat <= maxLat; lat += latCellSize) {
            for (double lon = minLon; lon <= maxLon; lon += lonCellSize) {
                if (isValidCoordinate(lat, lon)) {
                    geohashes.add(encode(lat, lon, precision));
                }
            }
        }

        return geohashes.toArray(new String[0]);
    }

    /**
     * Calculate the optimal precision for a given radius in kilometers
     */
    public static int getPrecisionForRadius(double radiusKm) {
        // Approximate precision based on radius
        if (radiusKm >= 20) return 4;  // ~40km cells
        if (radiusKm >= 5) return 5;   // ~5km cells
        if (radiusKm >= 1) return 6;   // ~1km cells
        if (radiusKm >= 0.15) return 7; // ~150m cells
        return 8; // ~40m cells
    }

    private static boolean isValidCoordinate(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0 &&
               longitude >= -180.0 && longitude <= 180.0;
    }
}
