package com.rometransit.util.math;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class TimeUtils {
    private static final DateTimeFormatter GTFS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static LocalTime parseGtfsTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) {
            return null;
        }

        try {
            String[] parts = timeString.split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid time format: " + timeString);
            }

            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);

            if (hours >= 24) {
                hours = hours % 24;
            }

            return LocalTime.of(hours, minutes, seconds);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse time: " + timeString, e);
        }
    }

    public static String formatGtfsTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(GTFS_TIME_FORMAT);
    }

    public static long getMinutesBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public static long getSecondsBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.SECONDS.between(start, end);
    }

    public static boolean isInFuture(LocalDateTime dateTime) {
        return dateTime.isAfter(LocalDateTime.now());
    }

    public static boolean isInPast(LocalDateTime dateTime) {
        return dateTime.isBefore(LocalDateTime.now());
    }

    public static LocalDateTime addMinutes(LocalDateTime dateTime, int minutes) {
        return dateTime.plusMinutes(minutes);
    }

    public static LocalDateTime addSeconds(LocalDateTime dateTime, int seconds) {
        return dateTime.plusSeconds(seconds);
    }

    public static String formatDuration(long minutes) {
        if (minutes < 0) {
            return "0 min";
        }
        
        if (minutes < 60) {
            return minutes + " min";
        }
        
        long hours = minutes / 60;
        long remainingMinutes = minutes % 60;
        
        if (hours < 24) {
            if (remainingMinutes == 0) {
                return hours + " h";
            }
            return hours + " h " + remainingMinutes + " min";
        }
        
        long days = hours / 24;
        long remainingHours = hours % 24;
        
        StringBuilder result = new StringBuilder();
        result.append(days).append(" d");
        
        if (remainingHours > 0) {
            result.append(" ").append(remainingHours).append(" h");
        }
        
        if (remainingMinutes > 0 && remainingHours == 0) {
            result.append(" ").append(remainingMinutes).append(" min");
        }
        
        return result.toString();
    }

    public static String getTimeUntilText(LocalDateTime targetTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = getMinutesBetween(now, targetTime);
        
        if (minutes < 0) {
            return "Passed";
        } else if (minutes == 0) {
            long seconds = getSecondsBetween(now, targetTime);
            if (seconds < 30) {
                return "Now";
            } else {
                return "< 1 min";
            }
        } else if (minutes < 60) {
            return minutes + " min";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + " h";
            }
            return hours + " h " + remainingMinutes + " min";
        }
    }

    public static boolean isWithinTimeWindow(LocalDateTime time, LocalDateTime center, int windowMinutes) {
        LocalDateTime start = center.minusMinutes(windowMinutes / 2);
        LocalDateTime end = center.plusMinutes(windowMinutes / 2);
        return !time.isBefore(start) && !time.isAfter(end);
    }
}