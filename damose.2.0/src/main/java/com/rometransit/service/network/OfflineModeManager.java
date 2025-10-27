package com.rometransit.service.network;

import com.rometransit.data.database.DatabaseManager;
import com.rometransit.model.enums.ConnectionStatus;
import com.rometransit.service.network.NetworkManager.ConnectionStatusListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OfflineModeManager implements ConnectionStatusListener {
    private static OfflineModeManager instance;
    
    private final DatabaseManager databaseManager;
    private final Map<String, OfflineData> offlineCache;
    private final Set<String> offlineCapabilities;
    
    private boolean isOfflineMode;
    private LocalDateTime lastOnlineTime;
    private LocalDateTime offlineModeStartTime;

    private OfflineModeManager() {
        this.databaseManager = DatabaseManager.getInstance();
        this.offlineCache = new ConcurrentHashMap<>();
        this.offlineCapabilities = new HashSet<>();
        this.isOfflineMode = false;

        initializeOfflineCapabilities();
    }

    public static synchronized OfflineModeManager getInstance() {
        if (instance == null) {
            instance = new OfflineModeManager();
        }
        return instance;
    }

    /**
     * Register this manager as a listener with NetworkManager.
     * Call this method after both singletons are initialized to avoid circular dependency.
     */
    public void registerWithNetworkManager() {
        NetworkManager.getInstance().addConnectionListener(this);
        System.out.println("   📴 OfflineModeManager registered with NetworkManager");
    }

    @Override
    public void onStatusChanged(ConnectionStatus oldStatus, ConnectionStatus newStatus) {
        switch (newStatus) {
            case ONLINE:
                if (isOfflineMode) {
                    exitOfflineMode();
                }
                lastOnlineTime = LocalDateTime.now();
                break;
            case OFFLINE:
            case ERROR:
                if (!isOfflineMode) {
                    enterOfflineMode();
                }
                break;
            case LIMITED:
                // Stay in current mode but note limited connectivity
                System.out.println("Limited connectivity - maintaining current mode");
                break;
        }
    }

    public boolean isOfflineMode() {
        return isOfflineMode;
    }

    public LocalDateTime getLastOnlineTime() {
        return lastOnlineTime;
    }

    public LocalDateTime getOfflineModeStartTime() {
        return offlineModeStartTime;
    }

    public long getOfflineDurationMinutes() {
        if (!isOfflineMode || offlineModeStartTime == null) {
            return 0;
        }
        return java.time.Duration.between(offlineModeStartTime, LocalDateTime.now()).toMinutes();
    }

    private void enterOfflineMode() {
        isOfflineMode = true;
        offlineModeStartTime = LocalDateTime.now();
        
        System.out.println("Entering offline mode at " + offlineModeStartTime);
        
        // Notify components about offline mode
        prepareOfflineResources();
        disableOnlineFeatures();
        
        System.out.println("Offline mode activated - using cached data");
    }

    private void exitOfflineMode() {
        System.out.println("Exiting offline mode - connection restored");
        
        isOfflineMode = false;
        long offlineDuration = getOfflineDurationMinutes();
        
        // Re-enable online features
        enableOnlineFeatures();
        
        // Sync any offline changes
        syncOfflineChanges();
        
        System.out.println("Online mode restored after " + offlineDuration + " minutes offline");
        
        offlineModeStartTime = null;
    }

    private void initializeOfflineCapabilities() {
        // Define what features work offline
        offlineCapabilities.add("stop_search");
        offlineCapabilities.add("route_search");
        offlineCapabilities.add("static_schedule");
        offlineCapabilities.add("favorites_management");
        offlineCapabilities.add("user_preferences");
        offlineCapabilities.add("cached_arrivals");
        offlineCapabilities.add("offline_maps");
        
        System.out.println("Initialized " + offlineCapabilities.size() + " offline capabilities");
    }

    public boolean isFeatureAvailable(String feature) {
        if (!isOfflineMode) {
            return true; // All features available online
        }
        
        return offlineCapabilities.contains(feature);
    }

    public String getOfflineMessage(String feature) {
        if (!isOfflineMode) {
            return null;
        }
        
        if (isFeatureAvailable(feature)) {
            return "Using offline data - last updated " + getTimeSinceLastOnline();
        } else {
            return "This feature requires an internet connection";
        }
    }

    private String getTimeSinceLastOnline() {
        if (lastOnlineTime == null) {
            return "unknown time ago";
        }
        
        long minutes = java.time.Duration.between(lastOnlineTime, LocalDateTime.now()).toMinutes();
        
        if (minutes < 60) {
            return minutes + " minutes ago";
        } else {
            long hours = minutes / 60;
            return hours + " hours ago";
        }
    }

    public void cacheDataForOfflineUse(String key, Object data) {
        OfflineData offlineData = new OfflineData(data, LocalDateTime.now());
        offlineCache.put(key, offlineData);
        
        // Also persist to database for long-term storage
        databaseManager.save("offline_cache", key, offlineData);
    }

    public Optional<Object> getCachedData(String key) {
        OfflineData cached = offlineCache.get(key);
        if (cached == null) {
            // Try to load from database
            cached = databaseManager.find("offline_cache", key, OfflineData.class).orElse(null);
            if (cached != null) {
                offlineCache.put(key, cached);
            }
        }
        
        if (cached == null || isDataStale(cached)) {
            return Optional.empty();
        }
        
        return Optional.of(cached.data);
    }

    private boolean isDataStale(OfflineData cached) {
        // Data is considered stale after 24 hours
        return cached.timestamp.isBefore(LocalDateTime.now().minusHours(24));
    }

    private void prepareOfflineResources() {
        // Ensure we have essential data cached
        System.out.println("Preparing offline resources...");
        
        // This could pre-cache critical data like:
        // - Stop and route information
        // - Recent vehicle positions
        // - User preferences
        // - Static schedules
    }

    private void disableOnlineFeatures() {
        // Disable features that require internet
        System.out.println("Disabling online-only features");
        // This could disable:
        // - Real-time updates
        // - Data synchronization
        // - External API calls
    }

    private void enableOnlineFeatures() {
        // Re-enable online features
        System.out.println("Re-enabling online features");
        // This could re-enable:
        // - Real-time data fetching
        // - Automatic updates
        // - Cloud synchronization
    }

    private void syncOfflineChanges() {
        // Sync any changes made while offline
        System.out.println("Syncing offline changes...");
        
        // This could sync:
        // - User preference changes
        // - Favorite additions/removals
        // - Usage statistics
    }

    public Set<String> getAvailableFeatures() {
        if (!isOfflineMode) {
            return Set.of("all_features"); // Placeholder for all online features
        }
        
        return Collections.unmodifiableSet(offlineCapabilities);
    }

    public Map<String, Object> getOfflineStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("isOfflineMode", isOfflineMode);
        status.put("lastOnlineTime", lastOnlineTime);
        status.put("offlineModeStartTime", offlineModeStartTime);
        status.put("offlineDurationMinutes", getOfflineDurationMinutes());
        status.put("availableFeatures", getAvailableFeatures());
        status.put("cachedDataCount", offlineCache.size());
        
        return status;
    }

    public void clearOfflineCache() {
        offlineCache.clear();
        databaseManager.clearCollection("offline_cache");
        System.out.println("Offline cache cleared");
    }

    public void clearStaleCache() {
        List<String> staleKeys = new ArrayList<>();
        
        for (Map.Entry<String, OfflineData> entry : offlineCache.entrySet()) {
            if (isDataStale(entry.getValue())) {
                staleKeys.add(entry.getKey());
            }
        }
        
        for (String key : staleKeys) {
            offlineCache.remove(key);
            databaseManager.delete("offline_cache", key);
        }
        
        System.out.println("Cleared " + staleKeys.size() + " stale cache entries");
    }

    // Offline data wrapper class
    private static class OfflineData {
        public final Object data;
        public final LocalDateTime timestamp;

        public OfflineData(Object data, LocalDateTime timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}