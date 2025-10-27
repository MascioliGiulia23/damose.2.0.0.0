package com.rometransit.service.network;

import com.rometransit.model.enums.ConnectionStatus;
import com.rometransit.service.network.NetworkManager.ConnectionStatusListener;

import java.time.LocalDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

public class ConnectionStatusMonitor {
    private final List<ConnectionStatusListener> listeners;
    private final List<StatusChangeEvent> statusHistory;
    private final int maxHistorySize = 100;

    public ConnectionStatusMonitor() {
        this.listeners = new CopyOnWriteArrayList<>();
        this.statusHistory = new CopyOnWriteArrayList<>();
    }

    public void addListener(ConnectionStatusListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ConnectionStatusListener listener) {
        listeners.remove(listener);
    }

    public void onStatusChange(ConnectionStatus oldStatus, ConnectionStatus newStatus) {
        // Record the status change
        StatusChangeEvent event = new StatusChangeEvent(oldStatus, newStatus, LocalDateTime.now());
        addToHistory(event);

        // Log the status change
        System.out.println("Connection status changed: " + oldStatus + " -> " + newStatus);

        // Notify all listeners
        notifyListeners(oldStatus, newStatus);

        // Handle specific status changes
        handleStatusChange(oldStatus, newStatus);
    }

    private void addToHistory(StatusChangeEvent event) {
        statusHistory.add(event);
        
        // Keep only the most recent events
        while (statusHistory.size() > maxHistorySize) {
            statusHistory.remove(0);
        }
    }

    private void notifyListeners(ConnectionStatus oldStatus, ConnectionStatus newStatus) {
        for (ConnectionStatusListener listener : listeners) {
            try {
                listener.onStatusChanged(oldStatus, newStatus);
            } catch (Exception e) {
                System.err.println("Error notifying connection status listener: " + e.getMessage());
            }
        }
    }

    private void handleStatusChange(ConnectionStatus oldStatus, ConnectionStatus newStatus) {
        switch (newStatus) {
            case ONLINE:
                handleOnlineStatus(oldStatus);
                break;
            case OFFLINE:
                handleOfflineStatus(oldStatus);
                break;
            case LIMITED:
                handleLimitedStatus(oldStatus);
                break;
            case ERROR:
                handleErrorStatus(oldStatus);
                break;
        }
    }

    private void handleOnlineStatus(ConnectionStatus previousStatus) {
        if (previousStatus == ConnectionStatus.OFFLINE) {
            System.out.println("Connection restored - switching to online mode");
            // Trigger data synchronization
            // This could notify services to start real-time updates
        } else if (previousStatus == ConnectionStatus.LIMITED) {
            System.out.println("Connection improved - full online capabilities restored");
        }
    }

    private void handleOfflineStatus(ConnectionStatus previousStatus) {
        if (previousStatus == ConnectionStatus.ONLINE || previousStatus == ConnectionStatus.LIMITED) {
            System.out.println("Connection lost - switching to offline mode");
            // Notify services to use cached data
            // Stop real-time updates
        }
    }

    private void handleLimitedStatus(ConnectionStatus previousStatus) {
        System.out.println("Limited connectivity detected - reducing network activity");
        // Might reduce update frequency or use compressed data
    }

    private void handleErrorStatus(ConnectionStatus previousStatus) {
        System.err.println("Network error detected - connection unstable");
        // Might implement retry mechanisms or error reporting
    }

    public List<StatusChangeEvent> getStatusHistory() {
        return List.copyOf(statusHistory);
    }

    public List<StatusChangeEvent> getRecentStatusChanges(int count) {
        int size = statusHistory.size();
        int start = Math.max(0, size - count);
        return statusHistory.subList(start, size);
    }

    public StatusChangeEvent getLastStatusChange() {
        return statusHistory.isEmpty() ? null : statusHistory.get(statusHistory.size() - 1);
    }

    public long getTimeInStatus(ConnectionStatus status) {
        // Calculate how long we've been in the current status
        StatusChangeEvent lastChange = getLastStatusChange();
        if (lastChange == null || lastChange.newStatus != status) {
            return 0;
        }

        return java.time.Duration.between(lastChange.timestamp, LocalDateTime.now()).toSeconds();
    }

    public int getStatusChangeCount(ConnectionStatus status) {
        return (int) statusHistory.stream()
                .filter(event -> event.newStatus == status)
                .count();
    }

    public double getStatusStability() {
        if (statusHistory.size() < 2) {
            return 1.0; // Perfect stability with no changes
        }

        // Calculate stability as inverse of change frequency
        long totalMinutes = java.time.Duration.between(
                statusHistory.get(0).timestamp,
                statusHistory.get(statusHistory.size() - 1).timestamp
        ).toMinutes();

        if (totalMinutes == 0) {
            return 1.0;
        }

        double changesPerMinute = (double) statusHistory.size() / totalMinutes;
        return Math.max(0.0, 1.0 - (changesPerMinute * 10)); // Scale factor
    }

    public ConnectionStatus getMostCommonStatus() {
        if (statusHistory.isEmpty()) {
            return ConnectionStatus.OFFLINE;
        }

        return statusHistory.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        event -> event.newStatus,
                        java.util.stream.Collectors.counting()
                ))
                .entrySet().stream()
                .max(java.util.Map.Entry.comparingByValue())
                .map(java.util.Map.Entry::getKey)
                .orElse(ConnectionStatus.OFFLINE);
    }

    public String getStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Connection Status Summary:\n");
        
        StatusChangeEvent lastChange = getLastStatusChange();
        if (lastChange != null) {
            summary.append("Current Status: ").append(lastChange.newStatus).append("\n");
            summary.append("Since: ").append(lastChange.timestamp).append("\n");
            summary.append("Duration: ").append(getTimeInStatus(lastChange.newStatus)).append(" seconds\n");
        }
        
        summary.append("Total Status Changes: ").append(statusHistory.size()).append("\n");
        summary.append("Most Common Status: ").append(getMostCommonStatus()).append("\n");
        summary.append("Stability Score: ").append(String.format("%.2f", getStatusStability())).append("\n");

        return summary.toString();
    }

    public void clearHistory() {
        statusHistory.clear();
    }

    // Status change event class
    public static class StatusChangeEvent {
        public final ConnectionStatus oldStatus;
        public final ConnectionStatus newStatus;
        public final LocalDateTime timestamp;

        public StatusChangeEvent(ConnectionStatus oldStatus, ConnectionStatus newStatus, LocalDateTime timestamp) {
            this.oldStatus = oldStatus;
            this.newStatus = newStatus;
            this.timestamp = timestamp;
        }

        @Override
        public String toString() {
            return String.format("%s: %s -> %s", timestamp, oldStatus, newStatus);
        }
    }
}