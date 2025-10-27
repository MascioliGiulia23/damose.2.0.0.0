package com.rometransit.ui.frontend.dashboard;

import com.rometransit.model.dto.dashboard.VehicleCrowdingData;
import com.rometransit.ui.frontend.home.HomeView;
import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.service.dashboard.TransportQualityService;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.model.entity.TransportIncident;
import com.rometransit.service.dashboard.TransportQualityService.TransportMetrics;

import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.util.logging.Logger;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

/**
 * Dashboard View - HTML Background + JavaFX Controls
 * Uses HTML for visual panels and JavaFX for interactive controls
 */
public class DashboardView {

    private static final Logger logger = Logger.getLogger(DashboardView.class);
    private static final int REFRESH_INTERVAL_SECONDS = 30;

    private Stage stage;

    // Services
    private TransportQualityService qualityService;

    // WebView Components
    private WebView webView;
    private WebEngine webEngine;

    // Layout Components
    private StackPane rootPane;
    private AnchorPane controlsPane;
    private ListView<VehicleCrowdingData> crowdingListView;
    private ListView<TransportIncident> incidentListView;
    private TextField searchField;

    // Card Labels (Top Metrics)
    private Label activeBusesLabel;
    private Label inactiveBusesLabel;
    private Label totalIssuesLabel;
    private Label highSeverityLabel;

    // Data Properties for Binding
    private final IntegerProperty activeBuses = new SimpleIntegerProperty(0);
    private final IntegerProperty inactiveBuses = new SimpleIntegerProperty(0);
    private final IntegerProperty totalIssues = new SimpleIntegerProperty(0);
    private final IntegerProperty highSeverityIssues = new SimpleIntegerProperty(0);

    // Observable Lists
    private ObservableList<VehicleCrowdingData> crowdingData;
    private FilteredList<VehicleCrowdingData> filteredCrowdingData;
    private ObservableList<TransportIncident> incidentData;

    // Auto-refresh Timeline
    private Timeline refreshTimeline;

    public DashboardView(Stage stage) {
        this.stage = stage;
        initializeServices();
        initializeDataLists();
    }

    private void initializeServices() {
        try {
            logger.info("Initializing dashboard services...");
            GTFSDataManager gtfsDataManager = GTFSDataManager.getInstance();
            this.qualityService = new TransportQualityService(gtfsDataManager);

            logger.info("Dashboard services initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize dashboard services", e);
        }
    }

    private void initializeDataLists() {
        crowdingData = FXCollections.observableArrayList();
        filteredCrowdingData = new FilteredList<>(crowdingData, p -> true);
        incidentData = FXCollections.observableArrayList();
    }

    public void show() {
        // Root pane: StackPane con layers
        rootPane = new StackPane();
        rootPane.setPrefSize(1440, 1000);

        // Layer 1: WebView con HTML background
        initializeHTMLBackground();

        // Layer 2: AnchorPane per controlli JavaFX
        controlsPane = new AnchorPane();
        controlsPane.setStyle("-fx-background-color: transparent;");
        controlsPane.setPrefSize(1440, 1000);

        // Build JavaFX controls (overlayed on HTML)
        buildMetricLabels();
        buildHomeButton();
        buildBusCrowdingControls();
        buildBusAlertsControls();

        // Add layers to root (bottom to top)
        rootPane.getChildren().addAll(webView, controlsPane);

        // Setup bindings and refresh
        setupBindings();
        setupLanguageListener();
        setupThemeListener();
        refreshDashboardData();
        startAutoRefresh();

        // Scene and Stage
        Scene scene = new Scene(rootPane, 1440, 1000);

        stage.setTitle(LanguageManager.getInstance().getString("dashboard.title"));
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Initialize HTML background with WebView
     */
    private void initializeHTMLBackground() {
        webView = new WebView();
        webEngine = webView.getEngine();
        webView.setPrefSize(1440, 1000);

        // Load HTML based on current language
        String htmlPath = getHTMLPath();
        try {
            String htmlURL = getClass().getResource(htmlPath).toExternalForm();
            webEngine.load(htmlURL);

            // Apply theme when HTML is loaded
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    applyThemeToHTML();
                }
            });

            logger.info("HTML dashboard loaded from: " + htmlPath);
        } catch (Exception e) {
            logger.error("Failed to load dashboard HTML from: " + htmlPath, e);
        }
    }

    /**
     * Get HTML path based on current language
     */
    private String getHTMLPath() {
        return LanguageManager.getInstance().getHTMLResourcePath("dashboard/dashboard.html");
    }

    /**
     * Apply accent color theme to HTML via JavaScript
     */
    private void applyThemeToHTML() {
        String accentColor = ThemeManager.getAccent();
        String script = "document.querySelectorAll('.accent-panel').forEach(function(el) { " +
                "el.style.background = '" + accentColor + "'; " +
                "});";
        webEngine.executeScript(script);
        logger.debug("Applied theme accent color to HTML: " + accentColor);
    }

    /**
     * Setup listener for language changes
     */
    private void setupLanguageListener() {
        LanguageManager.getInstance().addLanguageChangeListener(() -> {
            Platform.runLater(() -> {
                String newLang = LanguageManager.getInstance().getCurrentLanguageCode();
                logger.info("Language changed to: " + newLang + ", reloading dashboard HTML");

                // Reload HTML for new language
                String htmlPath = getHTMLPath();
                try {
                    String htmlURL = getClass().getResource(htmlPath).toExternalForm();
                    webEngine.load(htmlURL);

                    // Update JavaFX control texts
                    stage.setTitle(LanguageManager.getInstance().getString("dashboard.title"));
                    searchField.setPromptText(LanguageManager.getInstance().getString("dashboard.searchPlaceholder"));

                    logger.info("Dashboard HTML reloaded for language: " + newLang);
                } catch (Exception e) {
                    logger.error("Failed to reload dashboard HTML for language: " + newLang, e);
                }
            });
        });
    }

    /**
     * Setup listener for theme changes
     */
    private void setupThemeListener() {
        ThemeManager.addThemeChangeListener(() -> {
            Platform.runLater(() -> {
                String newColor = ThemeManager.getAccent();
                logger.info("Theme changed to: " + newColor + ", applying to dashboard");

                // Apply new accent color to HTML panels
                if (webEngine.getLoadWorker().getState() == Worker.State.SUCCEEDED) {
                    applyThemeToHTML();
                }

                logger.debug("Theme applied to dashboard: " + newColor);
            });
        });
    }

    /**
     * Build metric value labels (overlayed on HTML cards)
     * HTML has the card panels, we just add the numeric values
     */
    private void buildMetricLabels() {
        // Card 1: Active Buses Label (big number)
        activeBusesLabel = new Label("0");
        activeBusesLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 72px; " +
                "-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
        AnchorPane.setLeftAnchor(activeBusesLabel, 200.0);
        AnchorPane.setTopAnchor(activeBusesLabel, 260.0);

        // Card 2: Inactive Buses Label (big number)
        inactiveBusesLabel = new Label("0");
        inactiveBusesLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 72px; " +
                "-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
        AnchorPane.setLeftAnchor(inactiveBusesLabel, 648.0);
        AnchorPane.setTopAnchor(inactiveBusesLabel, 260.0);

        // Card 3: Total Issues Label (big number)
        totalIssuesLabel = new Label("0");
        totalIssuesLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 72px; " +
                "-fx-font-weight: bold; -fx-text-fill: black; -fx-background-color: transparent;");
        AnchorPane.setLeftAnchor(totalIssuesLabel, 1096.0);
        AnchorPane.setTopAnchor(totalIssuesLabel, 245.0);

        // Card 3: High Severity Badge
        highSeverityLabel = new Label("0 HIGH");
        highSeverityLabel.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; " +
                "-fx-padding: 5px 15px; -fx-background-radius: 15px; -fx-font-weight: bold; " +
                "-fx-font-family: 'Poppins'; -fx-font-size: 14px;");
        AnchorPane.setLeftAnchor(highSeverityLabel, 1076.0);
        AnchorPane.setTopAnchor(highSeverityLabel, 330.0);

        controlsPane.getChildren().addAll(activeBusesLabel, inactiveBusesLabel,
                totalIssuesLabel, highSeverityLabel);
    }

    /**
     * Build transparent Home button overlayed on HTML home icon
     */
    private void buildHomeButton() {
        Button homeButton = new Button();
        homeButton.setPrefSize(40, 40);
        homeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        homeButton.setOnAction(e -> goHome());

        AnchorPane.setLeftAnchor(homeButton, 1265.0);
        AnchorPane.setTopAnchor(homeButton, 54.0);

        controlsPane.getChildren().add(homeButton);
    }


    /**
     * Build Bus Crowding controls (overlayed on HTML panel)
     * HTML has the background panel and title, we add TextField and ListView
     */
    private void buildBusCrowdingControls() {
        // Search bar - width: 335px, height: 42px, left: 107px, top: 477px
        searchField = new TextField();
        searchField.setPromptText(LanguageManager.getInstance().getString("dashboard.searchPlaceholder"));
        searchField.setPrefSize(335, 42);
        searchField.setStyle("-fx-background-color: white; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.25), 4, 0, 0, 4); " +
                "-fx-background-radius: 8px; " +
                "-fx-font-family: 'Poppins'; -fx-font-size: 14px; " +
                "-fx-padding: 0 0 0 35px;");

        AnchorPane.setLeftAnchor(searchField, 107.0);
        AnchorPane.setTopAnchor(searchField, 477.0);

        // Setup search filter listener
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredCrowdingData.setPredicate(crowding -> {
                if (newValue == null || newValue.isEmpty()) return true;
                String lowerCaseFilter = newValue.toLowerCase();
                return crowding.getRouteId().toLowerCase().contains(lowerCaseFilter) ||
                        crowding.getRouteName().toLowerCase().contains(lowerCaseFilter);
            });
        });

        // ListView - width: 699px, height: 406px, left: 107px, top: 541px
        crowdingListView = new ListView<>(filteredCrowdingData);
        crowdingListView.setCellFactory(param -> new CrowdingListCell());
        crowdingListView.setPrefSize(699, 406);
        crowdingListView.setStyle("-fx-background-color: white; -fx-background-radius: 20px;");

        AnchorPane.setLeftAnchor(crowdingListView, 107.0);
        AnchorPane.setTopAnchor(crowdingListView, 541.0);

        controlsPane.getChildren().addAll(searchField, crowdingListView);
    }

    /**
     * Build Bus Alerts controls (overlayed on HTML panel)
     * HTML has the background panel and title, we add ListView
     */
    private void buildBusAlertsControls() {
        // ListView - width: 339px, height: 477px, left: 972px, top: 470px
        incidentListView = new ListView<>(incidentData);
        incidentListView.setCellFactory(param -> new IncidentListCell());
        incidentListView.setPrefSize(339, 477);
        incidentListView.setStyle("-fx-background-color: white; -fx-background-radius: 19px;");

        AnchorPane.setLeftAnchor(incidentListView, 972.0);
        AnchorPane.setTopAnchor(incidentListView, 470.0);

        controlsPane.getChildren().add(incidentListView);
    }

    /**
     * Data Binding Setup
     */
    private void setupBindings() {
        // Bind top metrics
        activeBusesLabel.textProperty().bind(activeBuses.asString());
        inactiveBusesLabel.textProperty().bind(inactiveBuses.asString());
        totalIssuesLabel.textProperty().bind(totalIssues.asString());
        highSeverityLabel.textProperty().bind(Bindings.concat(highSeverityIssues.asString(), " HIGH"));
    }

    /**
     * Auto-Refresh Timeline
     */
    private void startAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
        }

        refreshTimeline = new Timeline(new KeyFrame(
                Duration.seconds(REFRESH_INTERVAL_SECONDS),
                event -> refreshDashboardData()
        ));
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();

        logger.info("Auto-refresh enabled (interval: " + REFRESH_INTERVAL_SECONDS + "s)");
    }

    private void stopAutoRefresh() {
        if (refreshTimeline != null) {
            refreshTimeline.stop();
            logger.info("Auto-refresh stopped");
        }
    }

    /**
     * Refresh all dashboard data from services
     */
    private void refreshDashboardData() {
        Platform.runLater(() -> {
            try {
                logger.info("Refreshing dashboard data...");

                // Get transport metrics
                TransportMetrics metrics = qualityService.getTransportMetrics();

                // Update properties (triggers UI update via binding)
                activeBuses.set((int) metrics.getActiveVehicles());
                inactiveBuses.set((int) metrics.getInactiveVehicles());
                totalIssues.set(metrics.getTotalIncidents());
                highSeverityIssues.set(metrics.getHighSeverityIncidents());

                // Update crowding data
                try {
                    List<VehicleCrowdingData> crowding = qualityService.getCrowdingData();
                    crowdingData.setAll(crowding);
                } catch (Exception e) {
                    logger.warn("Error loading crowding data: " + e.getMessage());
                }

                // Update incident data
                List<TransportIncident> incidents = qualityService.getCurrentIncidents();
                incidents.sort((a, b) -> b.getSeverity().compareTo(a.getSeverity()));
                incidentData.setAll(incidents);

                logger.info("Dashboard data refreshed successfully");
            } catch (Exception e) {
                logger.error("Error refreshing dashboard data", e);
            }
        });
    }

    private void goHome() {
        try {
            stopAutoRefresh();
            HomeView homeView = new HomeView(stage);
            homeView.show();
        } catch (Exception e) {
            logger.error("Error opening Home", e);
        }
    }

    public void cleanup() {
        stopAutoRefresh();
        logger.info("Dashboard view cleaned up");
    }
}
