package com.rometransit.app;

import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.data.database.DatabaseManager;
import com.rometransit.service.auth.AuthService;
import com.rometransit.service.data.DataSyncService;
import com.rometransit.service.realtime.RealtimeDataSyncService;
import com.rometransit.model.entity.User;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.File;

public class Damose extends Application {
    private static final String APP_TITLE = "Damose 2.0 - Rome Transit";
    private static final String GTFS_ZIP_PATH = "static_gtfs.zip";

    private GTFSDataManager gtfsDataManager;
    private AuthService authService;
    private DataSyncService dataSyncService;
    private RealtimeDataSyncService realtimeSyncService;
    private Stage primaryStage;
    private com.rometransit.ui.frontend.home.HomeView homeView;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.gtfsDataManager = GTFSDataManager.getInstance();
        this.authService = AuthService.getInstance();
        this.dataSyncService = new DataSyncService();

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setOnCloseRequest(e -> shutdown());

        // Initialize GTFS data in background
        initializeGTFSDataInBackground();

        // Show HomeView directly as main screen
        showMainApplication();
    }

    private void showAuthScreen() {
        com.rometransit.ui.frontend.login.LoginView loginView =
            new com.rometransit.ui.frontend.login.LoginView(primaryStage, authService, this::showMainApplication);
        loginView.show();
    }

    private void showMainApplication() {
        Platform.runLater(() -> {
            // Set title with username if logged in, otherwise just app title
            if (authService.getCurrentUser() != null) {
                primaryStage.setTitle(APP_TITLE + " - " + authService.getCurrentUser().getUsername());
            } else {
                primaryStage.setTitle(APP_TITLE);
            }

            // Create and show the new frontend Home with integrated map
            homeView = new com.rometransit.ui.frontend.home.HomeView(primaryStage);
            homeView.show();
        });
    }

    private User createUserFromAuth() {
        com.rometransit.model.entity.User authUser = authService.getCurrentUser();
        if (authUser == null) {
            throw new IllegalStateException("No authenticated user available");
        }

        User user = new User();
        user.setUserId(authUser.getUsername());
        user.setUsername(authUser.getUsername());
        return user;
    }

    private void initializeGTFSDataInBackground() {
        Thread initThread = new Thread(() -> {
            try {
                System.out.println("🚀 Starting background GTFS initialization...");

                // Load static GTFS data
                String gtfsFilePath = getGTFSResourcePath();

                if (gtfsFilePath != null) {
                    File gtfsFile = new File(gtfsFilePath);

                    if (gtfsFile.exists()) {
                        System.out.println("📍 Loading GTFS static data from: " + gtfsFilePath);

                        // Clear any existing corrupted data first
                        System.out.println("🗑️ Clearing any existing repository data...");
                        dataSyncService.clearRepositoryData();

                        gtfsDataManager.initializeStaticData(gtfsFilePath);

                        // Sync data to repository system
                        System.out.println("🔄 Synchronizing data to repository system...");
                        dataSyncService.syncFromGTFSDataManager(gtfsDataManager);
                    } else {
                        System.out.println("⚠️ GTFS ZIP file not found at: " + gtfsFilePath);
                        System.out.println("🔄 Attempting to use existing cache or download from online...");
                        // GTFSDataManager will try to use existing cache or download
                    }
                } else {
                    System.out.println("⚠️ GTFS file path could not be resolved");
                    System.out.println("🔄 GTFSDataManager will try to use existing cache or download from online...");
                    // GTFSDataManager will try to load existing cache or download
                }

                // Test real-time connection
                System.out.println("📶 Testing real-time connection...");
                boolean realtimeAvailable = gtfsDataManager.testRealtimeConnection();
                if (realtimeAvailable) {
                    System.out.println("✅ Real-time updates available - starting sync service");
                    initializeRealtimeSyncService();
                } else {
                    System.out.println("⚠️ Real-time updates not available - using static data only");
                }

                System.out.println("✅ Background GTFS initialization completed");
            } catch (Exception e) {
                System.err.println("❌ Error during background GTFS initialization: " + e.getMessage());
                e.printStackTrace();
            }
        });
        initThread.setDaemon(true);
        initThread.setName("GTFS-Background-Init");
        initThread.start();
    }

    private void initializeRealtimeSyncService() {
        try {
            System.out.println("🔄 Initializing Real-time Data Sync Service...");
            realtimeSyncService = new RealtimeDataSyncService();
            realtimeSyncService.startSync(30); // Sync every 30 seconds
            System.out.println("✅ Real-time sync service started successfully");
            System.out.println("   📊 Dashboard will receive live data every 30 seconds");
        } catch (Exception e) {
            System.err.println("❌ Failed to start real-time sync service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getGTFSResourcePath() {
        System.out.println("=== GTFS Path Resolution Debug ===");
        System.out.println("Working directory: " + System.getProperty("user.dir"));
        System.out.println("Looking for file: " + GTFS_ZIP_PATH);

        // Method 1: Try classpath resource first (works for JAR and IDE)
        try {
            var resource = getClass().getClassLoader().getResource(GTFS_ZIP_PATH);
            System.out.println("Classpath resource: " + (resource != null ? resource.toString() : "null"));
            if (resource != null) {
                System.out.println("✅ Found file in classpath!");

                // Handle different URL protocols (file:, jar:, etc.)
                String path = resource.getPath();
                if (path != null && !path.isEmpty()) {
                    // Remove leading slash on Windows
                    if (System.getProperty("os.name").toLowerCase().contains("win") &&
                        path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                        path = path.substring(1);
                    }
                    System.out.println("   Using path: " + path);
                    File file = new File(path);
                    if (file.exists()) {
                        return file.getAbsolutePath();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error accessing classpath resource: " + e.getMessage());
        }

        // Method 2: Try relative to working directory (development mode)
        String resourcePath = "src/main/resources/" + GTFS_ZIP_PATH;
        File resourceFile = new File(resourcePath);
        System.out.println("Trying development path: " + resourceFile.getAbsolutePath());
        System.out.println("Development path exists: " + resourceFile.exists());

        if (resourceFile.exists()) {
            System.out.println("✅ Found file at development path!");
            return resourceFile.getAbsolutePath();
        }

        // Method 3: Try in current working directory
        File workingDirFile = new File(GTFS_ZIP_PATH);
        System.out.println("Trying working directory: " + workingDirFile.getAbsolutePath());
        if (workingDirFile.exists()) {
            System.out.println("✅ Found file in working directory!");
            return workingDirFile.getAbsolutePath();
        }

        // Method 4: Try user home directory fallback
        String userHomePath = System.getProperty("user.home") + "/.damose/" + GTFS_ZIP_PATH;
        File userHomeFile = new File(userHomePath);
        System.out.println("Trying user home: " + userHomeFile.getAbsolutePath());
        if (userHomeFile.exists()) {
            System.out.println("✅ Found file in user home!");
            return userHomeFile.getAbsolutePath();
        }

        // If file not found anywhere, return null to trigger download
        System.err.println("❌ GTFS file not found in any location");
        System.err.println("   The application will attempt to download it from online source");
        return null;
    }

    private void shutdown() {
        System.out.println("Shutting down Damose application...");

        // Stop real-time sync service first
        if (realtimeSyncService != null) {
            System.out.println("🛑 Stopping real-time sync service...");
            realtimeSyncService.stopSync();
        }

        if (homeView != null) {
            homeView.shutdown();
        }
        if (gtfsDataManager != null) {
            gtfsDataManager.shutdown();
        }
        // Flush all pending database writes
        DatabaseManager.getInstance().shutdown();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
