package com.rometransit.service.notification;

import com.rometransit.model.entity.NotificationPreferences;
import com.rometransit.model.entity.NotificationPreferences.NotificationFrequency;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Service for managing notification preferences and sending notifications
 * Handles persistence of user notification settings
 */
public class NotificationService {
    private static final String PREFS_FILE = "notification_preferences.json";
    private static final String PREFS_DIR = System.getProperty("user.home") + "/.damose";

    private static NotificationService instance;

    private final ObjectMapper objectMapper;
    private final File prefsFile;
    private Map<String, NotificationPreferences> userPreferences;

    private NotificationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Initialize preferences directory and file
        File prefsDir = new File(PREFS_DIR);
        if (!prefsDir.exists()) {
            prefsDir.mkdirs();
        }

        this.prefsFile = new File(prefsDir, PREFS_FILE);
        this.userPreferences = new HashMap<>();

        loadPreferences();
    }

    /**
     * Get singleton instance of NotificationService
     */
    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    // ========== Preference Management ==========

    /**
     * Get notification preferences for a user
     * @param userId User ID
     * @return NotificationPreferences for the user
     */
    public NotificationPreferences getUserPreferences(String userId) {
        return userPreferences.computeIfAbsent(userId, id -> {
            NotificationPreferences prefs = new NotificationPreferences(id);
            savePreferences();
            return prefs;
        });
    }

    /**
     * Update notification preferences for a user
     * @param userId User ID
     * @param preferences Updated preferences
     */
    public void updateUserPreferences(String userId, NotificationPreferences preferences) {
        preferences.setUserId(userId);
        userPreferences.put(userId, preferences);
        savePreferences();
        System.out.println("📝 Updated notification preferences for user: " + userId);
    }

    /**
     * Set master enable/disable for all notifications
     * @param userId User ID
     * @param enabled true to enable all, false to disable all
     */
    public void setEnableAll(String userId, boolean enabled) {
        NotificationPreferences prefs = getUserPreferences(userId);
        if (enabled) {
            prefs.enableAll();
        } else {
            prefs.disableAll();
        }
        updateUserPreferences(userId, prefs);
    }

    /**
     * Update specific notification type
     * @param userId User ID
     * @param type Type of notification
     * @param enabled true to enable, false to disable
     */
    public void setNotificationType(String userId, NotificationType type, boolean enabled) {
        NotificationPreferences prefs = getUserPreferences(userId);

        switch (type) {
            case ARRIVAL_REMINDERS:
                prefs.setArrivalReminders(enabled);
                break;
            case DELAY_ALERTS:
                prefs.setDelayAlerts(enabled);
                break;
            case LINE_DEVIATION_ALERTS:
                prefs.setLineDeviationAlerts(enabled);
                break;
        }

        updateUserPreferences(userId, prefs);
    }

    /**
     * Update notification frequency (mutually exclusive)
     * @param userId User ID
     * @param frequency Notification frequency
     */
    public void setNotificationFrequency(String userId, NotificationFrequency frequency) {
        NotificationPreferences prefs = getUserPreferences(userId);
        prefs.setFrequency(frequency);
        updateUserPreferences(userId, prefs);
    }

    // ========== Notification Sending ==========

    /**
     * Send an arrival reminder notification
     * @param userId User ID
     * @param stopId Stop ID
     * @param routeId Route ID
     * @param arrivalTime Estimated arrival time
     */
    public void sendArrivalReminder(String userId, String stopId, String routeId, String arrivalTime) {
        NotificationPreferences prefs = getUserPreferences(userId);

        if (prefs.isEnableAllNotifications() && prefs.isArrivalReminders()) {
            String message = String.format("🚍 Route %s arriving at stop %s in %s",
                    routeId, stopId, arrivalTime);
            displayNotification("Arrival Reminder", message);
            System.out.println("📬 Sent arrival reminder: " + message);
        }
    }

    /**
     * Send a delay alert notification
     * @param userId User ID
     * @param routeId Route ID
     * @param delayMinutes Delay in minutes
     */
    public void sendDelayAlert(String userId, String routeId, int delayMinutes) {
        NotificationPreferences prefs = getUserPreferences(userId);

        if (prefs.isEnableAllNotifications() && prefs.isDelayAlerts()) {
            String message = String.format("⚠️ Route %s is delayed by %d minutes",
                    routeId, delayMinutes);
            displayNotification("Delay Alert", message);
            System.out.println("📬 Sent delay alert: " + message);
        }
    }

    /**
     * Send a line deviation alert
     * @param userId User ID
     * @param routeId Route ID
     * @param deviationInfo Deviation information
     */
    public void sendLineDeviationAlert(String userId, String routeId, String deviationInfo) {
        NotificationPreferences prefs = getUserPreferences(userId);

        if (prefs.isEnableAllNotifications() && prefs.isLineDeviationAlerts()) {
            String message = String.format("🔀 Route %s has a deviation: %s",
                    routeId, deviationInfo);
            displayNotification("Line Deviation", message);
            System.out.println("📬 Sent line deviation alert: " + message);
        }
    }

    /**
     * Send a daily summary notification
     * @param userId User ID
     * @param summaryData Summary data
     */
    public void sendDailySummary(String userId, String summaryData) {
        NotificationPreferences prefs = getUserPreferences(userId);

        if (prefs.isEnableAllNotifications() &&
            prefs.getFrequency() == NotificationFrequency.DAILY_SUMMARY) {
            displayNotification("Daily Summary", summaryData);
            System.out.println("📬 Sent daily summary: " + summaryData);
        }
    }

    /**
     * Send a weekly summary notification
     * @param userId User ID
     * @param summaryData Summary data
     */
    public void sendWeeklySummary(String userId, String summaryData) {
        NotificationPreferences prefs = getUserPreferences(userId);

        if (prefs.isEnableAllNotifications() &&
            prefs.getFrequency() == NotificationFrequency.WEEKLY_SUMMARY) {
            displayNotification("Weekly Summary", summaryData);
            System.out.println("📬 Sent weekly summary: " + summaryData);
        }
    }

    /**
     * Display notification (can be extended to use system notifications)
     */
    private void displayNotification(String title, String message) {
        // For now, just log to console
        // Can be extended to use JavaFX Notifications or system tray notifications
        System.out.println("🔔 NOTIFICATION: " + title);
        System.out.println("   " + message);
    }

    // ========== Persistence ==========

    /**
     * Load preferences from JSON file
     */
    private void loadPreferences() {
        try {
            if (prefsFile.exists()) {
                userPreferences = objectMapper.readValue(prefsFile,
                        new TypeReference<Map<String, NotificationPreferences>>() {});
                System.out.println("📥 Loaded notification preferences for " +
                        userPreferences.size() + " users");
            } else {
                userPreferences = new HashMap<>();
                System.out.println("📝 Created new notification preferences file");
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading notification preferences: " + e.getMessage());
            userPreferences = new HashMap<>();
        }
    }

    /**
     * Save preferences to JSON file
     */
    private void savePreferences() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(prefsFile, userPreferences);
            System.out.println("💾 Notification preferences saved");
        } catch (IOException e) {
            System.err.println("❌ Error saving notification preferences: " + e.getMessage());
        }
    }

    /**
     * Clear all preferences
     */
    public void clearAllPreferences() {
        userPreferences.clear();
        savePreferences();
        System.out.println("🗑️ Cleared all notification preferences");
    }

    // ========== Enums ==========

    /**
     * Types of notifications that can be enabled/disabled
     */
    public enum NotificationType {
        ARRIVAL_REMINDERS,
        DELAY_ALERTS,
        LINE_DEVIATION_ALERTS
    }
}
