package com.rometransit.ui.frontend.home;

import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.ui.frontend.dashboard.DashboardView;
import com.rometransit.ui.frontend.settings.SettingsView;
import com.rometransit.ui.frontend.register.RegisterView;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.ui.component.NativeMapView;
import com.rometransit.service.auth.AuthService;
import com.rometransit.service.user.FavoriteService;
import com.rometransit.ui.component.DirectionSelector;
import com.rometransit.ui.waypoint.*;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Trip;
import com.rometransit.model.entity.Shape;
import com.rometransit.model.entity.User;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.service.map.MapService;
import com.rometransit.service.map.StopVisualizationService;
import com.rometransit.service.map.TileManager;
import com.rometransit.service.network.ConnectionStatusMonitor;
import com.rometransit.service.network.OfflineModeManager;
import com.rometransit.service.network.NetworkManager;
import com.rometransit.service.transit.VehicleTrackingService;
import com.rometransit.service.transit.ArrivalPredictionService;
import com.rometransit.service.transit.RouteCalculationService;
import com.rometransit.service.notification.RealtimeNotificationMonitor;
import com.rometransit.ui.notification.NotificationPopupManager;
import com.rometransit.model.enums.ConnectionStatus;
import com.rometransit.util.logging.Logger;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class HomeView {

    private Stage stage;
    private NativeMapView mapView;
    private GTFSDataManager gtfsDataManager;
    private TextField searchField;
    private ListView<String> searchResultsList;

    // Box components
    private ListView<String> stopBusesListView;  // Box stella: autobus che passano dalla fermata
    private TextArea lineDetailsTextArea;  // Box punto esclamativo: dettagli linea
    private Button addToFavoritesButton;  // Stellina per aggiungere route ai preferiti
    private FavoriteService favoriteService;  // Service per gestire i preferiti
    private Stop currentSelectedStop;
    private Route currentSelectedRoute;

    // Integrated services
    private MapService mapService;
    private ConnectionStatusMonitor connectionStatusMonitor;
    private OfflineModeManager offlineModeManager;
    private VehicleTrackingService vehicleTrackingService;
    private ArrivalPredictionService arrivalPredictionService;
    private RouteCalculationService routeCalculationService;
    private StopVisualizationService stopVisualizationService;
    private TileManager tileManager;
    private AuthService authService;
    private LanguageManager languageManager;
    private NotificationPopupManager notificationPopupManager;
    private RealtimeNotificationMonitor notificationMonitor;

    // UI Components
    private Label connectionStatusLabel;
    private Label offlineModeIndicator;
    private ProgressIndicator loadingIndicator;
    private HBox statusBar;
    private HBox mapToolbar;
    private Timeline statusUpdater;
    private Timeline vehicleUpdater;
    private WebView webView;
    private WebEngine webEngine;

    // Route visualization tracking
    private Map<String, RoutePathWaypoint> activeRouteWaypoints = new HashMap<>();
    private Map<String, List<StopMarkerWaypoint>> activeStopWaypoints = new HashMap<>();
    private Map<String, VehicleMarkerWaypoint> activeVehicleWaypoints = new HashMap<>();
    private RouteRepository routeRepository;

    public HomeView(Stage stage) {
        this.stage = stage;
        this.gtfsDataManager = GTFSDataManager.getInstance();
        initializeServices();
    }

    /**
     * Initialize all services for map, tracking, and network monitoring
     */
    private void initializeServices() {
        try {
            Logger.log("Initializing HomeView services...");

            // Initialize map services
            this.mapService = new MapService();
            this.tileManager = new TileManager();
            this.stopVisualizationService = StopVisualizationService.getInstance();

            // Initialize repositories
            this.routeRepository = new RouteRepository();

            // Initialize network services
            this.connectionStatusMonitor = new ConnectionStatusMonitor();
            this.offlineModeManager = OfflineModeManager.getInstance();
            this.offlineModeManager.registerWithNetworkManager();

            // Initialize transit services - use shared instance from GTFSDataManager
            this.vehicleTrackingService = gtfsDataManager.getVehicleTrackingService();
            this.arrivalPredictionService = ArrivalPredictionService.getInstance();
            this.routeCalculationService = new RouteCalculationService();

            // Initialize auth service
            this.authService = AuthService.getInstance();

            // Initialize favorites service
            this.favoriteService = new FavoriteService();

            // Initialize language manager
            this.languageManager = LanguageManager.getInstance();

            // Initialize notification services
            this.notificationPopupManager = NotificationPopupManager.getInstance();
            this.notificationMonitor = RealtimeNotificationMonitor.getInstance();

            // Warmup tile cache for Rome in background
            CompletableFuture.runAsync(() -> {
                try {
                    Logger.log("Starting tile cache warmup for Rome...");
                    tileManager.warmupCacheForRome();
                } catch (Exception e) {
                    Logger.log("Error warming up cache: " + e.getMessage());
                }
            });

            Logger.log("All services initialized successfully");
        } catch (Exception e) {
            Logger.log("Error initializing services: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Show all live vehicles currently operating in Rome
     */
    private void showAllLiveVehicles() {
        try {
            Logger.log("🚍 Showing all live vehicles in Rome...");

            if (mapView == null || vehicleTrackingService == null) {
                Logger.log("⚠️ MapView or VehicleTrackingService not available");
                return;
            }

            // Clear previous visualizations
            clearMap();

            // Get all active vehicles
            List<VehiclePosition> allVehicles = vehicleTrackingService.getAllActiveVehicles();
            Logger.log("📌 Found " + allVehicles.size() + " active vehicles");

            // Add all vehicles to map as GREEN DOTS
            for (VehiclePosition vehiclePos : allVehicles) {
                VehicleMarkerWaypoint vehicleWaypoint = new VehicleMarkerWaypoint(
                        "vehicle_" + vehiclePos.getVehicleId(),
                        vehiclePos
                );
                mapView.addWaypoint(vehicleWaypoint);
                activeVehicleWaypoints.put(vehiclePos.getVehicleId(), vehicleWaypoint);
            }

            // Center on Rome
            mapView.centerOnRome();
            mapView.setZoom(12);

            // Start auto-update for all vehicles
            startGlobalVehicleTracking();

            Logger.log("✅ " + allVehicles.size() + " live vehicles displayed");
        } catch (Exception e) {
            Logger.log("❌ Error showing all live vehicles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start tracking all vehicles (global tracking)
     */
    private void startGlobalVehicleTracking() {
        try {
            // Stop existing tracker if any
            if (vehicleUpdater != null) {
                vehicleUpdater.stop();
            }

            // Create new tracker that updates every 30 seconds
            vehicleUpdater = new Timeline(new KeyFrame(javafx.util.Duration.seconds(30), e -> {
                updateAllVehiclePositions();
            }));
            vehicleUpdater.setCycleCount(Timeline.INDEFINITE);
            vehicleUpdater.play();

            Logger.log("🔄 Global vehicle tracking started");
        } catch (Exception e) {
            Logger.log("❌ Error starting global vehicle tracking: " + e.getMessage());
        }
    }

    /**
     * Update all vehicle positions (called every 30 seconds for global tracking)
     */
    private void updateAllVehiclePositions() {
        try {
            if (vehicleTrackingService == null || mapView == null) return;

            List<VehiclePosition> allVehicles = vehicleTrackingService.getAllActiveVehicles();

            // Update or add vehicles
            for (VehiclePosition vehiclePos : allVehicles) {
                VehicleMarkerWaypoint existingWaypoint = activeVehicleWaypoints.get(vehiclePos.getVehicleId());

                if (existingWaypoint != null) {
                    // Update existing waypoint
                    existingWaypoint.updatePosition(vehiclePos);
                } else {
                    // Add new vehicle waypoint
                    VehicleMarkerWaypoint newWaypoint = new VehicleMarkerWaypoint(
                            "vehicle_" + vehiclePos.getVehicleId(),
                            vehiclePos
                    );
                    mapView.addWaypoint(newWaypoint);
                    activeVehicleWaypoints.put(vehiclePos.getVehicleId(), newWaypoint);
                }
            }

            // Remove vehicles that are no longer active
            Set<String> activeVehicleIds = allVehicles.stream()
                    .map(VehiclePosition::getVehicleId)
                    .collect(java.util.stream.Collectors.toSet());

            List<String> vehiclesToRemove = new ArrayList<>();
            for (String vehicleId : activeVehicleWaypoints.keySet()) {
                if (!activeVehicleIds.contains(vehicleId)) {
                    vehiclesToRemove.add(vehicleId);
                }
            }

            for (String vehicleId : vehiclesToRemove) {
                VehicleMarkerWaypoint waypoint = activeVehicleWaypoints.remove(vehicleId);
                if (waypoint != null) {
                    mapView.removeWaypoint(waypoint);
                }
            }

            // Refresh map
            mapView.repaint();

            Logger.log("🔄 Vehicle positions updated: " + allVehicles.size() + " active vehicles");
        } catch (Exception e) {
            Logger.log("❌ Error updating all vehicle positions: " + e.getMessage());
        }
    }

    /**
     * Create status bar showing connection and cache information
     */
    private HBox createStatusBar() {
        HBox statusBar = new HBox(15);
        statusBar.setPadding(new Insets(8));
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setStyle("-fx-background-color: #f5f5f5; -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");

        // Connection status label
        connectionStatusLabel = new Label(LanguageManager.getInstance().getString("home.statusChecking"));
        connectionStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");

        // Offline mode indicator
        offlineModeIndicator = new Label(LanguageManager.getInstance().getString("home.online"));
        offlineModeIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: green;");

        // Cache stats label
        Label cacheStatsLabel = new Label(LanguageManager.getInstance().getString("home.cacheLoading"));
        cacheStatsLabel.setStyle("-fx-font-size: 11px;");

        // Data status label
        Label dataStatusLabel = new Label(LanguageManager.getInstance().getString("home.gtfsReady"));
        dataStatusLabel.setStyle("-fx-font-size: 11px;");

        // Loading indicator
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setPrefSize(20, 20);
        loadingIndicator.setVisible(false);

        statusBar.getChildren().addAll(
                connectionStatusLabel,
                new Label("|"),
                offlineModeIndicator,
                new Label("|"),
                cacheStatsLabel,
                new Label("|"),
                dataStatusLabel,
                loadingIndicator
        );

        // Update status bar periodically
        setupStatusUpdater(cacheStatsLabel);

        return statusBar;
    }

    /**
     * Setup periodic status updates
     */
    private void setupStatusUpdater(Label cacheStatsLabel) {
        statusUpdater = new Timeline(new KeyFrame(javafx.util.Duration.seconds(5), e -> {
            try {
                updateConnectionStatus();
                updateCacheStats(cacheStatsLabel);
            } catch (Exception ex) {
                Logger.log("Error updating status: " + ex.getMessage());
            }
        }));
        statusUpdater.setCycleCount(Timeline.INDEFINITE);
        statusUpdater.play();
    }

    /**
     * Update connection status display
     */
    private void updateConnectionStatus() {
        try {
            ConnectionStatus status = NetworkManager.getInstance().getConnectionStatus();
            Platform.runLater(() -> {
                connectionStatusLabel.setText(LanguageManager.getInstance().getString("home.status") + " " + status.name());
                switch (status) {
                    case ONLINE:
                        connectionStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: green;");
                        offlineModeIndicator.setText(LanguageManager.getInstance().getString("home.statusOnline"));
                        offlineModeIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: green;");
                        break;
                    case OFFLINE:
                        connectionStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: red;");
                        offlineModeIndicator.setText(LanguageManager.getInstance().getString("home.statusOffline"));
                        offlineModeIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: red;");
                        break;
                    case LIMITED:
                        connectionStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: orange;");
                        offlineModeIndicator.setText(LanguageManager.getInstance().getString("home.statusLimited"));
                        offlineModeIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: orange;");
                        break;
                    case ERROR:
                        connectionStatusLabel.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: darkred;");
                        offlineModeIndicator.setText(LanguageManager.getInstance().getString("home.statusError"));
                        offlineModeIndicator.setStyle("-fx-font-size: 11px; -fx-text-fill: darkred;");
                        break;
                }
            });
        } catch (Exception e) {
            Logger.log("Error updating connection status: " + e.getMessage());
        }
    }

    /**
     * Update cache statistics
     */
    private void updateCacheStats() {
        try {
            String stats = tileManager.getCacheStats();
            Logger.log("Cache stats: " + stats);
        } catch (Exception e) {
            Logger.log("Error getting cache stats: " + e.getMessage());
        }
    }

    /**
     * Update cache statistics display
     */
    private void updateCacheStats(Label cacheStatsLabel) {
        try {
            String stats = tileManager.getCacheStats();
            Platform.runLater(() -> cacheStatsLabel.setText(
                LanguageManager.getInstance().getString("home.cacheLabel") + ": " + stats));
        } catch (Exception e) {
            Logger.log("Error updating cache stats: " + e.getMessage());
        }
    }

    /**
     * Setup connection monitoring with automatic offline mode switching
     */
    private void setupConnectionMonitoring() {
        try {
            Logger.log("Setting up connection monitoring...");

            // Add listener for connection status changes
            connectionStatusMonitor.addListener((oldStatus, newStatus) -> {
                Platform.runLater(() -> {
                    Logger.log("Connection status changed: " + oldStatus + " -> " + newStatus);
                    updateConnectionStatus();

                    // Show notification to user
                    if (newStatus == ConnectionStatus.OFFLINE) {
                        Logger.log("Switching to offline mode due to connection loss");
                    } else if (newStatus == ConnectionStatus.ONLINE && oldStatus == ConnectionStatus.OFFLINE) {
                        Logger.log("Connection restored - online mode active");
                    }
                });
            });

            // Register with NetworkManager to forward status changes to our monitor
            NetworkManager.getInstance().addConnectionListener((oldStatus, newStatus) -> {
                connectionStatusMonitor.onStatusChange(oldStatus, newStatus);
            });
            NetworkManager.getInstance().initialize();

            Logger.log("Connection monitoring setup complete");
        } catch (Exception e) {
            Logger.log("Error setting up connection monitoring: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Show route with direction selector
     */
    private void showRouteWithDirection(Route route) {
        try {
            Logger.log("Showing route with direction selector: " + route.getRouteShortName());

            // Check if route has multiple directions
            String[] directionNames = routeCalculationService.getRouteDirectionNames(route.getRouteId());
            boolean hasTwoDirections = routeCalculationService.routeHasTwoDirections(route.getRouteId());

            if (hasTwoDirections && directionNames != null) {
                // Show direction selector dialog
                DirectionSelector selector = new DirectionSelector(stage);
                selector.setRoute(route);
                selector.setDirectionNames(directionNames[0], directionNames[1]);

                selector.setOnDirectionSelected(direction -> {
                    Logger.log("Direction selected: " + direction);
                    visualizeRouteWithDirection(route, direction);
                });

                selector.show();
            } else {
                // Single direction, show directly
                visualizeRouteWithDirection(route, 0);
            }
        } catch (Exception e) {
            Logger.log("Error showing route with direction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visualize route with specific direction - COMPLETE VISUALIZATION
     * Shows: RED LINES (path), BLUE DOTS (stops), GREEN DOTS (vehicles)
     */
    private void visualizeRouteWithDirection(Route route, int direction) {
        try {
            Logger.log("🎨 Visualizing complete route " + route.getRouteShortName() + " direction " + direction);

            if (mapView == null) {
                Logger.log("⚠️ MapView is null, cannot visualize route");
                return;
            }

            // Clear ALL previous visualizations to show only the new route
            clearMap();

            // 1. Draw RED LINE - Route Path
            visualizeRoutePath(route, direction);

            // 2. Draw BLUE DOTS - Stops along route
            visualizeRouteStops(route, direction);

            // 3. Draw GREEN DOTS - Vehicles on route
            visualizeRouteVehicles(route);

            // 4. Fit map to show entire route
            fitMapToRoute(route);

            // 5. Start vehicle tracking updates
            startVehicleTracking(route.getRouteId());

            Logger.log("✅ Route visualization complete");
        } catch (Exception e) {
            Logger.log("❌ Error visualizing route: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visualize route path as RED LINE
     */
    private void visualizeRoutePath(Route route, int direction) {
        try {
            Logger.log("📍 Drawing route path for " + route.getRouteShortName());

            // Get all trips for this route
            List<Trip> trips = gtfsDataManager.getTripsForRoute(route.getRouteId());
            if (trips.isEmpty()) {
                Logger.log("⚠️ No trips found for route");
                return;
            }

            // Filter trips by direction
            List<Trip> directionTrips = trips.stream()
                    .filter(t -> t.getDirectionId() == direction)
                    .filter(t -> t.getShapeId() != null)
                    .collect(java.util.stream.Collectors.toList());

            if (directionTrips.isEmpty()) {
                Logger.log("⚠️ No trips found for direction " + direction);
                // Try any direction as fallback
                directionTrips = trips.stream()
                        .filter(t -> t.getShapeId() != null)
                        .collect(java.util.stream.Collectors.toList());
            }

            if (directionTrips.isEmpty()) {
                Logger.log("⚠️ No trips with shape ID found");
                return;
            }

            // Find the trip with the most shape points (most complete path)
            Trip bestTrip = null;
            int maxShapePoints = 0;

            Logger.log("🔍 Analyzing " + directionTrips.size() + " trips for direction " + direction);

            for (Trip trip : directionTrips) {
                String tripShapeId = trip.getShapeId();
                List<Shape> shapePoints = gtfsDataManager.getShapePoints(tripShapeId);
                Logger.log("  Trip: " + trip.getTripId() +
                          " | ShapeID: " + tripShapeId +
                          " | Points: " + shapePoints.size() +
                          " | Headsign: " + trip.getTripHeadsign());

                if (shapePoints.size() > maxShapePoints) {
                    maxShapePoints = shapePoints.size();
                    bestTrip = trip;
                }
            }

            if (bestTrip == null) {
                Logger.log("⚠️ No valid trip found");
                return;
            }

            String shapeId = bestTrip.getShapeId();
            Logger.log("✅ SELECTED: Trip " + bestTrip.getTripId() +
                      " | ShapeID: " + shapeId +
                      " | " + maxShapePoints + " points" +
                      " | Headsign: " + bestTrip.getTripHeadsign());

            // Get shape points
            List<Shape> shapePoints = gtfsDataManager.getShapePoints(shapeId);
            if (shapePoints.isEmpty()) {
                Logger.log("⚠️ No shape points found for shape ID: " + shapeId);
                return;
            }

            // Get stops to compare with shapes
            List<Stop> routeStops = gtfsDataManager.getStopsForRoute(route.getRouteId());

            // Log coordinate range to verify they're correct
            if (!shapePoints.isEmpty()) {
                double minLat = shapePoints.stream().mapToDouble(Shape::getShapePtLat).min().orElse(0);
                double maxLat = shapePoints.stream().mapToDouble(Shape::getShapePtLat).max().orElse(0);
                double minLon = shapePoints.stream().mapToDouble(Shape::getShapePtLon).min().orElse(0);
                double maxLon = shapePoints.stream().mapToDouble(Shape::getShapePtLon).max().orElse(0);

                Logger.log("📍 Shape coordinates range:");
                Logger.log("   Latitude:  " + String.format("%.6f", minLat) + " to " + String.format("%.6f", maxLat));
                Logger.log("   Longitude: " + String.format("%.6f", minLon) + " to " + String.format("%.6f", maxLon));
                Logger.log("   First point: (" + String.format("%.6f", shapePoints.get(0).getShapePtLat()) +
                          ", " + String.format("%.6f", shapePoints.get(0).getShapePtLon()) + ")");
                Logger.log("   Last point:  (" + String.format("%.6f", shapePoints.get(shapePoints.size()-1).getShapePtLat()) +
                          ", " + String.format("%.6f", shapePoints.get(shapePoints.size()-1).getShapePtLon()) + ")");

                // Check if shape coordinates match stops (detect corrupted GTFS data)
                if (!routeStops.isEmpty()) {
                    double stopMinLat = routeStops.stream().mapToDouble(Stop::getStopLat).min().orElse(0);
                    double stopMaxLat = routeStops.stream().mapToDouble(Stop::getStopLat).max().orElse(0);
                    double stopMinLon = routeStops.stream().mapToDouble(Stop::getStopLon).min().orElse(0);
                    double stopMaxLon = routeStops.stream().mapToDouble(Stop::getStopLon).max().orElse(0);

                    // Calculate distance between shape center and stops center
                    double shapeCenterLat = (minLat + maxLat) / 2;
                    double shapeCenterLon = (minLon + maxLon) / 2;
                    double stopsCenterLat = (stopMinLat + stopMaxLat) / 2;
                    double stopsCenterLon = (stopMinLon + stopMaxLon) / 2;

                    double distance = com.rometransit.util.math.GeoUtils.calculateDistance(
                        shapeCenterLat, shapeCenterLon, stopsCenterLat, stopsCenterLon
                    );

                    if (distance > 5.0) { // More than 5km difference = wrong shape!
                        Logger.log("⚠️⚠️⚠️ WARNING: Shape seems WRONG! Distance from stops: " +
                                  String.format("%.2f", distance) + " km");
                        Logger.log("⚠️ GTFS data may be corrupted. Using STOP-BASED path instead.");

                        // Use stops to create shape points
                        shapePoints = createShapePointsFromStops(routeStops, direction);
                        Logger.log("✅ Created " + shapePoints.size() + " shape points from stops");
                    } else {
                        Logger.log("✅ Shape coordinates look correct (distance from stops: " +
                                  String.format("%.2f", distance) + " km)");
                    }
                }
            }

            Logger.log("📌 Found " + shapePoints.size() + " shape points for shape ID: " + shapeId);

            // Create RoutePathWaypoint (RED LINE)
            RoutePathWaypoint routePathWaypoint = new RoutePathWaypoint(
                    "route_path_" + route.getRouteId() + "_" + direction,
                    route,
                    shapePoints,
                    route.getRouteShortName()
            );

            // Add to map
            mapView.addWaypoint(routePathWaypoint);
            activeRouteWaypoints.put(route.getRouteId(), routePathWaypoint);

            Logger.log("✅ Route path waypoint added");
        } catch (Exception e) {
            Logger.log("❌ Error visualizing route path: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visualize route stops as BLUE DOTS
     */
    private void visualizeRouteStops(Route route, int direction) {
        try {
            Logger.log("🔵 Drawing stops for route " + route.getRouteShortName());

            // Get stops for this route
            List<Stop> stops = gtfsDataManager.getStopsForRoute(route.getRouteId());
            Logger.log("📌 Found " + stops.size() + " stops");

            // Log stop coordinate range for comparison with shapes
            if (!stops.isEmpty()) {
                double minLat = stops.stream().mapToDouble(Stop::getStopLat).min().orElse(0);
                double maxLat = stops.stream().mapToDouble(Stop::getStopLat).max().orElse(0);
                double minLon = stops.stream().mapToDouble(Stop::getStopLon).min().orElse(0);
                double maxLon = stops.stream().mapToDouble(Stop::getStopLon).max().orElse(0);

                Logger.log("📍 Stops coordinates range:");
                Logger.log("   Latitude:  " + String.format("%.6f", minLat) + " to " + String.format("%.6f", maxLat));
                Logger.log("   Longitude: " + String.format("%.6f", minLon) + " to " + String.format("%.6f", maxLon));
                Logger.log("   First stop: " + stops.get(0).getStopName() +
                          " at (" + String.format("%.6f", stops.get(0).getStopLat()) +
                          ", " + String.format("%.6f", stops.get(0).getStopLon()) + ")");
                Logger.log("   Last stop:  " + stops.get(stops.size()-1).getStopName() +
                          " at (" + String.format("%.6f", stops.get(stops.size()-1).getStopLat()) +
                          ", " + String.format("%.6f", stops.get(stops.size()-1).getStopLon()) + ")");
            }

            List<StopMarkerWaypoint> stopWaypoints = new ArrayList<>();

            for (Stop stop : stops) {
                // Create StopMarkerWaypoint (BLUE DOT)
                StopMarkerWaypoint stopWaypoint = new StopMarkerWaypoint(stop);
                mapView.addWaypoint(stopWaypoint);
                stopWaypoints.add(stopWaypoint);
            }

            activeStopWaypoints.put(route.getRouteId(), stopWaypoints);
            Logger.log("✅ Added " + stopWaypoints.size() + " stop markers");
        } catch (Exception e) {
            Logger.log("❌ Error visualizing stops: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Visualize vehicles on route as GREEN DOTS
     */
    private void visualizeRouteVehicles(Route route) {
        try {
            Logger.log("🚍 Drawing vehicles for route " + route.getRouteShortName());

            if (vehicleTrackingService == null) {
                Logger.log("⚠️ VehicleTrackingService not available");
                return;
            }

            // Get vehicles for this route
            List<VehiclePosition> vehicles = vehicleTrackingService.getVehiclesForRoute(route.getRouteId());
            Logger.log("📌 Found " + vehicles.size() + " vehicles");

            for (VehiclePosition vehiclePos : vehicles) {
                // Create VehicleMarkerWaypoint (GREEN DOT)
                VehicleMarkerWaypoint vehicleWaypoint = new VehicleMarkerWaypoint(
                        "vehicle_" + vehiclePos.getVehicleId(),
                        vehiclePos
                );
                mapView.addWaypoint(vehicleWaypoint);
                activeVehicleWaypoints.put(vehiclePos.getVehicleId(), vehicleWaypoint);
            }

            Logger.log("✅ Added " + vehicles.size() + " vehicle markers");
        } catch (Exception e) {
            Logger.log("❌ Error visualizing vehicles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start automatic vehicle position updates
     */
    private void startVehicleTracking(String routeId) {
        try {
            // Stop existing tracker if any
            if (vehicleUpdater != null) {
                vehicleUpdater.stop();
            }

            // Create new tracker that updates every 30 seconds
            vehicleUpdater = new Timeline(new KeyFrame(javafx.util.Duration.seconds(30), e -> {
                updateVehiclePositions(routeId);
            }));
            vehicleUpdater.setCycleCount(Timeline.INDEFINITE);
            vehicleUpdater.play();

            Logger.log("🔄 Vehicle tracking started for route " + routeId);
        } catch (Exception e) {
            Logger.log("❌ Error starting vehicle tracking: " + e.getMessage());
        }
    }

    /**
     * Update vehicle positions (called every 30 seconds)
     */
    private void updateVehiclePositions(String routeId) {
        try {
            if (vehicleTrackingService == null || mapView == null) return;

            List<VehiclePosition> vehicles = vehicleTrackingService.getVehiclesForRoute(routeId);

            for (VehiclePosition vehiclePos : vehicles) {
                VehicleMarkerWaypoint existingWaypoint = activeVehicleWaypoints.get(vehiclePos.getVehicleId());

                if (existingWaypoint != null) {
                    // Update existing waypoint
                    existingWaypoint.updatePosition(vehiclePos);
                } else {
                    // Add new vehicle waypoint
                    VehicleMarkerWaypoint newWaypoint = new VehicleMarkerWaypoint(
                            "vehicle_" + vehiclePos.getVehicleId(),
                            vehiclePos
                    );
                    mapView.addWaypoint(newWaypoint);
                    activeVehicleWaypoints.put(vehiclePos.getVehicleId(), newWaypoint);
                }
            }

            // Refresh map
            mapView.repaint();
        } catch (Exception e) {
            Logger.log("❌ Error updating vehicle positions: " + e.getMessage());
        }
    }

    /**
     * Clear all visualization for a specific route
     */
    private void clearRouteVisualization(String routeId) {
        try {
            // Remove route path waypoint
            RoutePathWaypoint routeWaypoint = activeRouteWaypoints.remove(routeId);
            if (routeWaypoint != null) {
                mapView.removeWaypoint(routeWaypoint);
            }

            // Remove stop waypoints
            List<StopMarkerWaypoint> stopWaypoints = activeStopWaypoints.remove(routeId);
            if (stopWaypoints != null) {
                for (StopMarkerWaypoint waypoint : stopWaypoints) {
                    mapView.removeWaypoint(waypoint);
                }
            }

            // Remove vehicle waypoints
            for (VehicleMarkerWaypoint waypoint : new ArrayList<>(activeVehicleWaypoints.values())) {
                if (waypoint.getRouteId() != null && waypoint.getRouteId().equals(routeId)) {
                    mapView.removeWaypoint(waypoint);
                    activeVehicleWaypoints.remove(waypoint.getVehicleId());
                }
            }

            Logger.log("🗑️ Cleared visualization for route " + routeId);
        } catch (Exception e) {
            Logger.log("❌ Error clearing route visualization: " + e.getMessage());
        }
    }

    /**
     * Fit map view to show entire route
     */
    private void fitMapToRoute(Route route) {
        try {
            List<Stop> stops = gtfsDataManager.getStopsForRoute(route.getRouteId());
            if (stops.isEmpty()) return;

            // Calculate bounding box
            double minLat = stops.stream().mapToDouble(Stop::getStopLat).min().orElse(0);
            double maxLat = stops.stream().mapToDouble(Stop::getStopLat).max().orElse(0);
            double minLon = stops.stream().mapToDouble(Stop::getStopLon).min().orElse(0);
            double maxLon = stops.stream().mapToDouble(Stop::getStopLon).max().orElse(0);

            // Center on route
            double centerLat = (minLat + maxLat) / 2;
            double centerLon = (minLon + maxLon) / 2;

            mapView.setCenter(centerLat, centerLon);
            mapView.setZoom(13); // Good zoom level for route overview

            Logger.log("🎯 Map centered on route");
        } catch (Exception e) {
            Logger.log("❌ Error fitting map to route: " + e.getMessage());
        }
    }

    /**
     * Show stop details panel with arrival predictions
     */
    private void showStopDetails(Stop stop) {
        try {
            Logger.log("🔵 Showing details for stop: " + stop.getStopName());

            if (loadingIndicator != null) {
                loadingIndicator.setVisible(true);
            }

            // Visualize stop on map as BLUE DOT
            if (mapView != null) {
                StopMarkerWaypoint stopWaypoint = new StopMarkerWaypoint(stop);
                mapView.addWaypoint(stopWaypoint);
                mapView.setCenter(stop.getStopLat(), stop.getStopLon());
                mapView.setZoom(16);
            }

            // Get arrival predictions for this stop
            CompletableFuture.supplyAsync(() -> {
                return arrivalPredictionService.getUpcomingArrivals(stop.getStopId(), 10);
            }).thenAccept(arrivals -> {
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                    }

                    Logger.log("✅ Found " + arrivals.size() + " upcoming arrivals for stop");

                    // Log arrival information
                    for (com.rometransit.model.dto.ArrivalPrediction arrival : arrivals) {
                        Logger.log("  - Route " + arrival.getRoute().getRouteShortName() +
                                " arriving at " + arrival.getPredictedArrival() +
                                " (realtime: " + arrival.isRealtime() + ")");
                    }
                });
            }).exceptionally(ex -> {
                Platform.runLater(() -> {
                    if (loadingIndicator != null) {
                        loadingIndicator.setVisible(false);
                    }
                    Logger.log("❌ Error getting arrivals: " + ex.getMessage());
                });
                return null;
            });
        } catch (Exception e) {
            Logger.log("❌ Error showing stop details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create and configure stop buses box (Box Stella ⭐)
     */
    private void createStopBusesBox() {
        stopBusesListView = new ListView<>();
        stopBusesListView.setPrefSize(359, 308);
        stopBusesListView.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 15px; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 4, 0, 0, 4); " +
            "-fx-font-family: 'Poppins'; -fx-font-size: 13px;"
        );

        Label placeholder = new Label(LanguageManager.getInstance().getString("home.stopPlaceholder"));
        placeholder.setStyle("-fx-text-alignment: center; -fx-font-family: 'Poppins';");
        stopBusesListView.setPlaceholder(placeholder);

        // Doppio click listener per selezionare linea
        stopBusesListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                String selectedItem = stopBusesListView.getSelectionModel().getSelectedItem();
                if (selectedItem != null) {
                    handleBusLineDoubleClick(selectedItem);
                }
            }
        });
    }

    /**
     * Create and configure line details box (Box Punto Esclamativo ❗)
     */
    private void createLineDetailsBox() {
        lineDetailsTextArea = new TextArea();
        lineDetailsTextArea.setPrefSize(359, 287);
        lineDetailsTextArea.setEditable(false);
        lineDetailsTextArea.setWrapText(true);
        lineDetailsTextArea.setStyle(
            "-fx-control-inner-background: white; " +
            "-fx-background-color: white; " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 15px; " +
            "-fx-background-radius: 15px; " +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 4, 0, 0, 4); " +
            "-fx-font-family: 'Poppins'; -fx-font-size: 13px; " +
            "-fx-padding: 10px;"
        );
        lineDetailsTextArea.setPromptText(LanguageManager.getInstance().getString("home.routePlaceholder"));

        // Add double-click handler for selecting directions
        lineDetailsTextArea.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && currentSelectedRoute != null) {
                handleDirectionDoubleClick();
            }
        });
    }

    /**
     * Create button to add route to favorites (stellina)
     */
    private void createAddToFavoritesButton() {
        addToFavoritesButton = new Button(LanguageManager.getInstance().getString("home.favoriteButton"));
        addToFavoritesButton.setPrefSize(35, 35);
        addToFavoritesButton.setStyle(
            "-fx-background-color: transparent; " +
            "-fx-font-size: 20px; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 0;"
        );
        addToFavoritesButton.setVisible(false); // Initially hidden

        addToFavoritesButton.setOnAction(event -> handleAddToFavorites());
    }

    /**
     * Handle add route to favorites
     */
    private void handleAddToFavorites() {
        try {
            if (currentSelectedRoute == null) {
                Logger.log("⚠️ No route selected to add to favorites");
                return;
            }

            // Get current user ID
            User currentUser = authService.getCurrentUser();
            if (currentUser == null) {
                Logger.log("⚠️ User not logged in");
                showNotification(LanguageManager.getInstance().getString("home.loginRequired"));
                return;
            }
            String userId = currentUser.getUserId();

            // Check if already favorited
            if (favoriteService.isFavorite(userId, currentSelectedRoute.getRouteId(),
                                          com.rometransit.model.entity.Favorite.FavoriteType.ROUTE)) {
                Logger.log("⚠️ Route already in favorites");
                showNotification(LanguageManager.getInstance().getString("home.alreadyFavorite"));
                return;
            }

            // Add to favorites
            boolean success = favoriteService.addFavorite(userId, currentSelectedRoute.getRouteId(),
                                                         com.rometransit.model.entity.Favorite.FavoriteType.ROUTE);

            if (success) {
                Logger.log("⭐ Route added to favorites: " + currentSelectedRoute.getRouteShortName());
                showNotification(LanguageManager.getInstance().getString("home.addedToFavorites", currentSelectedRoute.getRouteShortName()));
                addToFavoritesButton.setText(LanguageManager.getInstance().getString("home.favoriteButton")); // Keep star
            } else {
                Logger.log("❌ Failed to add route to favorites");
                showNotification(LanguageManager.getInstance().getString("home.addFavoriteError"));
            }

        } catch (Exception e) {
            Logger.log("❌ Error adding to favorites: " + e.getMessage());
            e.printStackTrace();
            showNotification(LanguageManager.getInstance().getString("home.addFavoriteError"));
        }
    }

    /**
     * Show notification message to user
     */
    private void showNotification(String message) {
        // Create a simple label notification
        javafx.scene.control.Label notification = new javafx.scene.control.Label(message);
        notification.setStyle(
            "-fx-background-color: rgba(0, 0, 0, 0.8); " +
            "-fx-text-fill: white; " +
            "-fx-padding: 15px 25px; " +
            "-fx-background-radius: 10px; " +
            "-fx-font-family: 'Poppins'; " +
            "-fx-font-size: 14px;"
        );

        // Position it in the center-bottom of the screen
        AnchorPane root = (AnchorPane) stage.getScene().getRoot();
        notification.setLayoutX((stage.getWidth() - 300) / 2);
        notification.setLayoutY(stage.getHeight() - 150);

        root.getChildren().add(notification);

        // Auto-hide after 3 seconds
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(event -> root.getChildren().remove(notification));
        pause.play();
    }

    /**
     * Handle double click on line details to select a direction or visualize a route from upcoming arrivals
     */
    private void handleDirectionDoubleClick() {
        try {
            // Get the text content and caret position
            String text = lineDetailsTextArea.getText();
            int caretPosition = lineDetailsTextArea.getCaretPosition();

            // Find which line was clicked
            String[] lines = text.split("\n");
            int currentPos = 0;
            String clickedLine = null;

            for (String line : lines) {
                int lineLength = line.length() + 1; // +1 for newline
                if (caretPosition >= currentPos && caretPosition < currentPos + lineLength) {
                    clickedLine = line;
                    break;
                }
                currentPos += lineLength;
            }

            if (clickedLine == null) {
                Logger.log("⚠️ No line detected at click position");
                return;
            }

            Logger.log("🖱️ Double-clicked on line: " + clickedLine);

            // CASE 1: User clicked on a route direction (when viewing route details)
            if (currentSelectedRoute != null) {
                if (clickedLine.contains("➤ Direzione 0:") || clickedLine.contains("➤ Percorso principale")) {
                    Logger.log("📍 Visualizing Direction 0");
                    visualizeRouteWithDirection(currentSelectedRoute, 0);
                } else if (clickedLine.contains("➤ Direzione 1:")) {
                    Logger.log("📍 Visualizing Direction 1");
                    visualizeRouteWithDirection(currentSelectedRoute, 1);
                } else {
                    Logger.log("ℹ️ Clicked line is not a direction");
                }
                return;
            }

            // CASE 2: User clicked on a bus line in upcoming arrivals (when viewing stop details)
            String routePrefix = "🚌 " + LanguageManager.getInstance().getString("home.routePrefix");
            if (currentSelectedStop != null && clickedLine.contains(routePrefix)) {
                Logger.log("🚌 User clicked on upcoming arrival line");

                // Extract route number from string: "   1. 🚌 Route/Linea XX → HEADSIGN | HH:MM"
                String routeNumber = extractRouteNumberFromArrival(clickedLine);

                if (routeNumber != null) {
                    Logger.log("🔍 Extracted route number: " + routeNumber);

                    // Find the route
                    List<Route> routes = gtfsDataManager.searchRoutes(routeNumber);
                    if (!routes.isEmpty()) {
                        Route route = routes.get(0);
                        Logger.log("✅ Found route: " + route.getRouteShortName() + " - " + route.getRouteLongName());

                        // Visualize the route with direction selector and real-time vehicles
                        showRouteWithDirection(route);

                        // Also show route details in box punto esclamativo
                        showLineDetailsInBox(route);
                    } else {
                        Logger.log("⚠️ No route found for: " + routeNumber);
                        showNotification(LanguageManager.getInstance().getString("home.routeNotFound"));
                    }
                } else {
                    Logger.log("⚠️ Could not extract route number from: " + clickedLine);
                }
            }

        } catch (Exception e) {
            Logger.log("❌ Error handling direction double-click: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Extract route number from upcoming arrival line
     * Format: "   1. 🚌 Route/Linea XX → HEADSIGN | HH:MM"
     */
    private String extractRouteNumberFromArrival(String line) {
        try {
            // Find route prefix and extract the number after it
            String routePrefix = LanguageManager.getInstance().getString("home.routePrefix");
            int prefixIndex = line.indexOf(routePrefix);
            if (prefixIndex == -1) {
                return null;
            }

            String afterPrefix = line.substring(prefixIndex + routePrefix.length()).trim();

            // Find the next delimiter (→, space, |)
            int endIndex = afterPrefix.length();
            for (String delimiter : new String[]{"→", " →", "|"}) {
                int idx = afterPrefix.indexOf(delimiter);
                if (idx != -1 && idx < endIndex) {
                    endIndex = idx;
                }
            }

            String routeNumber = afterPrefix.substring(0, endIndex).trim();
            return routeNumber;

        } catch (Exception e) {
            Logger.log("⚠️ Error extracting route number: " + e.getMessage());
            return null;
        }
    }

    /**
     * Show buses that pass through the selected stop in the stella box
     * Shows ONLY route names (no duplicates, no times)
     */
    private void showStopBusesInBox(Stop stop) {
        try {
            Logger.log("📋 Populating stop buses box for: " + stop.getStopName());
            currentSelectedStop = stop;
            stopBusesListView.getItems().clear();

            // Get all routes that serve this stop
            List<Route> routes = getRoutesForStop(stop);

            if (routes.isEmpty()) {
                stopBusesListView.getItems().add(LanguageManager.getInstance().getString("home.noRoutesForStop"));
                return;
            }

            // Show ONLY route names (no duplicates, no times)
            for (Route route : routes) {
                String displayText = LanguageManager.getInstance().getString("home.routePrefix") + " " + route.getRouteShortName();
                stopBusesListView.getItems().add(displayText);
            }

            Logger.log("✅ Stop buses box populated with " + routes.size() + " routes");

        } catch (Exception e) {
            Logger.log("❌ Error showing stop buses in box: " + e.getMessage());
            e.printStackTrace();
            stopBusesListView.getItems().add("❌ Errore nel caricamento dei dati");
        }
    }

    /**
     * Get all routes that serve a specific stop - OPTIMIZED
     */
    private List<Route> getRoutesForStop(Stop stop) {
        List<Route> routes = new ArrayList<>();
        Set<String> routeIds = new HashSet<>();

        try {
            Logger.log("🔍 Getting routes for stop: " + stop.getStopName() + " (" + stop.getStopId() + ")");

            // Get all stop times for this stop (EFFICIENT!)
            List<com.rometransit.model.entity.StopTime> stopTimes = gtfsDataManager.getStopTimesForStop(stop.getStopId());
            Logger.log("📊 Found " + stopTimes.size() + " stop times");

            // Extract unique route IDs from trips
            for (com.rometransit.model.entity.StopTime stopTime : stopTimes) {
                Trip trip = gtfsDataManager.getTripById(stopTime.getTripId());
                if (trip != null) {
                    routeIds.add(trip.getRouteId());
                }
            }

            Logger.log("📍 Found " + routeIds.size() + " unique routes");

            // Get route objects
            for (String routeId : routeIds) {
                Route route = gtfsDataManager.getRouteById(routeId);
                if (route != null) {
                    routes.add(route);
                    Logger.log("  ✅ Route: " + route.getRouteShortName() + " - " + route.getRouteLongName());
                }
            }

            // Sort routes by short name
            routes.sort((a, b) -> {
                try {
                    // Try to parse as numbers for proper sorting
                    int numA = Integer.parseInt(a.getRouteShortName().replaceAll("[^0-9]", ""));
                    int numB = Integer.parseInt(b.getRouteShortName().replaceAll("[^0-9]", ""));
                    return Integer.compare(numA, numB);
                } catch (Exception e) {
                    return a.getRouteShortName().compareTo(b.getRouteShortName());
                }
            });

        } catch (Exception e) {
            Logger.log("❌ Error getting routes for stop: " + e.getMessage());
            e.printStackTrace();
        }

        return routes;
    }

    /**
     * Format bus arrival for display
     */
    private String formatBusArrival(com.rometransit.model.dto.ArrivalPrediction arrival) {
        String routeName = arrival.getRoute().getRouteShortName();
        String headsign = arrival.getTripHeadsign();
        if (headsign == null || headsign.isEmpty()) {
            headsign = arrival.getRoute().getRouteLongName();
        }

        String arrivalTime = arrival.getPredictedArrival().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        );

        long minutesUntil = java.time.Duration.between(
            java.time.LocalDateTime.now(),
            arrival.getPredictedArrival()
        ).toMinutes();

        return String.format("🚌 %s %s → %s | %s: %d min (%s)",
            LanguageManager.getInstance().getString("home.routePrefix"),
            routeName, headsign,
            LanguageManager.getInstance().getString("home.arrivalLabel"),
            minutesUntil, arrivalTime);
    }

    /**
     * Sort bus list by arrival time (earliest first)
     */
    private void sortBusListByArrivalTime() {
        try {
            String arrivalLabel = LanguageManager.getInstance().getString("home.arrivalLabel");
            List<String> items = new ArrayList<>(stopBusesListView.getItems());
            items.sort((a, b) -> {
                try {
                    // Extract minutes from "Arrival/Arrivo: X min"
                    int minsA = Integer.parseInt(
                        a.substring(a.indexOf(arrivalLabel + ":") + arrivalLabel.length() + 2).split(" ")[0]
                    );
                    int minsB = Integer.parseInt(
                        b.substring(b.indexOf(arrivalLabel + ":") + arrivalLabel.length() + 2).split(" ")[0]
                    );
                    return Integer.compare(minsA, minsB);
                } catch (Exception e) {
                    return 0;
                }
            });
            stopBusesListView.getItems().setAll(items);
        } catch (Exception e) {
            Logger.log("⚠️ Error sorting bus list: " + e.getMessage());
        }
    }

    /**
     * Handle double click on bus line in stella box
     * Supports multiple formats:
     * - "🚌 Linea 64 → Termini | Arrivo: 5 min (15:30)"
     * - "🚌 Linea 64 → Termini | Orario: 15:30"
     * - "🚌 Linea 64 - Nome linea"
     */
    private void handleBusLineDoubleClick(String selectedItem) {
        try {
            Logger.log("🖱️ Double click on: " + selectedItem);

            // Get the language-specific route prefix
            String routePrefix = LanguageManager.getInstance().getString("home.routePrefix");

            if (selectedItem == null || !selectedItem.contains(routePrefix)) {
                Logger.log("⚠️ Invalid selection format - expected prefix: " + routePrefix);
                return;
            }

            // Extract route number from string
            String routeNumber = null;
            try {
                // Find route prefix and extract the number/name after it
                int prefixIndex = selectedItem.indexOf(routePrefix);
                String afterPrefix = selectedItem.substring(prefixIndex + routePrefix.length()).trim();

                // Extract until next space, arrow, or dash
                int endIndex = afterPrefix.length();
                for (String delimiter : new String[]{" ", "→", "-", "|"}) {
                    int idx = afterPrefix.indexOf(delimiter);
                    if (idx != -1 && idx < endIndex) {
                        endIndex = idx;
                    }
                }

                routeNumber = afterPrefix.substring(0, endIndex).trim();
                Logger.log("🔍 Extracted route number: " + routeNumber);

            } catch (Exception e) {
                Logger.log("⚠️ Error extracting route number: " + e.getMessage());
                return;
            }

            if (routeNumber == null || routeNumber.isEmpty()) {
                Logger.log("⚠️ Could not extract route number");
                return;
            }

            // Find route
            List<Route> routes = gtfsDataManager.searchRoutes(routeNumber);
            if (!routes.isEmpty()) {
                Route route = routes.get(0);
                Logger.log("✅ Found route: " + route.getRouteShortName() + " - " + route.getRouteLongName());

                // Show route on map
                showRouteWithDirection(route);

                // Show route details in box punto esclamativo
                showLineDetailsInBox(route);
            } else {
                Logger.log("⚠️ No route found for: " + routeNumber);
            }
        } catch (Exception e) {
            Logger.log("❌ Error handling double click: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Show line details in the punto esclamativo box
     */
    private void showLineDetailsInBox(Route route) {
        try {
            Logger.log("📄 Showing line details for: " + route.getRouteShortName());
            currentSelectedRoute = route;

            StringBuilder details = new StringBuilder();
            details.append("🚌 ").append(LanguageManager.getInstance().getString("home.routeLabel").toUpperCase())
                   .append(" ").append(route.getRouteShortName())
                   .append(" - ").append(route.getRouteLongName()).append("\n\n");

            // Get real-time vehicle positions
            details.append("🚍 ").append(LanguageManager.getInstance().getString("home.busPositionLabel")).append(":\n");

            List<VehiclePosition> vehicles = vehicleTrackingService.getVehiclesForRoute(route.getRouteId());

            if (vehicles.isEmpty()) {
                details.append("   ").append(LanguageManager.getInstance().getString("home.noBusesDetected")).append("\n");
            } else {
                // Get all stops for this route
                List<Stop> routeStops = gtfsDataManager.getStopsForRoute(route.getRouteId());

                int vehicleCount = 0;
                for (VehiclePosition vehicle : vehicles) {
                    if (vehicleCount >= 3) break; // Show max 3 vehicles

                    // Find closest stop to this vehicle
                    Stop closestStop = findClosestStop(vehicle, routeStops);

                    if (closestStop != null) {
                        // Calculate distance to closest stop
                        double distanceKm = com.rometransit.util.math.GeoUtils.calculateDistance(
                            vehicle.getLatitude(), vehicle.getLongitude(),
                            closestStop.getStopLat(), closestStop.getStopLon()
                        );

                        // Estimate time to reach stop (assume average speed of 20 km/h if not available)
                        double speed = vehicle.getSpeed() > 0 ? vehicle.getSpeed() : 20.0;
                        int estimatedMinutes = (int) Math.ceil((distanceKm / speed) * 60);

                        details.append("   🚌 ").append(LanguageManager.getInstance().getString("home.busLabel"))
                               .append(" ").append(vehicle.getVehicleId(), 0, Math.min(4, vehicle.getVehicleId().length()));
                        details.append("\n");
                        details.append("      📍 ").append(LanguageManager.getInstance().getString("home.nearestStopLabel"))
                               .append(": ").append(closestStop.getStopName()).append("\n");
                        details.append("      ⏱️  ").append(LanguageManager.getInstance().getString("home.estimatedTimeLabel"))
                               .append(": ");

                        if (distanceKm < 0.05) { // Less than 50 meters
                            details.append(LanguageManager.getInstance().getString("home.atStopLabel")).append("\n");
                        } else if (estimatedMinutes < 1) {
                            details.append("< 1 ").append(LanguageManager.getInstance().getString("home.minLabel")).append("\n");
                        } else {
                            details.append(estimatedMinutes).append(" ").append(LanguageManager.getInstance().getString("home.minLabel")).append("\n");
                        }

                        details.append("      📏 ").append(LanguageManager.getInstance().getString("home.distanceLabel"))
                               .append(": ").append(String.format("%.2f km", distanceKm)).append("\n");

                        vehicleCount++;
                    }
                }

                if (vehicleCount == 0) {
                    details.append("   ").append(LanguageManager.getInstance().getString("home.noBusesNearStops")).append("\n");
                }
            }

            details.append("\n");

            // Get stops count
            List<Stop> stops = gtfsDataManager.getStopsForRoute(route.getRouteId());
            details.append("🚏 ").append(LanguageManager.getInstance().getString("home.totalStopsLabel"))
                   .append(": ").append(stops.size()).append("\n\n");

            // Route type
            details.append("📊 ").append(LanguageManager.getInstance().getString("home.typeLabel").toUpperCase())
                   .append(": ").append(getRouteTypeName(route.getRouteType())).append("\n\n");

            // Additional info
            details.append("ℹ️ INFO:\n");
            details.append("   Route ID: ").append(route.getRouteId()).append("\n");
            if (route.getAgencyId() != null) {
                details.append("   Agency: ").append(route.getAgencyId()).append("\n");
            }
            if (route.getRouteDesc() != null && !route.getRouteDesc().isEmpty()) {
                details.append("\n📝 DESCRIZIONE:\n   ").append(route.getRouteDesc()).append("\n");
            }

            lineDetailsTextArea.setText(details.toString());

            // Show favorite button when route is displayed
            if (addToFavoritesButton != null) {
                addToFavoritesButton.setVisible(true);
            }

            Logger.log("✅ Line details displayed");
        } catch (Exception e) {
            Logger.log("❌ Error showing line details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Show stop info + first 4 buses in the punto esclamativo box
     */
    private void showStopDetailsInBox(Stop stop) {
        try {
            Logger.log("📄 Showing stop details for: " + stop.getStopName());

            StringBuilder details = new StringBuilder();
            details.append("🚏 ").append(LanguageManager.getInstance().getString("home.stopLabel").toUpperCase())
                   .append(": ").append(stop.getStopName()).append("\n\n");

            // Stop information
            details.append("📍 ").append(LanguageManager.getInstance().getString("home.informationLabel").toUpperCase()).append(":\n");
            details.append("   Stop ID: ").append(stop.getStopId()).append("\n");
            if (stop.getStopCode() != null) {
                details.append("   ").append(LanguageManager.getInstance().getString("home.codeLabel"))
                       .append(": ").append(stop.getStopCode()).append("\n");
            }
            details.append("   ").append(LanguageManager.getInstance().getString("home.latitudeLabel"))
                   .append(": ").append(String.format("%.6f", stop.getStopLat())).append("\n");
            details.append("   ").append(LanguageManager.getInstance().getString("home.longitudeLabel"))
                   .append(": ").append(String.format("%.6f", stop.getStopLon())).append("\n");

            if (stop.getStopDesc() != null && !stop.getStopDesc().isEmpty()) {
                details.append("   ").append(LanguageManager.getInstance().getString("home.descriptionLabel"))
                       .append(": ").append(stop.getStopDesc()).append("\n");
            }

            details.append("\n🕐 ").append(LanguageManager.getInstance().getString("home.upcomingArrivalsLabel").toUpperCase()).append(":\n");

            // Get upcoming arrivals (first 4)
            List<com.rometransit.model.entity.StopTime> stopTimes = gtfsDataManager.getStopTimesForStop(stop.getStopId());

            // Get current time
            java.time.LocalTime now = java.time.LocalTime.now();
            String currentTimeStr = String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());

            // Filter upcoming times and limit to 4
            List<com.rometransit.model.entity.StopTime> upcomingTimes = stopTimes.stream()
                .filter(st -> st.getArrivalTime() != null)
                .filter(st -> st.getArrivalTime().compareTo(currentTimeStr) > 0)
                .sorted((a, b) -> a.getArrivalTime().compareTo(b.getArrivalTime()))
                .limit(4)
                .collect(java.util.stream.Collectors.toList());

            if (upcomingTimes.isEmpty()) {
                details.append("   ").append(LanguageManager.getInstance().getString("home.noUpcomingArrivals")).append("\n");
            } else {
                int count = 1;
                for (com.rometransit.model.entity.StopTime stopTime : upcomingTimes) {
                    Trip trip = gtfsDataManager.getTripById(stopTime.getTripId());
                    if (trip != null) {
                        Route route = gtfsDataManager.getRouteById(trip.getRouteId());
                        if (route != null) {
                            String headsign = trip.getTripHeadsign() != null ? trip.getTripHeadsign() : "N/A";
                            String arrivalTime = stopTime.getArrivalTime().substring(0, 5); // HH:MM
                            details.append("   ").append(count).append(". 🚌 ")
                                   .append(LanguageManager.getInstance().getString("home.routePrefix"))
                                   .append(" ").append(route.getRouteShortName())
                                   .append(" → ").append(headsign)
                                   .append(" | ").append(arrivalTime).append("\n");
                            count++;
                        }
                    }
                }
            }

            lineDetailsTextArea.setText(details.toString());

            // Hide favorite button when showing stop details
            if (addToFavoritesButton != null) {
                addToFavoritesButton.setVisible(false);
            }

            Logger.log("✅ Stop details displayed");
        } catch (Exception e) {
            Logger.log("❌ Error showing stop details: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Find the closest stop to a vehicle position
     */
    private Stop findClosestStop(VehiclePosition vehicle, List<Stop> stops) {
        if (vehicle == null || stops == null || stops.isEmpty()) {
            return null;
        }

        Stop closestStop = null;
        double minDistance = Double.MAX_VALUE;

        for (Stop stop : stops) {
            double distance = com.rometransit.util.math.GeoUtils.calculateDistance(
                vehicle.getLatitude(), vehicle.getLongitude(),
                stop.getStopLat(), stop.getStopLon()
            );

            if (distance < minDistance) {
                minDistance = distance;
                closestStop = stop;
            }
        }

        return closestStop;
    }

    /**
     * Create shape points from stops when GTFS shapes are corrupted
     * This creates a simplified path connecting all stops in order
     */
    private List<Shape> createShapePointsFromStops(List<Stop> stops, int direction) {
        List<Shape> shapePoints = new ArrayList<>();

        if (stops.isEmpty()) {
            return shapePoints;
        }

        // Create a shape point for each stop
        int sequence = 0;
        for (Stop stop : stops) {
            Shape shapePoint = new Shape();
            shapePoint.setShapeId("stops_generated_" + direction);
            shapePoint.setShapePtLat(stop.getStopLat());
            shapePoint.setShapePtLon(stop.getStopLon());
            shapePoint.setShapePtSequence(sequence++);
            shapePoint.setShapeDistTraveled(0.0); // We don't have this data

            shapePoints.add(shapePoint);
        }

        Logger.log("📝 Generated " + shapePoints.size() + " shape points from stops");
        return shapePoints;
    }

    /**
     * Get human-readable route type name
     */
    private String getRouteTypeName(com.rometransit.model.enums.TransportType routeType) {
        if (routeType == null) {
            return "Sconosciuto";
        }

        // Use the built-in displayName from TransportType enum
        return routeType.getDisplayName();
    }

    public void show() {
        // Setup connection monitoring first
        setupConnectionMonitoring();

        // Create WebView for HTML rendering
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        // Load HTML background with language support
        loadHTMLBackground();

        // Setup language change listener
        setupLanguageListener();

        // Create map view for the large rectangle on the right
        mapView = new NativeMapView(829, 704);
        mapView.centerOnRome();

        // Position map in the same coordinates as Rectangle50 in HTML (line 70)
        // Rectangle50: left: 513px, top: 237px
        AnchorPane.setLeftAnchor(mapView, 513.0);
        AnchorPane.setTopAnchor(mapView, 237.0);

        // Create search field (Rectangle6 in HTML: left: 23px, top: 148px, width: 391px, height: 56px)
        searchField = new TextField();
        searchField.setPrefSize(330, 40);
        searchField.setPromptText(LanguageManager.getInstance().getString("home.searchPlaceholder"));
        searchField.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 10px; " +
            "-fx-background-radius: 10px; " +
            "-fx-padding: 5px 5px 5px 35px; " +
            "-fx-font-size: 14px;"
        );

        // Search results list (appears below search field)
        searchResultsList = new ListView<>();
        searchResultsList.setPrefSize(360, 0); // Initially hidden
        searchResultsList.setVisible(false);
        searchResultsList.setStyle(
            "-fx-background-color: white; " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px;"
        );

        // Handle search input
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                searchResultsList.setVisible(false);
                searchResultsList.setPrefHeight(0);
                return;
            }

            // Check if GTFS data is loaded
            if (!gtfsDataManager.isStaticDataLoaded()) {
                searchResultsList.getItems().clear();
                searchResultsList.getItems().add("⚠️ " + LanguageManager.getInstance().getString("home.dataNotLoaded"));
                searchResultsList.setVisible(true);
                searchResultsList.setPrefHeight(30);
                Logger.log("⚠️ Search attempted but GTFS data not loaded yet");
                return;
            }

            // Search for stops and routes
            List<Stop> stops = gtfsDataManager.searchStops(newVal);
            List<Route> routes = gtfsDataManager.searchRoutes(newVal);

            searchResultsList.getItems().clear();

            // Add stops to results
            for (Stop stop : stops.subList(0, Math.min(5, stops.size()))) {
                searchResultsList.getItems().add("🚏 " + stop.getStopName());
            }

            // Add routes to results
            for (Route route : routes.subList(0, Math.min(5, routes.size()))) {
                searchResultsList.getItems().add("🚌 " + route.getRouteShortName() + " - " + route.getRouteLongName());
            }

            if (!searchResultsList.getItems().isEmpty()) {
                searchResultsList.setVisible(true);
                searchResultsList.setPrefHeight(Math.min(200, searchResultsList.getItems().size() * 30));
            } else {
                // No results found
                searchResultsList.getItems().add("❌ " + LanguageManager.getInstance().getString("home.noResults"));
                searchResultsList.setVisible(true);
                searchResultsList.setPrefHeight(30);
            }
        });

        // Create the two boxes
        createStopBusesBox();
        createLineDetailsBox();
        createAddToFavoritesButton();

        // Handle result selection
        searchResultsList.setOnMouseClicked(event -> {
            String selected = searchResultsList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    if (selected.startsWith("🚏")) {
                        // It's a stop
                        String stopName = selected.substring(2).trim();
                        List<Stop> stops = gtfsDataManager.searchStops(stopName);
                        if (!stops.isEmpty()) {
                            Stop stop = stops.get(0);
                            addStop(stop);
                            showStopDetails(stop);

                            // Populate box stella with buses (only names)
                            showStopBusesInBox(stop);

                            // Populate box punto esclamativo with stop info + first 4 buses
                            showStopDetailsInBox(stop);
                        }
                    } else if (selected.startsWith("🚌")) {
                        // It's a route
                        String routeInfo = selected.substring(2).trim();
                        String routeShortName = routeInfo.split(" - ")[0].trim();
                        List<Route> routes = gtfsDataManager.searchRoutes(routeShortName);
                        if (!routes.isEmpty()) {
                            Route route = routes.get(0);
                            showRouteWithDirection(route);

                            // NEW: Populate box punto esclamativo with route details
                            showLineDetailsInBox(route);
                        }
                    }
                    searchResultsList.setVisible(false);
                    searchField.clear();
                } catch (Exception ex) {
                    Logger.log("Error handling search result selection: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });

        // Account Button (User icon)
        Button accountButton = new Button();
        accountButton.setPrefSize(25, 30);
        accountButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 12px;");
        accountButton.setOnAction(e -> {
            try {
                // Check if user is logged in
                if (authService.getCurrentUser() != null) {
                    // User is logged in - show Account Settings
                    Logger.log("Opening Account Settings for user: " + authService.getCurrentUser().getUsername());
                    com.rometransit.ui.frontend.settings.AccountSettingsView accountView =
                        new com.rometransit.ui.frontend.settings.AccountSettingsView(stage, authService);
                    accountView.show();
                } else {
                    // User is NOT logged in - show Register/Login
                    Logger.log("User not logged in, opening Register view");

                    // Define what happens after successful login
                    Runnable onLoginSuccess = () -> {
                        try {
                            Logger.log("Login successful, returning to Home view");
                            this.show();
                        } catch (Exception ex) {
                            Logger.log("Error returning to Home after login: " + ex.getMessage());
                        }
                    };

                    RegisterView registerView = new RegisterView(stage, authService, onLoginSuccess);
                    registerView.show();
                }
            } catch (Exception ex) {
                Logger.log("Error opening Account: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Dashboard Button (Analytics icon)
        Button dashboardButton = new Button();
        dashboardButton.setPrefSize(25, 30);
        dashboardButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 12px;");
        dashboardButton.setOnAction(e -> {
            try {
                DashboardView dashboardView = new DashboardView(stage);
                dashboardView.show();
            } catch (Exception ex) {
                Logger.log("Error opening Dashboard: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Settings Button (Settings icon)
        Button settingsButton = new Button();
        settingsButton.setPrefSize(25, 30);
        settingsButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 12px;");
        settingsButton.setOnAction(e -> {
            try {
                SettingsView settingsView = new SettingsView(stage);
                settingsView.show();
            } catch (Exception ex) {
                Logger.log("Error opening Settings: " + ex.getMessage());
                ex.printStackTrace();
            }
        });


        statusBar = createStatusBar();

        // Layout
        AnchorPane root = new AnchorPane();

        // Add components with null checks
        if (webView != null) {
            root.getChildren().add(webView);
        } else {
            Logger.log("⚠️ webView is null, skipping");
        }

        if (mapView != null) {
            root.getChildren().add(mapView);
        } else {
            Logger.log("⚠️ mapView is null, skipping");
        }

        if (statusBar != null) {
            root.getChildren().add(statusBar);
        } else {
            Logger.log("⚠️ statusBar is null, skipping");
        }

        if (searchField != null) {
            root.getChildren().add(searchField);
        } else {
            Logger.log("⚠️ searchField is null, skipping");
        }

        if (searchResultsList != null) {
            root.getChildren().add(searchResultsList);
        } else {
            Logger.log("⚠️ searchResultsList is null, skipping");
        }

        if (accountButton != null) {
            root.getChildren().add(accountButton);
        } else {
            Logger.log("⚠️ accountButton is null, skipping");
        }

        if (dashboardButton != null) {
            root.getChildren().add(dashboardButton);
        } else {
            Logger.log("⚠️ dashboardButton is null, skipping");
        }

        if (settingsButton != null) {
            root.getChildren().add(settingsButton);
        } else {
            Logger.log("⚠️ settingsButton is null, skipping");
        }

        // Add the two boxes
        if (stopBusesListView != null) {
            root.getChildren().add(stopBusesListView);
        } else {
            Logger.log("⚠️ stopBusesListView is null, skipping");
        }

        if (lineDetailsTextArea != null) {
            root.getChildren().add(lineDetailsTextArea);
        } else {
            Logger.log("⚠️ lineDetailsTextArea is null, skipping");
        }

        if (addToFavoritesButton != null) {
            root.getChildren().add(addToFavoritesButton);
        } else {
            Logger.log("⚠️ addToFavoritesButton is null, skipping");
        }

        // Position search field (Rectangle6 in HTML: left: 23px, top: 148px)
        AnchorPane.setLeftAnchor(searchField, 54.0);
        AnchorPane.setTopAnchor(searchField, 159.0);

        // Position search results below search field
        AnchorPane.setLeftAnchor(searchResultsList, 38.0);
        AnchorPane.setTopAnchor(searchResultsList, 205.0);

        // Position stop buses box (Box Stella ⭐ - Rectangle55: left: 41px, top: 261px)
        AnchorPane.setLeftAnchor(stopBusesListView, 41.0);
        AnchorPane.setTopAnchor(stopBusesListView, 261.0);

        // Position line details box (Box Punto Esclamativo ❗ - Rectangle56: left: 39px, top: 654px)
        AnchorPane.setLeftAnchor(lineDetailsTextArea, 39.0);
        AnchorPane.setTopAnchor(lineDetailsTextArea, 654.0);

        // Position favorite button (stellina) in top-right corner of line details box
        AnchorPane.setLeftAnchor(addToFavoritesButton, 355.0);  // 39 + 359 - 35 - 8 padding
        AnchorPane.setTopAnchor(addToFavoritesButton, 659.0);   // 654 + 5



        // Position status bar
        AnchorPane.setBottomAnchor(statusBar, 10.0);
        AnchorPane.setLeftAnchor(statusBar, 513.0);
        AnchorPane.setRightAnchor(statusBar, 20.0);

        // Position buttons - coordinates from original Home.java
        // User icon at (1320, 54)
        AnchorPane.setTopAnchor(accountButton, 60.0);
        AnchorPane.setRightAnchor(accountButton, 86.0);

        // Analytics icon at (1269, 54)
        AnchorPane.setTopAnchor(dashboardButton, 60.0);
        AnchorPane.setRightAnchor(dashboardButton, 140.0);

        // Settings icon at (1370, 54)
        AnchorPane.setTopAnchor(settingsButton, 60.0);
        AnchorPane.setRightAnchor(settingsButton, 39.0);

        // Set WebView dimensions
        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        // Configure window
        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("home.title"));
        stage.setScene(scene);
        stage.show();

        // Initialize notification popup manager with stage
        if (notificationPopupManager != null) {
            notificationPopupManager.initialize(stage);
            Logger.log("✅ NotificationPopupManager initialized with stage");
        }

        // Start real-time notification monitoring
        if (notificationMonitor != null) {
            notificationMonitor.startMonitoring();
            Logger.log("✅ RealtimeNotificationMonitor started");
        }
    }

    public NativeMapView getMapView() {
        return mapView;
    }



    public void addStop(com.rometransit.model.entity.Stop stop) {
        if (mapView != null) {
            // Add a stop marker to the map (BLUE DOT)
            StopMarkerWaypoint stopWaypoint = new StopMarkerWaypoint(stop);
            mapView.addWaypoint(stopWaypoint);
            mapView.setCenter(stop.getStopLat(), stop.getStopLon());
            mapView.setZoom(16);
            Logger.log("🔵 Stop marker added: " + stop.getStopName());
        }
    }

    public void clearMap() {
        try {
            Logger.log("🗑️ Clearing map...");

            // Stop vehicle tracking
            if (vehicleUpdater != null) {
                vehicleUpdater.stop();
                vehicleUpdater = null;
            }

            // Clear waypoint tracking
            activeRouteWaypoints.clear();
            activeStopWaypoints.clear();
            activeVehicleWaypoints.clear();

            // Clear map view
            if (mapView != null) {
                mapView.clearWaypoints();
                mapView.repaint();
            }

            Logger.log("✅ Map cleared");
        } catch (Exception e) {
            Logger.log("❌ Error clearing map: " + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            Logger.log("🔌 Shutting down HomeView services...");

            // Stop notification monitoring
            if (notificationMonitor != null) {
                notificationMonitor.stopMonitoring();
                Logger.log("✅ RealtimeNotificationMonitor stopped");
            }

            // Stop status updater
            if (statusUpdater != null) {
                statusUpdater.stop();
                statusUpdater = null;
            }

            // Stop vehicle tracking updater
            if (vehicleUpdater != null) {
                vehicleUpdater.stop();
                vehicleUpdater = null;
            }

            // Clear map and waypoints
            clearMap();

            // Shutdown map view
            if (mapView != null) {
                mapView.shutdown();
            }

            // Shutdown tile manager
            if (tileManager != null) {
                tileManager.shutdown();
            }

            // Stop network monitoring
            NetworkManager networkManager = NetworkManager.getInstance();
            if (networkManager != null) {
                networkManager.stopMonitoring();
            }

            Logger.log("✅ HomeView shutdown complete");
        } catch (Exception e) {
            Logger.log("❌ Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Public method to show a route from favorites
     * Displays the route on the map with direction selector and details
     */
    public void showRoute(Route route) {
        try {
            showRouteWithDirection(route);
            showLineDetailsInBox(route);
        } catch (Exception e) {
            Logger.log("❌ Error showing route: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Public method to show a stop from favorites
     * Displays the stop on the map with details and bus list
     */
    public void showStop(Stop stop) {
        try {
            addStop(stop);
            showStopDetails(stop);
            showStopBusesInBox(stop);
            showStopDetailsInBox(stop);
        } catch (Exception e) {
            Logger.log("❌ Error showing stop: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground() {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("home/home.html");
            URL htmlUrl = getClass().getResource(htmlPath);

            if (htmlUrl == null) {
                Logger.log("Could not find home.html at path: " + htmlPath);
                return;
            }

            Logger.log("Loading HTML from: " + htmlUrl);
            webEngine.load(htmlUrl.toExternalForm());

            // Apply accent color theme after HTML loads and log state
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                Logger.log("WebView state: " + newState);
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    String color = ThemeManager.getAccent();
                    webEngine.executeScript(
                        "document.querySelectorAll('.accent-panel')" +
                        ".forEach(div => div.style.background = '" + color + "');"
                    );
                } else if (newState.toString().equals("FAILED")) {
                    Logger.log("Failed to load HTML file");
                }
            });

        } catch (Exception e) {
            Logger.log("Error loading HTML file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Setup language change listener to reload HTML when language changes
     */
    private void setupLanguageListener() {
        languageManager.addLanguageChangeListener(() -> {
            Logger.log("Language changed, reloading HomeView HTML...");
            Platform.runLater(() -> {
                loadHTMLBackground();
            });
        });
    }
}
