package com.rometransit.service.notification;

import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.data.repository.IncidentRepository;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.entity.TransportIncident;
import com.rometransit.model.entity.NotificationPreferences;
import com.rometransit.service.auth.AuthService;
import com.rometransit.ui.notification.NotificationPopupManager;
import com.rometransit.util.logging.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Monitors real-time data and triggers notifications based on user preferences
 * Handles:
 * - Arrival reminders for favorite stops
 * - Delay and cancellation alerts
 * - Line deviation alerts
 */
public class RealtimeNotificationMonitor {

    private static RealtimeNotificationMonitor instance;

    private final VehicleRepository vehicleRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationService notificationService;
    private final NotificationPopupManager popupManager;
    private final AuthService authService;

    private ScheduledExecutorService scheduler;
    private boolean isMonitoring = false;

    // Track seen incidents to avoid duplicate notifications
    private Set<String> seenIncidentIds = new ConcurrentHashMap().newKeySet();
    private Map<String, Long> lastNotificationTime = new ConcurrentHashMap<>();

    private static final long NOTIFICATION_COOLDOWN_MS = 60000; // 1 minute between same notifications
    private static final int ARRIVAL_THRESHOLD_MINUTES = 5; // Notify when vehicle is within 5 minutes
    private static final int MONITORING_INTERVAL_SECONDS = 15; // Check every 15 seconds

    private RealtimeNotificationMonitor() {
        this.vehicleRepository = new VehicleRepository();
        this.incidentRepository = new IncidentRepository();
        this.notificationService = NotificationService.getInstance();
        this.popupManager = NotificationPopupManager.getInstance();
        this.authService = AuthService.getInstance();
    }

    public static synchronized RealtimeNotificationMonitor getInstance() {
        if (instance == null) {
            instance = new RealtimeNotificationMonitor();
        }
        return instance;
    }

    /**
     * Start monitoring for notifications
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Logger.log("⚠️ Notification monitoring already running");
            return;
        }

        Logger.log("🔔 Starting real-time notification monitoring...");
        scheduler = Executors.newScheduledThreadPool(1);

        // Schedule periodic checks
        scheduler.scheduleAtFixedRate(
            this::checkForNotifications,
            0,
            MONITORING_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        isMonitoring = true;
        Logger.log("✅ Notification monitoring started (interval: " + MONITORING_INTERVAL_SECONDS + "s)");
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }

        Logger.log("🔕 Stopping notification monitoring...");

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        isMonitoring = false;
        Logger.log("✅ Notification monitoring stopped");
    }

    /**
     * Main check method called periodically
     */
    private void checkForNotifications() {
        try {
            // Get current user's preferences
            String userId = authService.getCurrentUser() != null ?
                           authService.getCurrentUser().getUserId() : "default_user";

            NotificationPreferences prefs = notificationService.getUserPreferences(userId);

            // Skip if all notifications are disabled
            if (!prefs.isEnableAllNotifications()) {
                return;
            }

            // Check for arrival reminders (if enabled)
            if (prefs.isArrivalReminders()) {
                checkArrivalReminders(userId);
            }

            // Check for delay/cancellation alerts (if enabled)
            if (prefs.isDelayAlerts()) {
                checkDelayAlerts();
            }

            // Check for line deviation alerts (if enabled)
            if (prefs.isLineDeviationAlerts()) {
                checkLineDeviationAlerts();
            }

        } catch (Exception e) {
            Logger.log("❌ Error checking notifications: " + e.getMessage());
        }
    }

    /**
     * Check for arriving vehicles at favorite stops
     * This would require favorite stops management - for now we'll use a simplified version
     */
    private void checkArrivalReminders(String userId) {
        try {
            // Get all active vehicles
            List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();

            // For each vehicle, check if it's approaching any "favorite" stop
            // This is a simplified implementation - in production you'd query favorite stops
            for (Vehicle vehicle : activeVehicles) {
                // Example: Check if vehicle has an estimated time to next stop
                // and if that stop is in favorites

                // For now, we'll just demonstrate the notification mechanism
                if (vehicle.getStopId() != null && shouldNotify("arrival_" + vehicle.getVehicleId())) {
                    String routeName = vehicle.getRouteId() != null ? vehicle.getRouteId() : "Sconosciuta";
                    String stopName = vehicle.getStopId() != null ? vehicle.getStopId() : "Fermata";

                    // Simulate arrival time calculation
                    int minutesAway = calculateMinutesAway(vehicle);

                    if (minutesAway > 0 && minutesAway <= ARRIVAL_THRESHOLD_MINUTES) {
                        popupManager.showArrivalReminder(routeName, stopName, minutesAway);
                        markNotified("arrival_" + vehicle.getVehicleId());
                    }
                }
            }
        } catch (Exception e) {
            Logger.log("❌ Error checking arrival reminders: " + e.getMessage());
        }
    }

    /**
     * Check for delay and cancellation incidents
     */
    private void checkDelayAlerts() {
        try {
            List<TransportIncident> activeIncidents = incidentRepository.findActiveIncidents();

            for (TransportIncident incident : activeIncidents) {
                String incidentId = incident.getId();

                // Skip if we've already notified about this incident
                if (seenIncidentIds.contains(incidentId)) {
                    continue;
                }

                // Check if incident is a delay or cancellation
                String type = incident.getType();
                boolean isDelayType = type != null &&
                                     (type.equalsIgnoreCase("RITARDO") ||
                                      type.equalsIgnoreCase("CANCELLAZIONE") ||
                                      type.equalsIgnoreCase("DELAY"));

                if (isDelayType && shouldNotify("delay_" + incidentId)) {
                    // Get first affected route or use default
                    String routeName = "Linea";
                    if (incident.getAffectedRoutes() != null && !incident.getAffectedRoutes().isEmpty()) {
                        routeName = incident.getAffectedRoutes().get(0);
                    }

                    String message = incident.getDescription() != null ?
                                   incident.getDescription() : "Segnalato problema sulla linea";
                    boolean isCancellation = type != null && type.equalsIgnoreCase("CANCELLAZIONE");

                    popupManager.showDelayAlert(routeName, message, isCancellation);
                    seenIncidentIds.add(incidentId);
                    markNotified("delay_" + incidentId);
                }
            }

            // Clean up old seen incidents (older than 1 hour)
            cleanupOldSeenIncidents();

        } catch (Exception e) {
            Logger.log("❌ Error checking delay alerts: " + e.getMessage());
        }
    }

    /**
     * Check for line deviation incidents
     */
    private void checkLineDeviationAlerts() {
        try {
            List<TransportIncident> activeIncidents = incidentRepository.findActiveIncidents();

            for (TransportIncident incident : activeIncidents) {
                String incidentId = incident.getId();

                // Skip if we've already notified about this incident
                if (seenIncidentIds.contains(incidentId)) {
                    continue;
                }

                // Check if incident is a deviation
                String type = incident.getType();
                boolean isDeviationType = type != null &&
                                         (type.equalsIgnoreCase("DEVIAZIONE") ||
                                          type.equalsIgnoreCase("DEVIATION"));

                if (isDeviationType && shouldNotify("deviation_" + incidentId)) {
                    // Get first affected route or use default
                    String routeName = "Linea";
                    if (incident.getAffectedRoutes() != null && !incident.getAffectedRoutes().isEmpty()) {
                        routeName = incident.getAffectedRoutes().get(0);
                    }

                    String message = incident.getDescription() != null ?
                                   incident.getDescription() : "Percorso modificato";

                    popupManager.showDeviationAlert(routeName, message);
                    seenIncidentIds.add(incidentId);
                    markNotified("deviation_" + incidentId);
                }
            }

        } catch (Exception e) {
            Logger.log("❌ Error checking deviation alerts: " + e.getMessage());
        }
    }

    /**
     * Calculate estimated minutes until vehicle arrives at next stop
     * Simplified implementation - in production would use real-time predictions
     */
    private int calculateMinutesAway(Vehicle vehicle) {
        // This is a placeholder - in production you would:
        // 1. Get vehicle's current position
        // 2. Get next stop's position
        // 3. Calculate distance and ETA based on speed
        // 4. Use arrival prediction data if available

        // For now, return a random value for demonstration
        return new Random().nextInt(10);
    }

    /**
     * Check if we should notify about this event (respects cooldown)
     */
    private boolean shouldNotify(String notificationKey) {
        Long lastTime = lastNotificationTime.get(notificationKey);

        if (lastTime == null) {
            return true;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        return elapsed >= NOTIFICATION_COOLDOWN_MS;
    }

    /**
     * Mark that we've notified about this event
     */
    private void markNotified(String notificationKey) {
        lastNotificationTime.put(notificationKey, System.currentTimeMillis());
    }

    /**
     * Clean up old seen incidents (older than 1 hour)
     */
    private void cleanupOldSeenIncidents() {
        // Remove incident IDs that we haven't seen in the last hour
        Iterator<Map.Entry<String, Long>> iterator = lastNotificationTime.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            long elapsed = System.currentTimeMillis() - entry.getValue();

            if (elapsed > TimeUnit.HOURS.toMillis(1)) {
                iterator.remove();
                seenIncidentIds.remove(entry.getKey().replaceFirst("(arrival|delay|deviation)_", ""));
            }
        }
    }

    /**
     * Check if monitoring is active
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
}
