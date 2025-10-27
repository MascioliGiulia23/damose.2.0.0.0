package com.rometransit.util.validation;

import java.util.regex.Pattern;

public class ValidationUtil {
    private static final Pattern GTFS_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Pattern COORDINATE_PATTERN = Pattern.compile("^-?\\d{1,2}\\.\\d+$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{1,2}:\\d{2}:\\d{2}$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^[A-Fa-f0-9]{6}$");
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$");

    // GTFS validation methods
    public static boolean isValidGtfsId(String id) {
        return id != null && !id.trim().isEmpty() && GTFS_ID_PATTERN.matcher(id).matches();
    }

    public static boolean isValidStopId(String stopId) {
        return isValidGtfsId(stopId);
    }

    public static boolean isValidRouteId(String routeId) {
        return isValidGtfsId(routeId);
    }

    public static boolean isValidTripId(String tripId) {
        return isValidGtfsId(tripId);
    }

    // Coordinate validation
    public static boolean isValidLatitude(double latitude) {
        return latitude >= -90.0 && latitude <= 90.0;
    }

    public static boolean isValidLongitude(double longitude) {
        return longitude >= -180.0 && longitude <= 180.0;
    }

    public static boolean isValidLatitude(String latitude) {
        if (latitude == null || latitude.trim().isEmpty()) {
            return false;
        }
        try {
            double lat = Double.parseDouble(latitude.trim());
            return isValidLatitude(lat);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidLongitude(String longitude) {
        if (longitude == null || longitude.trim().isEmpty()) {
            return false;
        }
        try {
            double lon = Double.parseDouble(longitude.trim());
            return isValidLongitude(lon);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidCoordinates(double latitude, double longitude) {
        return isValidLatitude(latitude) && isValidLongitude(longitude);
    }

    // Time validation
    public static boolean isValidGtfsTime(String time) {
        return time != null && TIME_PATTERN.matcher(time).matches();
    }

    public static boolean isValidTimeRange(String startTime, String endTime) {
        if (!isValidGtfsTime(startTime) || !isValidGtfsTime(endTime)) {
            return false;
        }
        
        try {
            String[] startParts = startTime.split(":");
            String[] endParts = endTime.split(":");
            
            int startHour = Integer.parseInt(startParts[0]);
            int startMinute = Integer.parseInt(startParts[1]);
            int startSecond = Integer.parseInt(startParts[2]);
            
            int endHour = Integer.parseInt(endParts[0]);
            int endMinute = Integer.parseInt(endParts[1]);
            int endSecond = Integer.parseInt(endParts[2]);
            
            int startTotalSeconds = startHour * 3600 + startMinute * 60 + startSecond;
            int endTotalSeconds = endHour * 3600 + endMinute * 60 + endSecond;
            
            return startTotalSeconds <= endTotalSeconds;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // Color validation
    public static boolean isValidRouteColor(String color) {
        return color != null && COLOR_PATTERN.matcher(color).matches();
    }

    // URL validation
    public static boolean isValidUrl(String url) {
        return url != null && URL_PATTERN.matcher(url).matches();
    }

    // String validation
    public static boolean isNonEmptyString(String str) {
        return str != null && !str.trim().isEmpty();
    }

    public static boolean isValidLength(String str, int maxLength) {
        return str != null && str.length() <= maxLength;
    }

    public static boolean isValidRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    public static boolean isValidRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    // Transport type validation
    public static boolean isValidRouteType(int routeType) {
        // GTFS route types
        return routeType >= 0 && routeType <= 12;
    }

    // Stop validation
    public static ValidationResult validateStop(String stopId, String stopName, double latitude, double longitude) {
        ValidationResult result = new ValidationResult();
        
        if (!isValidStopId(stopId)) {
            result.addError("Invalid stop ID: " + stopId);
        }
        
        if (!isNonEmptyString(stopName)) {
            result.addError("Stop name is required");
        } else if (!isValidLength(stopName, 255)) {
            result.addError("Stop name is too long (max 255 characters)");
        }
        
        if (!isValidLatitude(latitude)) {
            result.addError("Invalid latitude: " + latitude);
        }
        
        if (!isValidLongitude(longitude)) {
            result.addError("Invalid longitude: " + longitude);
        }
        
        return result;
    }

    // Route validation
    public static ValidationResult validateRoute(String routeId, String shortName, String longName, int routeType) {
        ValidationResult result = new ValidationResult();
        
        if (!isValidRouteId(routeId)) {
            result.addError("Invalid route ID: " + routeId);
        }
        
        if (!isNonEmptyString(shortName) && !isNonEmptyString(longName)) {
            result.addError("Either route short name or long name is required");
        }
        
        if (shortName != null && !isValidLength(shortName, 50)) {
            result.addError("Route short name is too long (max 50 characters)");
        }
        
        if (longName != null && !isValidLength(longName, 255)) {
            result.addError("Route long name is too long (max 255 characters)");
        }
        
        if (!isValidRouteType(routeType)) {
            result.addError("Invalid route type: " + routeType);
        }
        
        return result;
    }

    // Trip validation
    public static ValidationResult validateTrip(String tripId, String routeId, String serviceId, String headsign) {
        ValidationResult result = new ValidationResult();
        
        if (!isValidTripId(tripId)) {
            result.addError("Invalid trip ID: " + tripId);
        }
        
        if (!isValidRouteId(routeId)) {
            result.addError("Invalid route ID: " + routeId);
        }
        
        if (!isValidGtfsId(serviceId)) {
            result.addError("Invalid service ID: " + serviceId);
        }
        
        if (headsign != null && !isValidLength(headsign, 255)) {
            result.addError("Trip headsign is too long (max 255 characters)");
        }
        
        return result;
    }

    // Search query validation
    public static boolean isValidSearchQuery(String query) {
        return query != null && 
               query.trim().length() >= 2 && 
               query.trim().length() <= 100 &&
               !query.trim().matches("^[\\s\\p{Punct}]*$"); // Not just whitespace and punctuation
    }

    // Distance validation
    public static boolean isValidDistance(double distance) {
        return distance >= 0 && distance <= 50000; // Max 50km
    }

    // Pagination validation
    public static boolean isValidPageNumber(int page) {
        return page >= 0;
    }

    public static boolean isValidPageSize(int size) {
        return size > 0 && size <= 1000;
    }

    // User input sanitization
    public static String sanitizeSearchQuery(String query) {
        if (query == null) {
            return null;
        }
        
        return query.trim()
                   .replaceAll("\\s+", " ") // Replace multiple spaces with single space
                   .replaceAll("[<>\"'&]", "") // Remove potentially dangerous characters
                   .substring(0, Math.min(query.length(), 100)); // Limit length
    }

    public static String sanitizeUserInput(String input) {
        if (input == null) {
            return null;
        }
        
        return input.trim()
                   .replaceAll("[<>\"'&]", "")
                   .replaceAll("\\p{Cntrl}", ""); // Remove control characters
    }
}