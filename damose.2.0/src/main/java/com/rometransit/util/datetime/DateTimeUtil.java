package com.rometransit.util.datetime;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

public class DateTimeUtil {
    public static final DateTimeFormatter GTFS_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter ISO_DATETIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    public static final DateTimeFormatter DISPLAY_DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public static String formatTime(LocalTime time) {
        return time.format(DISPLAY_TIME_FORMAT);
    }
    
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DISPLAY_DATETIME_FORMAT);
    }
    
    public static String formatGtfsTime(LocalTime time) {
        return time.format(GTFS_TIME_FORMAT);
    }
    
    public static LocalTime parseGtfsTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // Handle times that go beyond 24:00:00 (common in GTFS)
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            
            // Normalize hours if they exceed 23
            if (hours >= 24) {
                hours = hours % 24;
            }
            
            return LocalTime.of(hours, minutes, seconds);
        } catch (Exception e) {
            System.err.println("Failed to parse GTFS time: " + timeStr);
            return null;
        }
    }
    
    public static LocalDateTime parseIsoDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, ISO_DATETIME_FORMAT);
        } catch (Exception e) {
            System.err.println("Failed to parse ISO datetime: " + dateTimeStr);
            return null;
        }
    }
    
    public static long getMinutesBetween(LocalDateTime from, LocalDateTime to) {
        return ChronoUnit.MINUTES.between(from, to);
    }
    
    public static long getSecondsBetween(LocalDateTime from, LocalDateTime to) {
        return ChronoUnit.SECONDS.between(from, to);
    }
    
    public static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + "m";
            } else {
                return minutes + "m " + remainingSeconds + "s";
            }
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            if (minutes == 0) {
                return hours + "h";
            } else {
                return hours + "h " + minutes + "m";
            }
        }
    }
    
    public static String formatDurationFromMinutes(long minutes) {
        if (minutes < 60) {
            return minutes + " min";
        } else {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            } else {
                return hours + "h " + remainingMinutes + "m";
            }
        }
    }
    
    public static String getRelativeTime(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutes = getMinutesBetween(now, dateTime);
        
        if (minutes < 0) {
            // Past time
            minutes = Math.abs(minutes);
            if (minutes < 60) {
                return minutes + " minutes ago";
            } else if (minutes < 1440) { // Less than 24 hours
                long hours = minutes / 60;
                return hours + " hours ago";
            } else {
                long days = minutes / 1440;
                return days + " days ago";
            }
        } else {
            // Future time
            if (minutes < 1) {
                return "now";
            } else if (minutes < 60) {
                return "in " + minutes + " minutes";
            } else if (minutes < 1440) { // Less than 24 hours
                long hours = minutes / 60;
                return "in " + hours + " hours";
            } else {
                long days = minutes / 1440;
                return "in " + days + " days";
            }
        }
    }
    
    public static boolean isToday(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        return dateTime.toLocalDate().equals(now.toLocalDate());
    }
    
    public static boolean isWithinMinutes(LocalDateTime dateTime, int minutes) {
        LocalDateTime now = LocalDateTime.now();
        long diffMinutes = Math.abs(getMinutesBetween(now, dateTime));
        return diffMinutes <= minutes;
    }
    
    public static boolean isWithinTimeRange(LocalTime time, LocalTime start, LocalTime end) {
        if (start.isBefore(end)) {
            // Normal case: start < end (e.g., 09:00 to 17:00)
            return !time.isBefore(start) && !time.isAfter(end);
        } else {
            // Over midnight case: start > end (e.g., 23:00 to 02:00)
            return !time.isBefore(start) || !time.isAfter(end);
        }
    }
    
    public static String formatTimeAgo(LocalDateTime dateTime) {
        LocalDateTime now = LocalDateTime.now();
        long seconds = getSecondsBetween(dateTime, now);
        
        if (seconds < 60) {
            return "just now";
        } else if (seconds < 3600) {
            long minutes = seconds / 60;
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else if (seconds < 86400) {
            long hours = seconds / 3600;
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else {
            long days = seconds / 86400;
            return days + " day" + (days == 1 ? "" : "s") + " ago";
        }
    }
    
    public static LocalDateTime addMinutes(LocalDateTime dateTime, int minutes) {
        return dateTime.plusMinutes(minutes);
    }
    
    public static LocalDateTime addSeconds(LocalDateTime dateTime, int seconds) {
        return dateTime.plusSeconds(seconds);
    }
    
    public static String getCurrentTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    }
    
    public static boolean isBusinessHours(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        return isWithinTimeRange(time, LocalTime.of(6, 0), LocalTime.of(23, 59));
    }
    
    public static boolean isRushHour(LocalDateTime dateTime) {
        LocalTime time = dateTime.toLocalTime();
        // Morning rush: 7:00-9:30, Evening rush: 17:00-19:30
        return isWithinTimeRange(time, LocalTime.of(7, 0), LocalTime.of(9, 30)) ||
               isWithinTimeRange(time, LocalTime.of(17, 0), LocalTime.of(19, 30));
    }
}