package com.rometransit.ui.notification;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Manager for displaying notification popups on the right side of the screen
 * Handles arrival reminders, delay alerts, and line deviation notifications
 */
public class NotificationPopupManager {

    private static NotificationPopupManager instance;

    private Stage primaryStage;
    private VBox popupContainer;
    private Queue<NotificationPopup> popupQueue;
    private static final int MAX_VISIBLE_POPUPS = 3;
    private static final double POPUP_WIDTH = 350;
    private static final double POPUP_MARGIN = 10;
    private int visiblePopupCount = 0;

    private NotificationPopupManager() {
        this.popupQueue = new LinkedList<>();
    }

    public static synchronized NotificationPopupManager getInstance() {
        if (instance == null) {
            instance = new NotificationPopupManager();
        }
        return instance;
    }

    /**
     * Initialize the popup manager with the primary stage
     */
    public void initialize(Stage primaryStage) {
        this.primaryStage = primaryStage;
        setupPopupContainer();
    }

    /**
     * Setup container for popups (right side of screen)
     */
    private void setupPopupContainer() {
        popupContainer = new VBox(POPUP_MARGIN);
        popupContainer.setAlignment(Pos.TOP_RIGHT);
        popupContainer.setPadding(new Insets(POPUP_MARGIN));
        popupContainer.setPickOnBounds(false);
        popupContainer.setMouseTransparent(false);
    }

    /**
     * Show an arrival reminder popup for a favorite stop
     */
    public void showArrivalReminder(String routeName, String stopName, int minutesAway) {
        String title = "🚌 Autobus in arrivo";
        String message = String.format("Linea %s sta arrivando a %s\nArrivo previsto: %d min",
                                      routeName, stopName, minutesAway);
        showPopup(title, message, NotificationType.ARRIVAL);
    }

    /**
     * Show a delay/cancellation alert
     */
    public void showDelayAlert(String routeName, String message, boolean isCancellation) {
        String title = isCancellation ? "❌ Corsa cancellata" : "⏱️ Ritardo";
        String fullMessage = String.format("Linea %s\n%s", routeName, message);
        showPopup(title, fullMessage, NotificationType.DELAY);
    }

    /**
     * Show a line deviation alert
     */
    public void showDeviationAlert(String routeName, String message) {
        String title = "🔀 Deviazione percorso";
        String fullMessage = String.format("Linea %s\n%s", routeName, message);
        showPopup(title, fullMessage, NotificationType.DEVIATION);
    }

    /**
     * Show a generic notification popup
     */
    public void showPopup(String title, String message, NotificationType type) {
        Platform.runLater(() -> {
            NotificationPopup popup = new NotificationPopup(title, message, type);

            if (visiblePopupCount < MAX_VISIBLE_POPUPS) {
                displayPopup(popup);
            } else {
                popupQueue.offer(popup);
            }
        });
    }

    /**
     * Display a popup with animation
     */
    private void displayPopup(NotificationPopup popup) {
        visiblePopupCount++;

        // Get screen bounds
        double screenWidth = Screen.getPrimary().getVisualBounds().getWidth();
        double screenHeight = Screen.getPrimary().getVisualBounds().getHeight();

        // Position popup on right side
        StackPane container = popup.getContainer();

        // Add to scene (you'll need to add this to your main scene)
        // For now, log that popup would be shown
        System.out.println("📬 NOTIFICATION: " + popup.getTitle());
        System.out.println("   " + popup.getMessage());

        // Animate entry
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), container);
        slideIn.setFromX(POPUP_WIDTH);
        slideIn.setToX(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), container);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        ParallelTransition entry = new ParallelTransition(slideIn, fadeIn);
        entry.play();

        // Auto-dismiss after 5 seconds
        PauseTransition autoDismiss = new PauseTransition(Duration.seconds(5));
        autoDismiss.setOnFinished(e -> dismissPopup(popup));
        autoDismiss.play();
    }

    /**
     * Dismiss a popup with animation
     */
    private void dismissPopup(NotificationPopup popup) {
        StackPane container = popup.getContainer();

        // Animate exit
        TranslateTransition slideOut = new TranslateTransition(Duration.millis(300), container);
        slideOut.setToX(POPUP_WIDTH);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), container);
        fadeOut.setToValue(0.0);

        ParallelTransition exit = new ParallelTransition(slideOut, fadeOut);
        exit.setOnFinished(e -> {
            visiblePopupCount--;

            // Show next popup from queue if available
            if (!popupQueue.isEmpty()) {
                NotificationPopup nextPopup = popupQueue.poll();
                displayPopup(nextPopup);
            }
        });
        exit.play();
    }

    /**
     * Notification types
     */
    public enum NotificationType {
        ARRIVAL("#27ae60"),    // Green
        DELAY("#e74c3c"),      // Red
        DEVIATION("#f39c12");  // Orange

        private final String color;

        NotificationType(String color) {
            this.color = color;
        }

        public String getColor() {
            return color;
        }
    }

    /**
     * Inner class representing a single notification popup
     */
    private static class NotificationPopup {
        private String title;
        private String message;
        private NotificationType type;
        private StackPane container;

        public NotificationPopup(String title, String message, NotificationType type) {
            this.title = title;
            this.message = message;
            this.type = type;
            createPopupUI();
        }

        private void createPopupUI() {
            // Main container
            container = new StackPane();
            container.setPrefWidth(POPUP_WIDTH);
            container.setMaxWidth(POPUP_WIDTH);

            // Background
            VBox background = new VBox(10);
            background.setPadding(new Insets(15));
            background.setStyle(String.format(
                "-fx-background-color: white; " +
                "-fx-background-radius: 10; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 15, 0, 0, 5);"
            ));

            // Accent bar (left side, colored based on type)
            Region accentBar = new Region();
            accentBar.setPrefWidth(5);
            accentBar.setPrefHeight(80);
            accentBar.setStyle(String.format(
                "-fx-background-color: %s; " +
                "-fx-background-radius: 10 0 0 10;",
                type.getColor()
            ));

            // Title label
            Label titleLabel = new Label(title);
            titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            titleLabel.setTextFill(Color.web("#2c3e50"));
            titleLabel.setWrapText(true);
            titleLabel.setMaxWidth(POPUP_WIDTH - 40);

            // Message label
            Label messageLabel = new Label(message);
            messageLabel.setFont(Font.font("System", 12));
            messageLabel.setTextFill(Color.web("#7f8c8d"));
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(POPUP_WIDTH - 40);

            // Add all to background
            background.getChildren().addAll(titleLabel, messageLabel);

            // Add to container
            HBox contentBox = new HBox(10);
            contentBox.getChildren().addAll(accentBar, background);

            container.getChildren().add(contentBox);
        }

        public String getTitle() {
            return title;
        }

        public String getMessage() {
            return message;
        }

        public StackPane getContainer() {
            return container;
        }
    }
}
