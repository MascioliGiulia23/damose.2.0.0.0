package com.rometransit.service.user;

import com.rometransit.data.database.DatabaseManager;
import com.rometransit.model.entity.UserPreferences;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import com.rometransit.data.repository.StopRepository;
import com.rometransit.data.repository.RouteRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PreferencesService {
    private static final String PREFERENCES_COLLECTION = "user_preferences";
    private final DatabaseManager databaseManager;
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;

    public PreferencesService() {
        this.databaseManager = DatabaseManager.getInstance();
        this.stopRepository = new StopRepository();
        this.routeRepository = new RouteRepository();
    }

    public UserPreferences getUserPreferences(String userId) {
        Optional<UserPreferences> prefsOpt = databaseManager.find(PREFERENCES_COLLECTION, userId, UserPreferences.class);
        return prefsOpt.orElse(new UserPreferences(userId));
    }

    public UserPreferences saveUserPreferences(UserPreferences preferences) {
        databaseManager.save(PREFERENCES_COLLECTION, preferences.getUserId(), preferences);
        return preferences;
    }

    public void addFavoriteStop(String userId, String stopId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.addFavoriteStop(stopId);
        saveUserPreferences(prefs);
    }

    public void removeFavoriteStop(String userId, String stopId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.removeFavoriteStop(stopId);
        saveUserPreferences(prefs);
    }

    public void addFavoriteRoute(String userId, String routeId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.addFavoriteRoute(routeId);
        saveUserPreferences(prefs);
    }

    public void removeFavoriteRoute(String userId, String routeId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.removeFavoriteRoute(routeId);
        saveUserPreferences(prefs);
    }

    public List<Stop> getFavoriteStops(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        Set<String> favoriteStopIds = prefs.getFavoriteStopIds();
        
        return stopRepository.findByFavorites(favoriteStopIds.stream().collect(Collectors.toList()));
    }

    public List<Route> getFavoriteRoutes(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        Set<String> favoriteRouteIds = prefs.getFavoriteRouteIds();
        
        return routeRepository.findByFavorites(favoriteRouteIds.stream().collect(Collectors.toList()));
    }

    public boolean isStopFavorite(String userId, String stopId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getFavoriteStopIds().contains(stopId);
    }

    public boolean isRouteFavorite(String userId, String routeId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getFavoriteRouteIds().contains(routeId);
    }

    public void setTheme(String userId, boolean darkTheme) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setDarkThemeEnabled(darkTheme);
        saveUserPreferences(prefs);
    }

    public boolean isDarkThemeEnabled(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.isDarkThemeEnabled();
    }

    public void setRealtimeNotifications(String userId, boolean enabled) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setRealtimeNotificationsEnabled(enabled);
        saveUserPreferences(prefs);
    }

    public boolean areRealtimeNotificationsEnabled(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.isRealtimeNotificationsEnabled();
    }

    public void setAllowUsageStatistics(String userId, boolean allow) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setAllowUsageStatistics(allow);
        saveUserPreferences(prefs);
    }

    public boolean isAllowUsageStatistics(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.isAllowUsageStatistics();
    }

    public void setRefreshInterval(String userId, int seconds) {
        if (seconds < 10 || seconds > 300) {
            throw new IllegalArgumentException("Refresh interval must be between 10 and 300 seconds");
        }
        
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setRefreshIntervalSeconds(seconds);
        saveUserPreferences(prefs);
    }

    public int getRefreshInterval(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getRefreshIntervalSeconds();
    }

    public void setHomeLocation(String userId, double latitude, double longitude) {
        if (Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("Invalid coordinates");
        }
        
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setHomeLatitude(latitude);
        prefs.setHomeLongitude(longitude);
        saveUserPreferences(prefs);
    }

    public void setHomeStop(String userId, String stopId) {
        // Validate that stop exists
        Optional<Stop> stop = stopRepository.findById(stopId);
        if (!stop.isPresent()) {
            throw new IllegalArgumentException("Stop not found: " + stopId);
        }
        
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setHomeStopId(stopId);
        
        // Also set home coordinates to stop location
        Stop homeStop = stop.get();
        prefs.setHomeLatitude(homeStop.getStopLat());
        prefs.setHomeLongitude(homeStop.getStopLon());
        
        saveUserPreferences(prefs);
    }

    public Optional<Stop> getHomeStop(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        String homeStopId = prefs.getHomeStopId();
        
        if (homeStopId == null) {
            return Optional.empty();
        }
        
        return stopRepository.findById(homeStopId);
    }

    public boolean hasHomeLocation(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getHomeLatitude() != 0.0 && prefs.getHomeLongitude() != 0.0;
    }

    public double[] getHomeCoordinates(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return new double[]{prefs.getHomeLatitude(), prefs.getHomeLongitude()};
    }

    public void setMapCenteredOnHome(String userId, boolean centered) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setMapCenteredOnHome(centered);
        saveUserPreferences(prefs);
    }

    public boolean isMapCenteredOnHome(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.isMapCenteredOnHome();
    }

    public void setMaxWalkingDistance(String userId, int meters) {
        if (meters < 100 || meters > 2000) {
            throw new IllegalArgumentException("Walking distance must be between 100 and 2000 meters");
        }
        
        UserPreferences prefs = getUserPreferences(userId);
        prefs.setMaxWalkingDistance(meters);
        saveUserPreferences(prefs);
    }

    public int getMaxWalkingDistance(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getMaxWalkingDistance();
    }

    public void setPreferredLanguage(String userId, String languageCode) {
        if (!languageCode.equals("it") && !languageCode.equals("en")) {
            throw new IllegalArgumentException("Language code must be 'it' or 'en'");
        }

        UserPreferences prefs = getUserPreferences(userId);
        prefs.setPreferredLanguage(languageCode);
        saveUserPreferences(prefs);
    }

    public String getPreferredLanguage(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        String lang = prefs.getPreferredLanguage();
        return lang != null ? lang : "it"; // Default to Italian
    }

    public void clearFavoriteStops(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.getFavoriteStopIds().clear();
        saveUserPreferences(prefs);
    }

    public void clearFavoriteRoutes(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.getFavoriteRouteIds().clear();
        saveUserPreferences(prefs);
    }

    public void clearAllFavorites(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        prefs.getFavoriteStopIds().clear();
        prefs.getFavoriteRouteIds().clear();
        saveUserPreferences(prefs);
    }

    public void resetToDefaults(String userId) {
        UserPreferences defaults = new UserPreferences(userId);
        saveUserPreferences(defaults);
    }

    public int getFavoriteStopCount(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getFavoriteStopIds().size();
    }

    public int getFavoriteRouteCount(String userId) {
        UserPreferences prefs = getUserPreferences(userId);
        return prefs.getFavoriteRouteIds().size();
    }

    public void exportPreferences(String userId, String filePath) {
        UserPreferences prefs = getUserPreferences(userId);
        try {
            String json = databaseManager.getObjectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(prefs);
            
            java.nio.file.Files.write(java.nio.file.Paths.get(filePath), json.getBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to export preferences", e);
        }
    }

    public void importPreferences(String userId, String filePath) {
        try {
            String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)));
            UserPreferences prefs = databaseManager.getObjectMapper().readValue(json, UserPreferences.class);
            
            // Ensure user ID matches
            prefs.setUserId(userId);
            
            saveUserPreferences(prefs);
        } catch (Exception e) {
            throw new RuntimeException("Failed to import preferences", e);
        }
    }
}