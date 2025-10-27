package com.rometransit.service.gtfs;
import com.rometransit.ui.listener.VehicleUpdateListener;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.StopTime;
import com.rometransit.model.entity.Trip;
import com.rometransit.model.enums.ConnectionStatus;
import com.rometransit.util.exception.DataException;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Ultra-Optimized GTFS Data Manager
 * Provides unified access to both static and real-time GTFS data
 * with backward compatibility for existing code
 *
 * SINGLETON - Use getInstance() instead of creating new instances
 */
public class GTFSDataManager {

    // Singleton instance
    private static GTFSDataManager instance;
    private static final Object lock = new Object();

    private final com.rometransit.data.repository.GTFSRepository repository;
    private final GTFSOnlineDataService onlineDataService;
    private final GTFSRealtimeParser realtimeParser;
    private final com.rometransit.service.transit.VehicleTrackingService vehicleTrackingService;
    private final com.rometransit.service.transit.RouteCalculationService routeCalculationService;
    private ConnectionStatus connectionStatus;
    private boolean realtimeAvailable;
    private int updateIntervalSeconds = 30;
    private LocalDateTime lastUpdate;
    private LocalDateTime lastRealtimeUpdate;
    private boolean staticDataLoaded = false;
    private boolean autoUpdateEnabled = true;
    private ScheduledExecutorService realtimeScheduler;

    // Cached data
    private Map<String, Stop> stops = new HashMap<>();
    private Map<String, Route> routes = new HashMap<>();
    private Map<String, Trip> trips = new HashMap<>();
    private Map<String, List<Trip>> tripsByRoute = new HashMap<>(); // Secondary index: routeId -> list of trips
    private Map<String, List<com.rometransit.model.entity.Shape>> shapes = new HashMap<>(); // shapeId -> list of shape points
    private Map<String, List<Stop>> stopsByTrip = new HashMap<>(); // tripId -> ordered list of stops
    private Map<String, List<StopTime>> stopTimesByTrip = new HashMap<>(); // tripId -> list of stop_times
    private Map<String, List<StopTime>> stopTimesByStop = new HashMap<>(); // stopId -> list of stop_times
    private List<VehiclePosition> vehiclePositions = new ArrayList<>();

    // Vehicle update listeners
    private final java.util.concurrent.CopyOnWriteArrayList<VehicleUpdateListener> vehicleListeners =
        new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * Get singleton instance
     */
    public static GTFSDataManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new GTFSDataManager();
                }
            }
        }
        return instance;
    }

    /**
     * Private constructor for singleton pattern
     */
    private GTFSDataManager() {
        this.repository = com.rometransit.data.repository.GTFSRepository.getInstance();
        this.onlineDataService = new GTFSOnlineDataService();
        this.realtimeParser = new GTFSRealtimeParser();
        this.vehicleTrackingService = new com.rometransit.service.transit.VehicleTrackingService();
        this.routeCalculationService = new com.rometransit.service.transit.RouteCalculationService();
        this.connectionStatus = ConnectionStatus.CONNECTING;
        this.realtimeAvailable = false;

        System.out.println("🚀 GTFS Data Manager initialized (SQLite-based)");
        System.out.println("   📊 SQLite repository ready");
        System.out.println("   🌐 Online data service ready");
        System.out.println("   🚗 Vehicle tracking service ready");
        System.out.println("   📏 Route calculation service ready");
        System.out.println("   🔄 Auto-updates enabled");

        // Initialize OfflineModeManager integration (after NetworkManager is ready)
        com.rometransit.service.network.OfflineModeManager.getInstance().registerWithNetworkManager();

        // Try to load existing cached data
        tryLoadExistingCache();
    }

    private void tryLoadExistingCache() {
        try {
            System.out.println("🔍 Checking for existing SQLite data...");

            // Check if database has data
            boolean hasData = repository.hasData();

            if (!hasData) {
                System.out.println("⚠️ Database is empty");
                System.out.println("   Will attempt to load from static_gtfs.zip");

                // Try to find and load from static_gtfs.zip
                if (tryRegenerateFromStaticGTFS()) {
                    System.out.println("✅ Successfully loaded data from static_gtfs.zip");
                } else {
                    System.out.println("⚠️ Could not load data - will try online fallback later");
                }
                return;
            }

            // Database has data, load it
            loadCachedData();
            if (!stops.isEmpty() || !routes.isEmpty() || !trips.isEmpty()) {
                staticDataLoaded = true;
                connectionStatus = ConnectionStatus.CONNECTED;
                lastUpdate = LocalDateTime.now();
                System.out.println("✅ Loaded existing data: " + stops.size() + " stops, " +
                                 routes.size() + " routes, " + trips.size() + " trips");
            } else {
                System.out.println("ℹ️  Database is empty, will need to initialize");
            }
        } catch (Exception e) {
            System.out.println("ℹ️  Error loading data: " + e.getMessage());
            System.out.println("   Will attempt to load from static_gtfs.zip");

            // Try to load from static_gtfs.zip
            if (tryRegenerateFromStaticGTFS()) {
                System.out.println("✅ Successfully loaded data from static_gtfs.zip");
            } else {
                System.out.println("⚠️ Could not load data - will try online fallback later");
            }
        }
    }

    /**
     * Attempts to find static_gtfs.zip and regenerate the cache from it.
     * Returns true if successful, false otherwise.
     */
    private boolean tryRegenerateFromStaticGTFS() {
        try {
            System.out.println("🔍 Looking for static_gtfs.zip...");

            // Try multiple locations to find static_gtfs.zip
            String[] possiblePaths = {
                "static_gtfs.zip",
                "src/main/resources/static_gtfs.zip",
                System.getProperty("user.home") + "/.damose/static_gtfs.zip"
            };

            File gtfsFile = null;
            for (String path : possiblePaths) {
                File file = new File(path);
                System.out.println("   Checking: " + file.getAbsolutePath());
                if (file.exists() && file.length() > 0) {
                    gtfsFile = file;
                    System.out.println("   ✅ Found: " + file.getAbsolutePath());
                    break;
                }
            }

            // Try classpath resource
            if (gtfsFile == null) {
                try {
                    var resource = getClass().getClassLoader().getResource("static_gtfs.zip");
                    if (resource != null) {
                        String path = resource.getPath();
                        if (System.getProperty("os.name").toLowerCase().contains("win") &&
                            path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
                            path = path.substring(1);
                        }
                        File file = new File(path);
                        if (file.exists()) {
                            gtfsFile = file;
                            System.out.println("   ✅ Found in classpath: " + file.getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    System.err.println("   ⚠️ Error checking classpath: " + e.getMessage());
                }
            }

            if (gtfsFile == null) {
                System.out.println("❌ static_gtfs.zip not found in any location");
                return false;
            }

            // Parse the GTFS file to regenerate cache
            System.out.println("📋 Regenerating cache from: " + gtfsFile.getAbsolutePath());
            GTFSParser parser = new GTFSParser(repository);
            parser.parseGTFSZip(gtfsFile.getAbsolutePath());

            // Load the newly generated cache
            loadCachedData();

            if (!stops.isEmpty() || !routes.isEmpty() || !trips.isEmpty()) {
                staticDataLoaded = true;
                connectionStatus = ConnectionStatus.CONNECTED;
                lastUpdate = LocalDateTime.now();
                System.out.println("✅ Cache regenerated successfully");
                return true;
            } else {
                System.out.println("⚠️ Cache regenerated but appears empty");
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to regenerate cache from static_gtfs.zip: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // === COMPATIBILITY METHODS FOR EXISTING CODE ===
    
    public void initializeStaticData(String gtfsZipPath) {
        try {
            System.out.println("📋 Initializing static GTFS data from: " + gtfsZipPath);

            // Check if file exists
            java.io.File file = new java.io.File(gtfsZipPath);
            if (!file.exists() || file.length() == 0) {
                System.out.println("⚠️ Local file not found or empty: " + gtfsZipPath);

                // Try to download from online source as fallback
                if (tryDownloadOnlineFallback()) {
                    System.out.println("✅ Successfully downloaded GTFS data from online source");
                    return; // Data already loaded by tryDownloadOnlineFallback
                } else {
                    throw new DataException("No GTFS data available (local or online)");
                }
            }

            // Check if cache needs regeneration using the new validation system
            boolean needsRegeneration = !repository.hasData();

            if (!needsRegeneration) {
                System.out.println("ℹ️ Cache is up-to-date, skipping regeneration");
                System.out.println("   Loading existing cache...");
                try {
                    loadCachedData();
                    if (!stops.isEmpty() || !routes.isEmpty() || !trips.isEmpty()) {
                        connectionStatus = ConnectionStatus.CONNECTED;
                        lastUpdate = LocalDateTime.now();
                        staticDataLoaded = true;
                        System.out.println("✅ Loaded existing cache successfully");
                        System.out.println("   📊 Loaded " + stops.size() + " stops, " + routes.size() + " routes, " + trips.size() + " trips");
                        return;
                    } else {
                        System.out.println("⚠️ Cache loaded but appears empty, will regenerate");
                    }
                } catch (Exception e) {
                    System.out.println("⚠️ Error loading cache: " + e.getMessage());
                    System.out.println("   Will regenerate from source file");
                }
            }

            // Regenerate cache from GTFS ZIP file
            System.out.println("🔄 Regenerating cache from GTFS source file...");
            GTFSParser parser = new GTFSParser(repository);
            parser.parseGTFSZip(gtfsZipPath);

            // Load parsed data into memory
            loadCachedData();

            connectionStatus = ConnectionStatus.CONNECTED;
            lastUpdate = LocalDateTime.now();
            staticDataLoaded = true;
            System.out.println("✅ Static data initialization completed");
            System.out.println("   📊 Loaded " + stops.size() + " stops, " + routes.size() + " routes, " + trips.size() + " trips");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize static data: " + e.getMessage());

            // Final fallback: try to load from previously downloaded online data
            if (tryLoadPreviouslyDownloadedData()) {
                System.out.println("✅ Loaded previously downloaded GTFS data as fallback");
            } else {
                connectionStatus = ConnectionStatus.ERROR;
                staticDataLoaded = false;
                e.printStackTrace();
            }
        }
    }

    /**
     * Try to download GTFS data from online source as fallback
     */
    private boolean tryDownloadOnlineFallback() {
        try {
            System.out.println("🌐 Attempting to download GTFS data from online source...");

            // Download the file
            java.nio.file.Path downloadedFile = onlineDataService.downloadGTFSZip();

            if (downloadedFile == null || !java.nio.file.Files.exists(downloadedFile)) {
                return false;
            }

            // Parse and load the downloaded file
            GTFSParser parser = new GTFSParser(repository);
            parser.parseGTFSZip(downloadedFile.toString());
            loadCachedData();

            connectionStatus = ConnectionStatus.ONLINE;
            lastUpdate = LocalDateTime.now();
            staticDataLoaded = true;

            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Online fallback failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Try to load previously downloaded GTFS data
     */
    private boolean tryLoadPreviouslyDownloadedData() {
        try {
            System.out.println("🔍 Searching for previously downloaded GTFS data...");

            java.nio.file.Path latestFile = onlineDataService.getLatestDownloadedFile();

            if (latestFile == null || !java.nio.file.Files.exists(latestFile)) {
                System.out.println("⚠️ No previously downloaded data found");
                return false;
            }

            System.out.println("📂 Found: " + latestFile);

            // Parse and load
            GTFSParser parser = new GTFSParser(repository);
            parser.parseGTFSZip(latestFile.toString());
            loadCachedData();

            connectionStatus = ConnectionStatus.OFFLINE;
            lastUpdate = LocalDateTime.now();
            staticDataLoaded = true;

            return true;

        } catch (Exception e) {
            System.err.println("⚠️ Failed to load previously downloaded data: " + e.getMessage());
            return false;
        }
    }

    private void loadCachedData() throws DataException {
        System.out.println("📦 Loading GTFS data from SQLite database into memory...");

        try {
            // Load stops
            System.out.println("🔍 Loading stops from database...");
            List<Stop> stopList = repository.loadStops();
            stops.clear();
            for (Stop stop : stopList) {
                stops.put(stop.getStopId(), stop);
            }
            System.out.println("✅ Loaded " + stops.size() + " stops");

            // Load routes
            System.out.println("🔍 Loading routes from database...");
            List<Route> routeList = repository.loadRoutes();
            routes.clear();
            for (Route route : routeList) {
                routes.put(route.getRouteId(), route);
            }
            System.out.println("✅ Loaded " + routes.size() + " routes");

            // Load trips
            System.out.println("🔍 Loading trips from database...");
            List<Trip> tripList = repository.loadTrips();
            trips.clear();
            tripsByRoute.clear();
            for (Trip trip : tripList) {
                trips.put(trip.getTripId(), trip);
                // Build secondary index for fast route lookup
                tripsByRoute.computeIfAbsent(trip.getRouteId(), k -> new ArrayList<>()).add(trip);
            }
            System.out.println("✅ Loaded " + trips.size() + " trips");

            // Load shapes
            loadShapesData();

            // Load stop_times
            loadStopTimesData();

            System.out.println("✅ Data loaded into memory: " + stops.size() + " stops, " +
                             routes.size() + " routes, " + trips.size() + " trips, " + shapes.size() + " shapes, " +
                             stopTimesByTrip.size() + " trips with stop_times");
        } catch (Exception e) {
            System.err.println("❌ Error loading data from database: " + e.getMessage());
            e.printStackTrace();
            throw new DataException("Failed to load data from database", e);
        }
    }

    public boolean testRealtimeConnection() {
        try {
            System.out.println("🔄 Testing real-time connection...");

            // Test connection to GTFS online endpoints
            boolean connected = onlineDataService.testConnection();

            if (connected) {
                realtimeAvailable = true;
                connectionStatus = ConnectionStatus.ONLINE;
                System.out.println("✅ Real-time connection established");
                return true;
            } else {
                realtimeAvailable = false;
                connectionStatus = ConnectionStatus.OFFLINE;
                System.out.println("❌ Real-time connection failed");
                return false;
            }
        } catch (Exception e) {
            realtimeAvailable = false;
            connectionStatus = ConnectionStatus.OFFLINE;
            System.err.println("❌ Real-time connection failed: " + e.getMessage());
            return false;
        }
    }
    
    public void startRealtimeUpdates() {
        if (!realtimeAvailable) {
            System.out.println("⚠️ Real-time updates not available, using cached data");
            connectionStatus = ConnectionStatus.OFFLINE;
            return;
        }

        if (realtimeScheduler != null && !realtimeScheduler.isShutdown()) {
            System.out.println("⚠️ Real-time updates already running");
            return;
        }

        System.out.println("🚀 Starting real-time updates scheduler...");
        System.out.println("   ⏰ Update interval: " + updateIntervalSeconds + " seconds");

        // Create scheduler with single thread
        realtimeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GTFS-Realtime-Updater");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic updates
        realtimeScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    if (autoUpdateEnabled && realtimeAvailable) {
                        System.out.println("⏰ [Scheduled] Updating real-time data...");
                        updateVehiclePositions();
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error in scheduled real-time update: " + e.getMessage());
                }
            },
            0, // Initial delay
            updateIntervalSeconds, // Period
            TimeUnit.SECONDS
        );

        connectionStatus = ConnectionStatus.ONLINE;
        System.out.println("✅ Real-time updates scheduler started");
    }

    public void stopRealtimeUpdates() {
        if (realtimeScheduler == null || realtimeScheduler.isShutdown()) {
            return;
        }

        System.out.println("🛑 Stopping real-time updates scheduler...");
        realtimeScheduler.shutdown();

        try {
            if (!realtimeScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                realtimeScheduler.shutdownNow();
                if (!realtimeScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Scheduler did not terminate");
                }
            }
            System.out.println("✅ Real-time updates scheduler stopped");
        } catch (InterruptedException e) {
            realtimeScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    public boolean isRealtimeAvailable() {
        return realtimeAvailable;
    }
    
    public synchronized void updateVehiclePositions() {
        try {
            System.out.println("🚗 Updating vehicle positions...");

            if (!realtimeAvailable) {
                System.out.println("⚠️ Real-time updates not available, trying cache...");
                loadVehiclePositionsFromDatabase();
                return;
            }

            // Download vehicle positions from online service (Protocol Buffer format)
            byte[] vehicleData = onlineDataService.downloadVehiclePositions();

            // Parse the protobuf data
            List<VehiclePosition> newPositions = realtimeParser.parseVehiclePositions(vehicleData);

            // Update in-memory cache (thread-safe replacement)
            vehiclePositions = new ArrayList<>(newPositions);

            // Save to SQLite database
            try {
                repository.saveVehiclePositions(newPositions);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to save vehicle positions to database: " + e.getMessage());
            }

            // Update VehicleTrackingService with new positions
            for (VehiclePosition vp : newPositions) {
                try {
                    vehicleTrackingService.updateVehiclePosition(
                        vp.getVehicleId(),
                        vp.getLatitude(),
                        vp.getLongitude(),
                        vp.getBearing(),
                        vp.getSpeed(),
                        vp.getStatus()
                    );
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to update tracking for vehicle " + vp.getVehicleId() + ": " + e.getMessage());
                }
            }

            lastUpdate = LocalDateTime.now();
            lastRealtimeUpdate = LocalDateTime.now();
            connectionStatus = ConnectionStatus.ONLINE;

            System.out.println("✅ Updated " + vehiclePositions.size() + " vehicle positions");
            System.out.println("   🚗 Vehicle tracking service synchronized");
            System.out.println("   📊 Data saved to SQLite database");

            // Notify listeners of updated vehicles
            notifyVehicleListeners(vehiclePositions);

        } catch (Exception e) {
            connectionStatus = ConnectionStatus.ERROR;
            System.err.println("❌ Failed to update vehicle positions: " + e.getMessage());
            e.printStackTrace();

            // Try to load from database as fallback
            System.out.println("🔄 Attempting to load from database...");
            loadVehiclePositionsFromDatabase();

            // Notify listeners of failure
            for (VehicleUpdateListener listener : vehicleListeners) {
                try {
                    listener.onUpdateFailed(e);
                } catch (Exception le) {
                    // Ignore listener errors
                }
            }
        }
    }

    /**
     * Load vehicle positions from database (fallback)
     */
    private void loadVehiclePositionsFromDatabase() {
        try {
            List<VehiclePosition> dbPositions = repository.loadVehiclePositions();

            if (!dbPositions.isEmpty()) {
                vehiclePositions = new ArrayList<>(dbPositions);
                System.out.println("✅ Loaded " + vehiclePositions.size() +
                                 " vehicle positions from database");

                // Notify listeners with database data
                notifyVehicleListeners(vehiclePositions);
            } else {
                System.out.println("⚠️ No vehicle positions in database");
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to load from database: " + e.getMessage());
        }
    }

    /**
     * Notifica tutti i listeners registrati degli aggiornamenti veicoli
     */
    private void notifyVehicleListeners(List<VehiclePosition> positions) {
        if (vehicleListeners.isEmpty()) return;

        for (VehicleUpdateListener listener : vehicleListeners) {
            try {
                listener.onVehiclesUpdated(new ArrayList<>(positions));
            } catch (Exception e) {
                System.err.println("⚠️ Listener error: " + e.getMessage());
            }
        }
    }
    
    public String getStatusReport() {
        StringBuilder report = new StringBuilder();
        report.append("GTFS Data Manager Status Report\n");
        report.append("================================\n");
        report.append("Connection Status: ").append(connectionStatus.getDescription()).append("\n");
        report.append("Real-time Available: ").append(realtimeAvailable ? "Yes" : "No").append("\n");
        report.append("Static Data Loaded: ").append(staticDataLoaded ? "Yes" : "No").append("\n");
        report.append("Auto-updates: ").append(autoUpdateEnabled ? "Enabled" : "Disabled").append("\n");
        report.append("Update Interval: ").append(updateIntervalSeconds).append(" seconds\n");
        report.append("Last Update: ").append(lastUpdate != null ? lastUpdate : "Never").append("\n");
        report.append("Last Real-time Update: ").append(lastRealtimeUpdate != null ? lastRealtimeUpdate : "Never").append("\n");
        report.append("Loaded Stops: ").append(stops.size()).append("\n");
        report.append("Loaded Routes: ").append(routes.size()).append("\n");
        report.append("Loaded Trips: ").append(trips.size()).append("\n");
        report.append("Vehicle Positions: ").append(vehiclePositions.size()).append("\n");
        report.append("Memory Usage: ").append(getMemoryUsage()).append("\n");
        return report.toString();
    }
    
    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }
    
    public void setUpdateInterval(int seconds) {
        this.updateIntervalSeconds = seconds;
        System.out.println("⏰ Update interval set to " + seconds + " seconds");
    }
    
    public boolean isStaticDataLoaded() {
        return staticDataLoaded;
    }
    
    public LocalDateTime getLastRealtimeUpdate() {
        return lastRealtimeUpdate;
    }
    
    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }
    
    public void setAutoUpdateEnabled(boolean enabled) {
        this.autoUpdateEnabled = enabled;
        System.out.println("🔄 Auto-update " + (enabled ? "enabled" : "disabled"));
    }
    
    // === DATA ACCESS METHODS ===
    
    public List<Stop> getAllStops() {
        return new ArrayList<>(stops.values());
    }
    
    public List<Route> getAllRoutes() {
        return new ArrayList<>(routes.values());
    }

    public Map<String, Trip> getAllTrips() {
        return new HashMap<>(trips);
    }

    public Stop getStopById(String stopId) {
        return stops.get(stopId);
    }
    
    public Route getRouteById(String routeId) {
        return routes.get(routeId);
    }
    
    public List<VehiclePosition> getVehiclePositions() {
        return new ArrayList<>(vehiclePositions);
    }
    
    public List<ArrivalPrediction> getArrivals(String stopId) {
        // Return empty list for now - would integrate with real-time predictions
        return new ArrayList<>();
    }
    
    public List<ArrivalPrediction> getArrivals(String stopId, int maxResults) {
        return getArrivals(stopId).stream()
                .limit(maxResults)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    // === SEARCH METHODS ===

    public List<Stop> searchStops(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchQuery = query.toLowerCase().trim();
        return stops.values().stream()
                .filter(stop ->
                    (stop.getStopName() != null && stop.getStopName().toLowerCase().contains(searchQuery)) ||
                    (stop.getStopCode() != null && stop.getStopCode().toLowerCase().contains(searchQuery)) ||
                    (stop.getStopId() != null && stop.getStopId().toLowerCase().contains(searchQuery)))
                .sorted((s1, s2) -> {
                    // Sort by exact matches first, then by name
                    String name1 = s1.getStopName() != null ? s1.getStopName().toLowerCase() : "";
                    String name2 = s2.getStopName() != null ? s2.getStopName().toLowerCase() : "";

                    if (name1.equals(searchQuery)) return -1;
                    if (name2.equals(searchQuery)) return 1;
                    if (name1.startsWith(searchQuery) && !name2.startsWith(searchQuery)) return -1;
                    if (name2.startsWith(searchQuery) && !name1.startsWith(searchQuery)) return 1;

                    return name1.compareTo(name2);
                })
                .limit(50) // Limit results for performance
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public List<Route> searchRoutes(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchQuery = query.toLowerCase().trim();
        System.out.println("🔍 Searching routes for: '" + searchQuery + "'");
        System.out.println("   Total routes in memory: " + routes.size());

        // Detect if query is numeric (for bus/tram numbers like 70, 71, 64)
        boolean isNumericQuery = searchQuery.matches("\\d+");
        if (isNumericQuery) {
            System.out.println("   Detected numeric route search");
        }

        List<Route> results = routes.values().stream()
                .filter(route -> {
                    // Search in route_short_name (usually contains numeric route number)
                    if (route.getRouteShortName() != null) {
                        String shortName = route.getRouteShortName().toLowerCase();
                        // For numeric queries, prioritize exact numeric matches
                        if (isNumericQuery) {
                            // Extract numeric part from route name (e.g., "70", "71A", "N7")
                            String numericPart = shortName.replaceAll("[^0-9]", "");
                            if (numericPart.equals(searchQuery)) {
                                return true; // Exact numeric match
                            }
                        }
                        if (shortName.contains(searchQuery)) {
                            return true;
                        }
                    }

                    // Search in route_long_name
                    if (route.getRouteLongName() != null &&
                        route.getRouteLongName().toLowerCase().contains(searchQuery)) {
                        return true;
                    }

                    // Search in route_id
                    if (route.getRouteId() != null &&
                        route.getRouteId().toLowerCase().contains(searchQuery)) {
                        return true;
                    }

                    return false;
                })
                .sorted((r1, r2) -> {
                    // PRIORITY SORTING: Exact matches first, then partial matches
                    String name1 = r1.getRouteShortName() != null ? r1.getRouteShortName().toLowerCase() : "";
                    String name2 = r2.getRouteShortName() != null ? r2.getRouteShortName().toLowerCase() : "";

                    // For numeric queries, prioritize exact numeric matches
                    if (isNumericQuery) {
                        String num1 = name1.replaceAll("[^0-9]", "");
                        String num2 = name2.replaceAll("[^0-9]", "");

                        boolean exactNum1 = num1.equals(searchQuery);
                        boolean exactNum2 = num2.equals(searchQuery);

                        if (exactNum1 && !exactNum2) return -1;
                        if (!exactNum1 && exactNum2) return 1;
                    }

                    // Check for exact matches first
                    boolean exactMatch1 = name1.equals(searchQuery);
                    boolean exactMatch2 = name2.equals(searchQuery);

                    if (exactMatch1 && !exactMatch2) return -1;
                    if (!exactMatch1 && exactMatch2) return 1;

                    // Check for "starts with" matches (higher priority than "contains")
                    boolean startsWith1 = name1.startsWith(searchQuery);
                    boolean startsWith2 = name2.startsWith(searchQuery);

                    if (startsWith1 && !startsWith2) return -1;
                    if (!startsWith1 && startsWith2) return 1;

                    // For numeric routes, sort numerically
                    if (isNumericQuery) {
                        try {
                            Integer num1 = Integer.parseInt(name1.replaceAll("[^0-9]", ""));
                            Integer num2 = Integer.parseInt(name2.replaceAll("[^0-9]", ""));
                            int numCompare = num1.compareTo(num2);
                            if (numCompare != 0) return numCompare;
                        } catch (NumberFormatException e) {
                            // Fallback to alphabetic sort
                        }
                    }

                    // Finally, sort alphabetically
                    return name1.compareTo(name2);
                })
                .limit(50) // Limit results for performance
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        System.out.println("   Found " + results.size() + " matching routes");
        if (!results.isEmpty() && results.size() <= 10) {
            results.forEach(r -> System.out.println("   - " + r.getRouteShortName() +
                " (" + r.getRouteId() + ") - " + r.getRouteLongName()));
        }

        return results;
    }

    public List<Trip> searchTrips(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String searchQuery = query.toLowerCase().trim();
        return trips.values().stream()
                .filter(trip ->
                    (trip.getTripHeadsign() != null && trip.getTripHeadsign().toLowerCase().contains(searchQuery)) ||
                    (trip.getTripId() != null && trip.getTripId().toLowerCase().contains(searchQuery)) ||
                    (trip.getRouteId() != null && trip.getRouteId().toLowerCase().contains(searchQuery)))
                .sorted((t1, t2) -> {
                    String name1 = t1.getTripHeadsign() != null ? t1.getTripHeadsign() : t1.getTripId();
                    String name2 = t2.getTripHeadsign() != null ? t2.getTripHeadsign() : t2.getTripId();

                    if (name1 == null) name1 = "";
                    if (name2 == null) name2 = "";

                    return name1.compareTo(name2);
                })
                .limit(50) // Limit results for performance
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    // === ROUTE VISUALIZATION METHODS ===

    /**
     * Get all trips for a specific route (optimized with secondary index)
     */
    public List<Trip> getTripsByRoute(String routeId) {
        if (routeId == null) return new ArrayList<>();

        // Use secondary index for O(1) lookup instead of O(n) stream filtering
        List<Trip> routeTrips = tripsByRoute.getOrDefault(routeId, Collections.emptyList());

        // Return sorted copy
        List<Trip> result = new ArrayList<>(routeTrips);
        result.sort((t1, t2) -> {
            // Sort by direction ID, then headsign
            int dirComp = Integer.compare(t1.getDirectionId(), t2.getDirectionId());
            if (dirComp != 0) return dirComp;

            String h1 = t1.getTripHeadsign() != null ? t1.getTripHeadsign() : "";
            String h2 = t2.getTripHeadsign() != null ? t2.getTripHeadsign() : "";
            return h1.compareTo(h2);
        });

        return result;
    }

    /**
     * Get all trips for a specific route (alias for getTripsByRoute)
     */
    public List<Trip> getTripsForRoute(String routeId) {
        return getTripsByRoute(routeId);
    }

    /**
     * Get all unique stops for a specific route
     * This method collects stops from all trips on the route and returns unique stops
     */
    public List<Stop> getStopsForRoute(String routeId) {
        if (routeId == null) return new ArrayList<>();

        // Get all trips for this route
        List<Trip> trips = getTripsByRoute(routeId);
        if (trips.isEmpty()) {
            return new ArrayList<>();
        }

        // Use LinkedHashSet to maintain order and avoid duplicates
        java.util.LinkedHashSet<Stop> uniqueStops = new java.util.LinkedHashSet<>();

        // Collect stops from all trips
        for (Trip trip : trips) {
            List<Stop> tripStops = getStopsByTrip(trip.getTripId());
            uniqueStops.addAll(tripStops);
        }

        return new ArrayList<>(uniqueStops);
    }

    /**
     * Get shape points for a specific shape ID
     */
    public List<com.rometransit.model.entity.Shape> getShapePoints(String shapeId) {
        return shapes.getOrDefault(shapeId, new ArrayList<>());
    }

    /**
     * Get ordered list of stops for a specific trip
     */
    public List<Stop> getStopsByTrip(String tripId) {
        return stopsByTrip.getOrDefault(tripId, new ArrayList<>());
    }

    /**
     * Get a trip by ID
     */
    public Trip getTripById(String tripId) {
        return trips.get(tripId);
    }

    /**
     * Load shapes data (called after GTFS parsing)
     */
    public void loadShapesData() {
        try {
            System.out.println("🔍 Loading shapes from database...");
            List<com.rometransit.model.entity.Shape> shapeList = repository.loadShapes();

            // Group by shape ID and sort by sequence
            shapes.clear();
            for (com.rometransit.model.entity.Shape shape : shapeList) {
                shapes.computeIfAbsent(shape.getShapeId(), k -> new ArrayList<>()).add(shape);
            }

            // Sort each shape's points by sequence
            shapes.values().forEach(pointList ->
                pointList.sort((s1, s2) -> Integer.compare(s1.getShapePtSequence(), s2.getShapePtSequence()))
            );

            System.out.println("✅ Loaded " + shapes.size() + " shapes");
        } catch (Exception e) {
            System.err.println("❌ Failed to load shapes: " + e.getMessage());
        }
    }

    /**
     * Load stop_times data (called after GTFS parsing)
     */
    public void loadStopTimesData() {
        try {
            System.out.println("🔍 Loading stop_times from database...");
            System.out.println("   This may take a while for large datasets...");
            long startTime = System.currentTimeMillis();

            List<StopTime> stopTimeList = repository.loadStopTimes();

            // Group by trip ID and stop ID
            stopTimesByTrip.clear();
            stopTimesByStop.clear();

            int count = 0;
            for (StopTime stopTime : stopTimeList) {
                // Index by trip ID
                stopTimesByTrip.computeIfAbsent(stopTime.getTripId(), k -> new ArrayList<>()).add(stopTime);

                // Index by stop ID
                stopTimesByStop.computeIfAbsent(stopTime.getStopId(), k -> new ArrayList<>()).add(stopTime);

                count++;
                if (count % 50000 == 0) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double rate = count * 1000.0 / elapsed;
                    System.out.printf("   Indexed %,d stop_times (%.0f/sec)%n", count, rate);
                }
            }

            // Sort each trip's stop_times by sequence
            stopTimesByTrip.values().forEach(stopTimesList ->
                stopTimesList.sort(Comparator.comparingInt(StopTime::getStopSequence))
            );

            long elapsed = System.currentTimeMillis() - startTime;
            System.out.printf("✅ Loaded and indexed %,d stop_times in %,dms%n", count, elapsed);
            System.out.printf("   %,d trips, %,d stops%n", stopTimesByTrip.size(), stopTimesByStop.size());

            // Build stopsByTrip index from stopTimesByTrip
            System.out.println("🔍 Building stopsByTrip index from stop_times...");
            long indexStartTime = System.currentTimeMillis();
            stopsByTrip.clear();

            int tripsProcessed = 0;
            int stopsAdded = 0;
            for (Map.Entry<String, List<StopTime>> entry : stopTimesByTrip.entrySet()) {
                String tripId = entry.getKey();
                List<StopTime> stopTimes = entry.getValue();

                // Create ordered list of Stop objects from stop_times
                List<Stop> stopsForTrip = new ArrayList<>();
                for (StopTime stopTime : stopTimes) {
                    Stop stop = stops.get(stopTime.getStopId());
                    if (stop != null) {
                        stopsForTrip.add(stop);
                        stopsAdded++;
                    }
                }

                if (!stopsForTrip.isEmpty()) {
                    stopsByTrip.put(tripId, stopsForTrip);
                }

                tripsProcessed++;
                if (tripsProcessed % 1000 == 0) {
                    long indexElapsed = System.currentTimeMillis() - indexStartTime;
                    double rate = tripsProcessed * 1000.0 / indexElapsed;
                    System.out.printf("   Indexed %,d trips (%.0f trips/sec)%n", tripsProcessed, rate);
                }
            }

            long indexElapsed = System.currentTimeMillis() - indexStartTime;
            System.out.printf("✅ Built stopsByTrip index in %,dms%n", indexElapsed);
            System.out.printf("   %,d trips with stops, %,d total stop references%n",
                            stopsByTrip.size(), stopsAdded);

        } catch (Exception e) {
            System.err.println("❌ Failed to load stop_times: " + e.getMessage());
            System.err.println("   The app will work with limited arrival prediction accuracy");
        }
    }

    /**
     * Get stop_times for a specific trip
     */
    public List<StopTime> getStopTimesForTrip(String tripId) {
        return stopTimesByTrip.getOrDefault(tripId, new ArrayList<>());
    }

    /**
     * Get stop_times for a specific stop
     */
    public List<StopTime> getStopTimesForStop(String stopId) {
        return stopTimesByStop.getOrDefault(stopId, new ArrayList<>());
    }

    // === ASYNC METHODS FOR PERFORMANCE ===
    
    public CompletableFuture<Void> initializeStaticDataAsync(String gtfsZipPath) {
        return CompletableFuture.runAsync(() -> initializeStaticData(gtfsZipPath));
    }

    /**
     * Load static GTFS data from a local file path
     * This is an alias for initializeStaticData for better API clarity
     * @param path Path to GTFS ZIP file
     */
    public void loadStaticGTFS(String path) {
        initializeStaticData(path);
    }

    /**
     * Load static GTFS data asynchronously from a local file path
     * @param path Path to GTFS ZIP file
     * @return CompletableFuture that completes when loading is done
     */
    public CompletableFuture<Void> loadStaticGTFSAsync(String path) {
        return initializeStaticDataAsync(path);
    }

    public CompletableFuture<Boolean> testRealtimeConnectionAsync() {
        return CompletableFuture.supplyAsync(this::testRealtimeConnection);
    }
    
    public CompletableFuture<Void> updateVehiclePositionsAsync() {
        return CompletableFuture.runAsync(this::updateVehiclePositions);
    }
    
    // === PERFORMANCE MONITORING ===
    
    public void startPerformanceMonitoring() {
        System.out.println("📊 Performance monitoring started");
        // Would start background monitoring thread
    }
    
    public Map<String, Object> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", connectionStatus.name());
        health.put("realtimeAvailable", realtimeAvailable);
        health.put("staticDataLoaded", staticDataLoaded);
        health.put("lastUpdate", lastUpdate);
        health.put("lastRealtimeUpdate", lastRealtimeUpdate);
        health.put("memoryUsage", getMemoryUsage());
        health.put("dataLoaded", !stops.isEmpty() || staticDataLoaded);
        health.put("autoUpdates", autoUpdateEnabled);
        return health;
    }
    
    public void performEmergencyCacheCleanup() {
        System.out.println("🧹 Performing emergency cache cleanup...");
        try {
            // Clear in-memory caches
            vehiclePositions.clear();
            
            // Suggest garbage collection
            System.gc();
            
            System.out.println("✅ Emergency cache cleanup completed");
        } catch (Exception e) {
            System.err.println("❌ Cache cleanup failed: " + e.getMessage());
        }
    }
    
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return String.format("%.1f MB", used / (1024.0 * 1024.0));
    }
    
    // === LIFECYCLE MANAGEMENT ===
    
    public void shutdown() {
        System.out.println("🛑 Shutting down Ultra-Optimized GTFS Data Manager...");

        // Stop real-time updates
        stopRealtimeUpdates();

        // Cleanup resources
        performEmergencyCacheCleanup();

        // Update status
        connectionStatus = ConnectionStatus.OFFLINE;
        realtimeAvailable = false;

        System.out.println("✅ GTFS Data Manager shutdown completed");
    }
    
    // === UTILITY METHODS ===
    
    public void printSystemStatus() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("📊 ULTRA-OPTIMIZED GTFS DATA MANAGER STATUS");
        System.out.println("=".repeat(60));
        System.out.println("Status: " + connectionStatus.getDescription());
        System.out.println("Real-time: " + (realtimeAvailable ? "🟢 Available" : "🔴 Unavailable"));
        System.out.println("Static data: " + (staticDataLoaded ? "🟢 Loaded" : "🔴 Not loaded"));
        System.out.println("Auto-updates: " + (autoUpdateEnabled ? "🟢 Enabled" : "🔴 Disabled"));
        System.out.println("Memory usage: " + getMemoryUsage());
        System.out.println("Last update: " + (lastUpdate != null ? lastUpdate : "Never"));
        System.out.println("=".repeat(60));
    }

    // === ONLINE DATA METHODS ===

    /**
     * Download updated GTFS static data from online source
     */
    public void downloadUpdatedGTFSData() throws DataException {
        System.out.println("📥 Downloading updated GTFS data from online source...");

        try {
            // Check if update is available
            if (onlineDataService.isUpdateAvailable()) {
                System.out.println("   ℹ️ Update available, proceeding with download...");
            } else {
                System.out.println("   ℹ️ Data is up to date, but downloading anyway...");
            }

            // Download the file
            java.nio.file.Path downloadedFile = onlineDataService.downloadGTFSZip();

            // Parse and load the downloaded file
            System.out.println("   📋 Parsing downloaded GTFS data...");
            initializeStaticData(downloadedFile.toString());

            System.out.println("✅ Successfully downloaded and loaded updated GTFS data");

        } catch (Exception e) {
            System.err.println("❌ Failed to download updated GTFS data: " + e.getMessage());
            throw new DataException("Failed to download updated GTFS data", e);
        }
    }

    /**
     * Check if GTFS update is available online
     */
    public boolean checkForUpdates() {
        return onlineDataService.isUpdateAvailable();
    }

    /**
     * Update arrival predictions for a specific stop
     */
    public List<ArrivalPrediction> updateArrivalPredictions(String stopId) {
        try {
            System.out.println("🕐 Updating arrival predictions for stop: " + stopId);

            if (!realtimeAvailable) {
                System.out.println("⚠️ Real-time updates not available");
                return new ArrayList<>();
            }

            // Download trip updates (Protocol Buffer format)
            byte[] tripUpdatesData = onlineDataService.downloadTripUpdates();

            // Parse trip updates
            List<ArrivalPrediction> allPredictions = realtimeParser.parseTripUpdates(tripUpdatesData);

            // Filter for the requested stop
            List<ArrivalPrediction> stopPredictions = allPredictions.stream()
                    .filter(p -> stopId.equals(p.getStopId()))
                    .sorted((p1, p2) -> {
                        if (p1.getPredictedArrival() == null) return 1;
                        if (p2.getPredictedArrival() == null) return -1;
                        return p1.getPredictedArrival().compareTo(p2.getPredictedArrival());
                    })
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

            System.out.println("✅ Found " + stopPredictions.size() + " predictions for stop " + stopId);
            return stopPredictions;

        } catch (Exception e) {
            System.err.println("❌ Failed to update arrival predictions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get online data service for advanced operations
     */
    public GTFSOnlineDataService getOnlineDataService() {
        return onlineDataService;
    }

    /**
     * Get real-time parser for advanced operations
     */
    public GTFSRealtimeParser getRealtimeParser() {
        return realtimeParser;
    }

    /**
     * Get vehicle tracking service for advanced vehicle operations
     */
    public com.rometransit.service.transit.VehicleTrackingService getVehicleTrackingService() {
        return vehicleTrackingService;
    }

    /**
     * Get route calculation service for route planning operations
     */
    public com.rometransit.service.transit.RouteCalculationService getRouteCalculationService() {
        return routeCalculationService;
    }

    // === VEHICLE UPDATE LISTENERS ===

    /**
     * Registra listener per aggiornamenti veicoli real-time
     */
    public void addVehicleUpdateListener(VehicleUpdateListener listener) {
        if (listener != null && !vehicleListeners.contains(listener)) {
            vehicleListeners.add(listener);
            System.out.println("✅ Registered vehicle update listener: " +
                             listener.getClass().getSimpleName());
        }
    }

    /**
     * Rimuove listener per aggiornamenti veicoli
     */
    public void removeVehicleUpdateListener(VehicleUpdateListener listener) {
        if (vehicleListeners.remove(listener)) {
            System.out.println("✅ Removed vehicle update listener: " +
                             listener.getClass().getSimpleName());
        }
    }

    /**
     * Ottiene numero di listeners registrati
     */
    public int getVehicleListenerCount() {
        return vehicleListeners.size();
    }

    // === VEHICLE FILTERING METHODS ===

    /**
     * Ottiene le posizioni dei veicoli per una specifica route
     * Filtra automaticamente veicoli "stale" (non aggiornati da >5 minuti)
     */
    public List<VehiclePosition> getVehiclePositionsForRoute(String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            return new ArrayList<>();
        }

        return vehiclePositions.stream()
            .filter(vp -> routeId.equals(vp.getRouteId()))
            .filter(vp -> !vp.isStale()) // Escludi dati vecchi
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Ottiene posizioni veicoli per una route, con fallback a veicoli simulati
     * se non ci sono dati real-time disponibili
     */
    public List<VehiclePosition> getVehiclePositionsForRouteWithSimulation(String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            return new ArrayList<>();
        }

        // Prima prova con dati real-time
        List<VehiclePosition> realVehicles = getVehiclePositionsForRoute(routeId);

        if (!realVehicles.isEmpty()) {
            System.out.println("✅ [GTFSDataManager] Using " + realVehicles.size() +
                             " real-time vehicles for route " + routeId);
            return realVehicles;
        }

        // Se non ci sono veicoli real-time, genera simulati
        System.out.println("🎭 [GTFSDataManager] No real-time vehicles for route " + routeId);

        // TODO: Implement VehicleSimulationService to generate simulated vehicles
        // when real-time data is not available
        /*
        try {
            com.rometransit.service.simulation.VehicleSimulationService simService =
                com.rometransit.service.simulation.VehicleSimulationService.getInstance();
            return simService.generateSimulatedVehicles(routeId);
        } catch (Exception e) {
            System.err.println("❌ Failed to generate simulated vehicles: " + e.getMessage());
            return new ArrayList<>();
        }
        */
        return new ArrayList<>();
    }

    /**
     * Ottiene TUTTE le posizioni veicoli valide (non stale)
     */
    public List<VehiclePosition> getActiveVehiclePositions() {
        return vehiclePositions.stream()
            .filter(vp -> !vp.isStale())
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Verifica se ci sono veicoli attivi per una route
     */
    public boolean hasActiveVehicles(String routeId) {
        return getVehiclePositionsForRoute(routeId).size() > 0;
    }

    /**
     * Conta veicoli attivi per route
     */
    public int countActiveVehiclesForRoute(String routeId) {
        return getVehiclePositionsForRoute(routeId).size();
    }

    /**
     * Ottiene veicolo specifico per ID
     */
    public VehiclePosition getVehicleById(String vehicleId) {
        if (vehicleId == null || vehicleId.isEmpty()) {
            return null;
        }

        return vehiclePositions.stream()
            .filter(vp -> vehicleId.equals(vp.getVehicleId()))
            .filter(vp -> !vp.isStale())
            .findFirst()
            .orElse(null);
    }
}