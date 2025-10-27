package com.rometransit.util.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static AppConfig instance;
    private final Properties properties;

    private AppConfig() {
        this.properties = new Properties();
        loadConfiguration();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadConfiguration() {
        try {
            // Load from application.properties in resources
            InputStream configStream = getClass().getClassLoader()
                    .getResourceAsStream("config/application.properties");
            
            if (configStream != null) {
                properties.load(configStream);
                configStream.close();
                System.out.println("Configuration loaded successfully");
            } else {
                System.err.println("Could not find application.properties, using defaults");
                loadDefaults();
            }
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
            loadDefaults();
        }
    }

    private void loadDefaults() {
        properties.setProperty("app.name", "Damose 2.0");
        properties.setProperty("app.version", "2.0.0");
        properties.setProperty("app.description", "Rome Transit Application");
        
        properties.setProperty("gtfs.static.file", "static_gtfs.zip");
        properties.setProperty("gtfs.realtime.base_url", "https://dati.comune.roma.it/catalog/it/dataset/c_h501-d-9000#");
        properties.setProperty("gtfs.realtime.vehicle_positions", "vehicle_position.json");
        properties.setProperty("gtfs.realtime.trip_updates", "trip_update.json");
        
        properties.setProperty("gtfs.realtime.update_interval", "30");
        properties.setProperty("gtfs.realtime.timeout", "30");
        properties.setProperty("gtfs.realtime.retry_interval", "60");
        
        properties.setProperty("database.directory", System.getProperty("user.home") + "/.damose/data");
        properties.setProperty("database.backup_enabled", "true");
        properties.setProperty("database.backup_interval", "3600");
        
        properties.setProperty("ui.theme", "light");
        properties.setProperty("ui.language", "it");
        properties.setProperty("ui.startup_tab", "stops");
        properties.setProperty("ui.window.width", "1000");
        properties.setProperty("ui.window.height", "700");
        properties.setProperty("ui.auto_refresh", "true");
        
        properties.setProperty("map.default_zoom", "12");
        properties.setProperty("map.center_latitude", "41.9028");
        properties.setProperty("map.center_longitude", "12.4964");
        properties.setProperty("map.tile_server", "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png");
        properties.setProperty("map.cache_tiles", "true");
        properties.setProperty("map.max_zoom", "18");
        
        properties.setProperty("cache.size", "1000");
        properties.setProperty("cache.ttl", "300");
        properties.setProperty("thread_pool.size", "4");
        
        properties.setProperty("logging.level", "INFO");
        properties.setProperty("logging.file", System.getProperty("user.home") + "/.damose/logs/damose.log");
        properties.setProperty("logging.max_size", "10MB");
        properties.setProperty("logging.max_files", "5");
        
        properties.setProperty("features.offline_mode", "true");
        properties.setProperty("features.favorites", "true");
        properties.setProperty("features.notifications", "true");
        properties.setProperty("features.quality_analytics", "true");
        properties.setProperty("features.export", "true");
    }

    // Application info
    public String getAppName() {
        return properties.getProperty("app.name", "Damose 2.0");
    }

    public String getAppVersion() {
        return properties.getProperty("app.version", "2.0.0");
    }

    public String getAppDescription() {
        return properties.getProperty("app.description", "Rome Transit Application");
    }

    // GTFS Configuration
    public String getGtfsStaticFile() {
        return properties.getProperty("gtfs.static.file", "static_gtfs.zip");
    }

    public String getGtfsStaticDownloadUrl() {
        return properties.getProperty("gtfs.static.download_url",
                "https://romamobilita.it/sites/default/files/rome_static_gtfs.zip");
    }

    public String getGtfsRealtimeBaseUrl() {
        return properties.getProperty("gtfs.realtime.base_url",
                "https://dati.comune.roma.it/catalog/dataset/a7dadb4a-66ae-4eff-8ded-a102064702ba/resource/");
    }

    public String getGtfsVehiclePositionsEndpoint() {
        return properties.getProperty("gtfs.realtime.vehicle_positions", "vehicle_position.json");
    }

    public String getGtfsTripUpdatesEndpoint() {
        return properties.getProperty("gtfs.realtime.trip_updates", "trip_update.json");
    }

    public String getGtfsVehiclePositionsUrl() {
        return properties.getProperty("gtfs.realtime.vehicle_positions_url",
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_vehicle_positions_feed.pb");
    }

    public String getGtfsTripUpdatesUrl() {
        return properties.getProperty("gtfs.realtime.trip_updates_url",
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_trip_updates_feed.pb");
    }

    public String getGtfsServiceAlertsUrl() {
        return properties.getProperty("gtfs.realtime.service_alerts_url",
                "https://romamobilita.it/sites/default/files/rome_rtgtfs_service_alerts_feed.pb");
    }

    public String getStopTimesJsonUrl() {
        return properties.getProperty("gtfs.static.stop_times_json_url", "");
    }

    public int getGtfsUpdateInterval() {
        return Integer.parseInt(properties.getProperty("gtfs.realtime.update_interval", "30"));
    }

    public int getGtfsTimeout() {
        return Integer.parseInt(properties.getProperty("gtfs.realtime.timeout", "30"));
    }

    public int getGtfsRetryInterval() {
        return Integer.parseInt(properties.getProperty("gtfs.realtime.retry_interval", "60"));
    }

    // Database Configuration
    public String getDatabaseDirectory() {
        return properties.getProperty("database.directory", 
                System.getProperty("user.home") + "/.damose/data");
    }

    public boolean isDatabaseBackupEnabled() {
        return Boolean.parseBoolean(properties.getProperty("database.backup_enabled", "true"));
    }

    public int getDatabaseBackupInterval() {
        return Integer.parseInt(properties.getProperty("database.backup_interval", "3600"));
    }

    // UI Configuration
    public String getUiTheme() {
        return properties.getProperty("ui.theme", "light");
    }

    public String getUiLanguage() {
        return properties.getProperty("ui.language", "it");
    }

    public String getStartupTab() {
        return properties.getProperty("ui.startup_tab", "stops");
    }

    public int getWindowWidth() {
        return Integer.parseInt(properties.getProperty("ui.window.width", "1000"));
    }

    public int getWindowHeight() {
        return Integer.parseInt(properties.getProperty("ui.window.height", "700"));
    }

    public boolean isAutoRefreshEnabled() {
        return Boolean.parseBoolean(properties.getProperty("ui.auto_refresh", "true"));
    }

    // Map Configuration
    public int getMapDefaultZoom() {
        return Integer.parseInt(properties.getProperty("map.default_zoom", "12"));
    }

    public double getMapCenterLatitude() {
        return Double.parseDouble(properties.getProperty("map.center_latitude", "41.9028"));
    }

    public double getMapCenterLongitude() {
        return Double.parseDouble(properties.getProperty("map.center_longitude", "12.4964"));
    }

    public String getMapTileServer() {
        return properties.getProperty("map.tile_server", 
                "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png");
    }

    public boolean isMapTileCacheEnabled() {
        return Boolean.parseBoolean(properties.getProperty("map.cache_tiles", "true"));
    }

    public int getMapMaxZoom() {
        return Integer.parseInt(properties.getProperty("map.max_zoom", "18"));
    }

    // Performance Configuration
    public int getCacheSize() {
        return Integer.parseInt(properties.getProperty("cache.size", "1000"));
    }

    public int getCacheTtl() {
        return Integer.parseInt(properties.getProperty("cache.ttl", "300"));
    }

    public int getThreadPoolSize() {
        return Integer.parseInt(properties.getProperty("thread_pool.size", "4"));
    }

    // Logging Configuration
    public String getLoggingLevel() {
        return properties.getProperty("logging.level", "INFO");
    }

    public String getLoggingFile() {
        return properties.getProperty("logging.file", 
                System.getProperty("user.home") + "/.damose/logs/damose.log");
    }

    public String getLoggingMaxSize() {
        return properties.getProperty("logging.max_size", "10MB");
    }

    public int getLoggingMaxFiles() {
        return Integer.parseInt(properties.getProperty("logging.max_files", "5"));
    }

    // Feature Flags
    public boolean isOfflineModeEnabled() {
        return Boolean.parseBoolean(properties.getProperty("features.offline_mode", "true"));
    }

    public boolean isFavoritesEnabled() {
        return Boolean.parseBoolean(properties.getProperty("features.favorites", "true"));
    }

    public boolean areNotificationsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("features.notifications", "true"));
    }

    public boolean isQualityAnalyticsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("features.quality_analytics", "true"));
    }

    public boolean isExportEnabled() {
        return Boolean.parseBoolean(properties.getProperty("features.export", "true"));
    }

    // Generic property access
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    // Additional required methods with different signatures
    public String getString(String key, String defaultValue) {
        return getProperty(key, defaultValue);
    }

    public void setString(String key, String value) {
        properties.setProperty(key, value);
    }

    public int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        return Boolean.parseBoolean(properties.getProperty(key, String.valueOf(defaultValue)));
    }

    public double getDoubleProperty(String key, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    // Configuration info
    public void printConfiguration() {
        System.out.println("=== Damose Configuration ===");
        properties.forEach((key, value) -> {
            System.out.println(key + " = " + value);
        });
        System.out.println("============================");
    }

    public Properties getAllProperties() {
        return new Properties(properties);
    }
}