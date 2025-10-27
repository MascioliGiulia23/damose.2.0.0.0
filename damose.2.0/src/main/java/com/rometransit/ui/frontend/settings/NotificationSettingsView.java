package com.rometransit.ui.frontend.settings;

import com.rometransit.ui.frontend.settings.SettingsView;
import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.ui.component.ToggleButton;
import com.rometransit.service.notification.NotificationService;
import com.rometransit.service.notification.NotificationService.NotificationType;
import com.rometransit.service.auth.AuthService;
import com.rometransit.model.entity.NotificationPreferences;
import com.rometransit.model.entity.NotificationPreferences.NotificationFrequency;
import com.rometransit.util.language.LanguageManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * View for managing notification preferences
 * Features:
 * - Master toggle to enable/disable all notifications
 * - Individual toggles for different notification types
 * - Frequency selection with mutual exclusion
 * - Automatic persistence of preferences
 */
public class NotificationSettingsView {
    private Stage stage;
    private NotificationService notificationService;
    private AuthService authService;
    private LanguageManager languageManager;
    private String currentUserId;
    private WebEngine webEngine;

    // Toggle buttons
    private ToggleButton toggleEnableAll;
    private ToggleButton toggleArrivalReminders;
    private ToggleButton toggleDelayAlerts;
    private ToggleButton toggleLineDeviation;


    private List<ToggleButton> notificationToggles;
    private List<ToggleButton> frequencyToggles;

    public NotificationSettingsView(Stage stage) {
        this.stage = stage;
        this.notificationService = NotificationService.getInstance();
        this.authService = AuthService.getInstance();
        this.languageManager = LanguageManager.getInstance();
        this.currentUserId = authService.getCurrentUser() != null ?
                authService.getCurrentUser().getUserId() : "default_user";

        this.notificationToggles = new ArrayList<>();
        this.frequencyToggles = new ArrayList<>();
    }

    public void show() {
        WebView webView = new WebView();
        this.webEngine = webView.getEngine();

        // Load HTML with language-aware path
        loadHTMLBackground(webView);

        // Setup language change listener
        setupLanguageListener(webView);

        // Settings Back Button
        Button settingsButton = new Button();
        settingsButton.setPrefSize(35, 35);
        settingsButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 12px;");
        settingsButton.setOnAction(e -> {
            try {
                new SettingsView(stage).show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        // ========== CREATE ALL TOGGLE BUTTONS ==========

        // Master Toggle: Enable all notifications
        toggleEnableAll = new ToggleButton();
        setupEnableAllToggle();

        // Notification Type Toggles
        toggleArrivalReminders = new ToggleButton();
        setupArrivalRemindersToggle();
        notificationToggles.add(toggleArrivalReminders);

        toggleDelayAlerts = new ToggleButton();
        setupDelayAlertsToggle();
        notificationToggles.add(toggleDelayAlerts);

        toggleLineDeviation = new ToggleButton();
        setupLineDeviationToggle();
        notificationToggles.add(toggleLineDeviation);



        // Load saved preferences
        loadUserPreferences();

        // Add all components to root
        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(
            webView,
            settingsButton,
            toggleEnableAll,
            toggleArrivalReminders,
            toggleDelayAlerts,
            toggleLineDeviation);

        // Position settings button
        AnchorPane.setTopAnchor(settingsButton, 70.0);
        AnchorPane.setLeftAnchor(settingsButton, 28.0);

        // Position toggle buttons (based on HTML layout)
        AnchorPane.setTopAnchor(toggleEnableAll, 282.0);
        AnchorPane.setLeftAnchor(toggleEnableAll, 1180.0);

        AnchorPane.setTopAnchor(toggleArrivalReminders, 384.0);
        AnchorPane.setLeftAnchor(toggleArrivalReminders, 1180.0);

        AnchorPane.setTopAnchor(toggleDelayAlerts, 434.0);
        AnchorPane.setLeftAnchor(toggleDelayAlerts, 1180.0);

        AnchorPane.setTopAnchor(toggleLineDeviation, 484.0);
        AnchorPane.setLeftAnchor(toggleLineDeviation, 1180.0);



        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle("Damose - Notifications Interface");
        stage.setScene(scene);
        stage.show();
    }

    // ========== TOGGLE SETUP METHODS ==========

    /**
     * Setup master toggle: Enable all notifications
     * Controls all other toggles and provides visual feedback
     * Sub-toggles remain independently clickable
     */
    private void setupEnableAllToggle() {
        toggleEnableAll.onProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("🎛️ Enable All toggled: " + newVal);

            // Enable/disable all notification types
            notificationService.setEnableAll(currentUserId, newVal);

            if (newVal) {
                // When enabling, turn ON all sub-toggles with animation
                animateEnableAll(true);
            } else {
                // When disabling, turn OFF all sub-toggles but keep them clickable
                turnOffAllToggles();
            }

            savePreferences();
        });
    }

    /**
     * Setup arrival reminders toggle
     */
    private void setupArrivalRemindersToggle() {
        toggleArrivalReminders.onProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("📍 Arrival Reminders toggled: " + newVal);
            notificationService.setNotificationType(currentUserId,
                    NotificationType.ARRIVAL_REMINDERS, newVal);
            savePreferences();
        });
    }

    /**
     * Setup delay alerts toggle
     */
    private void setupDelayAlertsToggle() {
        toggleDelayAlerts.onProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("⚠️ Delay Alerts toggled: " + newVal);
            notificationService.setNotificationType(currentUserId,
                    NotificationType.DELAY_ALERTS, newVal);
            savePreferences();
        });
    }

    /**
     * Setup line deviation alerts toggle
     */
    private void setupLineDeviationToggle() {
        toggleLineDeviation.onProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("🔀 Line Deviation toggled: " + newVal);
            notificationService.setNotificationType(currentUserId,
                    NotificationType.LINE_DEVIATION_ALERTS, newVal);
            savePreferences();
        });
    }

    /**
     * Setup real-time frequency toggle
     * Part of mutual exclusion group with daily and weekly
     */

    /**
     * Setup daily summary frequency toggle
     * Part of mutual exclusion group with real-time and weekly
     */


    /**
     * Setup weekly summary frequency toggle
     * Part of mutual exclusion group with real-time and daily
     */


    // ========== STATE MANAGEMENT METHODS ==========

    /**
     * Load saved preferences from NotificationService
     * Called when view is opened
     */
    private void loadUserPreferences() {
        NotificationPreferences prefs = notificationService.getUserPreferences(currentUserId);

        // Set master toggle
        toggleEnableAll.setOn(prefs.isEnableAllNotifications());

        // Set notification type toggles - these remain independently clickable
        toggleArrivalReminders.setOn(prefs.isArrivalReminders());
        toggleDelayAlerts.setOn(prefs.isDelayAlerts());
        toggleLineDeviation.setOn(prefs.isLineDeviationAlerts());

        // Set frequency toggles (mutual exclusion)


        // Sub-toggles remain clickable regardless of master toggle state

        System.out.println("📥 Loaded preferences for user: " + currentUserId);
        System.out.println("   " + prefs.toString());
    }

    /**
     * Save current state to NotificationService
     * Called whenever a toggle changes
     */
    private void savePreferences() {
        System.out.println("💾 Saving notification preferences...");
    }

    /**
     * Turn off all sub-toggles when master toggle is OFF
     * Sub-toggles remain clickable for independent control
     */
    private void turnOffAllToggles() {
        // Turn OFF all notification toggles
        toggleArrivalReminders.setOn(false);
        toggleDelayAlerts.setOn(false);
        toggleLineDeviation.setOn(false);

        // Keep toggles fully interactive (no opacity change, no mouse blocking)
        // Users can still individually enable specific notifications

        System.out.println("⚪ All sub-toggles turned off (still clickable)");
    }

    /**
     * Disable all sub-toggles when master toggle is OFF
     * DEPRECATED: Use turnOffAllToggles() instead to keep toggles clickable
     */
    private void disableAllToggles() {
        // Just turn off, don't disable interaction
        turnOffAllToggles();
    }

    /**
     * Enable all sub-toggles with animation when master toggle is ON
     * Provides smooth visual feedback
     * Sub-toggles remain independently clickable
     */
    private void animateEnableAll(boolean enable) {
        if (enable) {
            // Toggles are always interactive, no need to re-enable
            // Just animate turning them ON

            // Animate turning ON each toggle with delay
            PauseTransition delay = new PauseTransition(Duration.millis(0));

            // First, enable all notification types
            for (int i = 0; i < notificationToggles.size(); i++) {
                final int index = i;
                PauseTransition toggleDelay = new PauseTransition(Duration.millis(100 * (i + 1)));
                toggleDelay.setOnFinished(e -> {
                    notificationToggles.get(index).setOn(true);
                });
                toggleDelay.play();
            }

            // Then, enable default frequency (Real-time)


            System.out.println("✅ All sub-toggles enabled with animation");
        }
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground(WebView webView) {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("settings/notifications/Notifications.html");
            URL htmlUrl = getClass().getResource(htmlPath);

            if (htmlUrl == null) {
                System.err.println("Could not find HTML at path: " + htmlPath);
                return;
            }

            System.out.println("Loading HTML from: " + htmlUrl);
            webEngine.load(htmlUrl.toExternalForm());

            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                    applyTheme();
                }
            });
        } catch (Exception e) {
            System.err.println("Error loading HTML file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply theme accent color to HTML
     */
    private void applyTheme() {
        try {
            String color = ThemeManager.getAccent();
            webEngine.executeScript(
                "document.querySelectorAll('.accent-panel')" +
                ".forEach(div => div.style.background = '" + color + "');"
            );
        } catch (Exception e) {
            System.err.println("Error applying theme: " + e.getMessage());
        }
    }

    /**
     * Setup language change listener to reload HTML when language changes
     */
    private void setupLanguageListener(WebView webView) {
        languageManager.addLanguageChangeListener(() -> {
            System.out.println("Language changed, reloading HTML...");
            Platform.runLater(() -> {
                loadHTMLBackground(webView);
            });
        });
    }
}

