package com.rometransit.model.entity;

import java.time.LocalDateTime;

/**
 * Model for storing user notification preferences
 * Persists notification settings for different alert types and frequencies
 */
public class NotificationPreferences {

    private String userId;
    private LocalDateTime lastUpdated;

    // Master toggle
    private boolean enableAllNotifications;

    // Bus & Lines notifications
    private boolean arrivalReminders;
    private boolean delayAlerts;
    private boolean lineDeviationAlerts;

    // Notification frequency (only one can be active at a time)
    private NotificationFrequency frequency;

    /**
     * Default constructor with all notifications disabled
     */
    public NotificationPreferences() {
        this.enableAllNotifications = false;
        this.arrivalReminders = false;
        this.delayAlerts = false;
        this.lineDeviationAlerts = false;
        this.frequency = NotificationFrequency.NONE;
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Constructor with user ID
     */
    public NotificationPreferences(String userId) {
        this();
        this.userId = userId;
    }

    // ========== Getters and Setters ==========

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public boolean isEnableAllNotifications() {
        return enableAllNotifications;
    }

    public void setEnableAllNotifications(boolean enableAllNotifications) {
        this.enableAllNotifications = enableAllNotifications;
        this.lastUpdated = LocalDateTime.now();

        // When master toggle is disabled, disable all sub-toggles
        if (!enableAllNotifications) {
            this.arrivalReminders = false;
            this.delayAlerts = false;
            this.lineDeviationAlerts = false;
            this.frequency = NotificationFrequency.NONE;
        }
    }

    public boolean isArrivalReminders() {
        return arrivalReminders;
    }

    public void setArrivalReminders(boolean arrivalReminders) {
        this.arrivalReminders = arrivalReminders;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isDelayAlerts() {
        return delayAlerts;
    }

    public void setDelayAlerts(boolean delayAlerts) {
        this.delayAlerts = delayAlerts;
        this.lastUpdated = LocalDateTime.now();
    }

    public boolean isLineDeviationAlerts() {
        return lineDeviationAlerts;
    }

    public void setLineDeviationAlerts(boolean lineDeviationAlerts) {
        this.lineDeviationAlerts = lineDeviationAlerts;
        this.lastUpdated = LocalDateTime.now();
    }

    public NotificationFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(NotificationFrequency frequency) {
        this.frequency = frequency;
        this.lastUpdated = LocalDateTime.now();
    }

    // ========== Utility Methods ==========

    /**
     * Check if any notification type is enabled
     */
    public boolean hasAnyNotificationEnabled() {
        return arrivalReminders || delayAlerts || lineDeviationAlerts;
    }

    /**
     * Enable all notification types
     */
    public void enableAll() {
        this.enableAllNotifications = true;
        this.arrivalReminders = true;
        this.delayAlerts = true;
        this.lineDeviationAlerts = true;
        if (this.frequency == NotificationFrequency.NONE) {
            this.frequency = NotificationFrequency.REAL_TIME;
        }
        this.lastUpdated = LocalDateTime.now();
    }

    /**
     * Disable all notification types
     */
    public void disableAll() {
        this.enableAllNotifications = false;
        this.arrivalReminders = false;
        this.delayAlerts = false;
        this.lineDeviationAlerts = false;
        this.frequency = NotificationFrequency.NONE;
        this.lastUpdated = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "NotificationPreferences{" +
                "userId='" + userId + '\'' +
                ", enableAll=" + enableAllNotifications +
                ", arrivalReminders=" + arrivalReminders +
                ", delayAlerts=" + delayAlerts +
                ", lineDeviationAlerts=" + lineDeviationAlerts +
                ", frequency=" + frequency +
                ", lastUpdated=" + lastUpdated +
                '}';
    }

    /**
     * Enum for notification frequency options
     * Only one frequency can be active at a time (mutual exclusion)
     */
    public enum NotificationFrequency {
        NONE("None"),
        REAL_TIME("Real-time"),
        DAILY_SUMMARY("Daily summary"),
        WEEKLY_SUMMARY("Weekly summary");

        private final String displayName;

        NotificationFrequency(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
