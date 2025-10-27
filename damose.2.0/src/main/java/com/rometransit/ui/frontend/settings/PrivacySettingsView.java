package com.rometransit.ui.frontend.settings;

import com.rometransit.service.auth.AuthService;
import com.rometransit.service.user.PreferencesService;
import com.rometransit.ui.component.ToggleButton;
import com.rometransit.ui.frontend.settings.SettingsView;
import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.util.language.LanguageManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.concurrent.Worker;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * View for managing privacy settings
 * Features:
 * - Clear local data
 * - Toggle usage statistics
 * - Change password
 * - Delete account
 * - View privacy policy
 */
public class PrivacySettingsView {
    private Stage stage;
    private WebEngine webEngine;
    private String currentLocation;
    private PreferencesService preferencesService;
    private AuthService authService;
    private String currentUserId;
    private ToggleButton toggleUsageStats;
    private LanguageManager languageManager;

    public PrivacySettingsView(Stage stage) {
        this.stage = stage;
        this.preferencesService = new PreferencesService();
        this.authService = AuthService.getInstance();
        this.languageManager = LanguageManager.getInstance();
        this.currentUserId = authService.getCurrentUser() != null ?
                authService.getCurrentUser().getUserId() : "default_user";
    }

    public void show() {
        WebView webView = new WebView();
        webEngine = webView.getEngine();

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

        // Create Usage Statistics Toggle
        toggleUsageStats = new ToggleButton();
        boolean currentStats = preferencesService.isAllowUsageStatistics(currentUserId);
        toggleUsageStats.setOn(currentStats);
        toggleUsageStats.onProperty().addListener((obs, oldVal, newVal) -> {
            handleUsageStatistics(newVal);
        });

        // Create invisible buttons overlaying the HTML links for reliable click handling
        // Clear local data button (left: 316, top: 342, width: ~195, height: ~34)
        Button clearDataButton = createInvisibleButton(195, 34);
        clearDataButton.setOnAction(e -> handleClearData());

        // Privacy Policy button (left: 188, top: 674, width: ~231, height: ~47)
        Button privacyPolicyButton = createInvisibleButton(231, 47);
        privacyPolicyButton.setOnAction(e -> handlePrivacyPolicy());

        // Change password button (left: 316, top: 564, width: ~180, height: ~30)
        Button changePasswordButton = createInvisibleButton(180, 30);
        changePasswordButton.setOnAction(e -> handleChangePassword());

        // Delete account button (left: 316, top: 606, width: ~150, height: ~30)
        Button deleteAccountButton = createInvisibleButton(150, 30);
        deleteAccountButton.setOnAction(e -> handleDeleteAccount());

        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(webView, settingsButton, toggleUsageStats,
            clearDataButton, privacyPolicyButton, changePasswordButton, deleteAccountButton);

        // Position elements
        AnchorPane.setTopAnchor(settingsButton, 70.0);
        AnchorPane.setLeftAnchor(settingsButton, 28.0);

        // Position toggle at "Allow usage statistics" location (from HTML: left: 316+280=~600, top: 435)
        AnchorPane.setLeftAnchor(toggleUsageStats, 1180.0); // Same X as notifications toggles
        AnchorPane.setTopAnchor(toggleUsageStats, 435.0);

        // Position invisible buttons over HTML links
        AnchorPane.setLeftAnchor(clearDataButton, 316.0);
        AnchorPane.setTopAnchor(clearDataButton, 342.0);

        AnchorPane.setLeftAnchor(privacyPolicyButton, 188.0);
        AnchorPane.setTopAnchor(privacyPolicyButton, 674.0);

        AnchorPane.setLeftAnchor(changePasswordButton, 316.0);
        AnchorPane.setTopAnchor(changePasswordButton, 564.0);

        AnchorPane.setLeftAnchor(deleteAccountButton, 316.0);
        AnchorPane.setTopAnchor(deleteAccountButton, 606.0);

        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("privacy.windowTitle"));
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Setup link interception for custom damose:// URLs
     */
    private void setupLinkInterception() {
        // Track current location
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                currentLocation = webEngine.getLocation();
            }
        });

        // Intercept navigation to custom URLs
        webEngine.locationProperty().addListener((obs, oldLocation, newLocation) -> {
            if (newLocation != null && newLocation.startsWith("damose://action/")) {
                // Prevent actual navigation
                Platform.runLater(() -> {
                    webEngine.getLoadWorker().cancel();
                    if (currentLocation != null) {
                        webEngine.load(currentLocation);
                    }
                });

                // Extract and handle action
                String action = newLocation.replace("damose://action/", "");
                handleAction(action);
            }
        });
    }


    /**
     * Handle actions triggered by clicking links
     */
    private void handleAction(String action) {
        Platform.runLater(() -> {
            switch (action) {
                case "clear-data":
                    handleClearData();
                    break;
                case "privacy-policy":
                    handlePrivacyPolicy();
                    break;
                case "change-password":
                    handleChangePassword();
                    break;
                case "delete-account":
                    handleDeleteAccount();
                    break;
                default:
                    System.err.println("Unknown action: " + action);
            }
        });
    }

    /**
     * Handle "Clear local data" action
     * Deletes cached GTFS data, maps, and realtime data
     */
    private void handleClearData() {
        LanguageManager lm = LanguageManager.getInstance();
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        confirmDialog.setTitle(lm.getString("privacy.clearLocalDataDialog"));
        confirmDialog.setHeaderText(lm.getString("privacy.clearLocalDataConfirm"));
        confirmDialog.setContentText(lm.getString("privacy.clearLocalDataDetails"));

        ButtonType clearButton = new ButtonType(lm.getString("privacy.clearDataButton"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType(lm.getString("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        confirmDialog.getButtonTypes().setAll(clearButton, cancelButton);

        Optional<ButtonType> result = confirmDialog.showAndWait();
        if (result.isPresent() && result.get() == clearButton) {
            try {
                String damoseDir = System.getProperty("user.home") + "/.damose";
                boolean success = true;

                // Clear data directory
                success &= clearDirectory(damoseDir + "/data");

                // Clear map cache
                success &= clearDirectory(damoseDir + "/map_cache");

                // Clear realtime cache
                success &= clearDirectory(damoseDir + "/realtime_cache");

                // Clear tiles cache
                success &= clearDirectory(damoseDir + "/tiles");

                // Delete tiles.db if exists
                File tilesDb = new File(damoseDir + "/tiles.db");
                if (tilesDb.exists()) {
                    success &= tilesDb.delete();
                }

                if (success) {
                    showInfoAlert(lm.getString("privacy.success"), lm.getString("privacy.localDataCleared"),
                        lm.getString("privacy.localDataClearedDetails"));
                } else {
                    showWarningAlert(lm.getString("privacy.partialSuccess"), lm.getString("privacy.someDataNotCleared"),
                        lm.getString("privacy.someDataNotClearedDetails"));
                }
            } catch (Exception e) {
                showErrorAlert(lm.getString("privacy.error"), lm.getString("privacy.failedToClearData"), e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Helper method to clear a directory
     */
    private boolean clearDirectory(String dirPath) {
        try {
            Path path = Paths.get(dirPath);
            if (Files.exists(path) && Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(f -> !f.equals(path.toFile())) // Don't delete the directory itself
                        .forEach(File::delete);
                }
                return true;
            }
            return true; // Directory doesn't exist, consider it "cleared"
        } catch (Exception e) {
            System.err.println("Error clearing directory " + dirPath + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Handle "Privacy Policy" action
     * Opens the privacy policy page
     */
    private void handlePrivacyPolicy() {
        try {
            new PrivacyPolicyView(stage).show();
        } catch (Exception e) {
            LanguageManager lm = LanguageManager.getInstance();
            showErrorAlert(lm.getString("privacy.error"), lm.getString("privacy.failedToOpenPrivacyPolicy"), e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle "Allow usage statistics" toggle
     */
    private void handleUsageStatistics(boolean enabled) {
        try {
            preferencesService.setAllowUsageStatistics(currentUserId, enabled);
            System.out.println("Usage statistics " + (enabled ? "enabled" : "disabled"));

            // Optional: Show confirmation toast
            // (You can add a toast notification here if you have a toast component)
        } catch (Exception e) {
            LanguageManager lm = LanguageManager.getInstance();
            showErrorAlert(lm.getString("privacy.error"), lm.getString("privacy.failedToUpdateStats"), e.getMessage());
            // Revert toggle on error
            toggleUsageStats.setOn(!enabled);
        }
    }

    /**
     * Handle "Change password" action
     * Shows a dialog to change password
     */
    private void handleChangePassword() {
        LanguageManager lm = LanguageManager.getInstance();
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(stage);
        dialogStage.setTitle(lm.getString("privacy.changePasswordDialog"));
        dialogStage.setResizable(false);

        // Create form
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        Label headerLabel = new Label(lm.getString("privacy.changePasswordHeader"));
        headerLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        PasswordField currentPasswordField = new PasswordField();
        currentPasswordField.setPromptText(lm.getString("privacy.currentPassword"));

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText(lm.getString("privacy.newPassword"));

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText(lm.getString("privacy.confirmNewPassword"));

        Label messageLabel = new Label();
        messageLabel.setStyle("-fx-text-fill: red;");

        Button saveButton = new Button(lm.getString("privacy.changePasswordButton"));
        saveButton.setDefaultButton(true);
        saveButton.setStyle("-fx-background-color: #007DFF; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");

        Button cancelButton = new Button(lm.getString("common.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setStyle("-fx-font-size: 14px; -fx-padding: 8 20;");

        HBox buttonBox = new HBox(10, saveButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(15,
            headerLabel,
            new Label(lm.getString("privacy.currentPasswordLabel")), currentPasswordField,
            new Label(lm.getString("privacy.newPasswordLabel")), newPasswordField,
            new Label(lm.getString("privacy.confirmPasswordLabel")), confirmPasswordField,
            messageLabel,
            buttonBox
        );
        content.setPadding(new Insets(20));
        content.setMinWidth(400);

        saveButton.setOnAction(e -> {
            String current = currentPasswordField.getText();
            String newPass = newPasswordField.getText();
            String confirm = confirmPasswordField.getText();

            // Validate
            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                messageLabel.setText(lm.getString("privacy.allFieldsRequired"));
                return;
            }

            if (newPass.length() < 8) {
                messageLabel.setText(lm.getString("privacy.passwordTooShort"));
                return;
            }

            if (!newPass.equals(confirm)) {
                messageLabel.setText(lm.getString("privacy.passwordsDoNotMatch"));
                return;
            }

            // Attempt to change password
            try {
                boolean success = authService.changePassword(current, newPass);

                if (success) {
                    showInfoAlert(lm.getString("privacy.success"),
                        lm.getString("privacy.passwordChanged"),
                        lm.getString("privacy.passwordChangedMessage"));
                    dialogStage.close();
                } else {
                    messageLabel.setText(lm.getString("privacy.incorrectCurrentPassword"));
                }
            } catch (IllegalArgumentException iae) {
                messageLabel.setText(iae.getMessage());
            } catch (Exception ex) {
                showErrorAlert(lm.getString("privacy.error"),
                    lm.getString("privacy.passwordChangeError"),
                    ex.getMessage());
            }
        });

        cancelButton.setOnAction(e -> dialogStage.close());

        Scene dialogScene = new Scene(content);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();
    }

    /**
     * Handle "Delete account" action
     * Shows confirmation dialog and deletes all user data
     */
    private void handleDeleteAccount() {
        LanguageManager lm = LanguageManager.getInstance();
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(stage);
        dialogStage.setTitle(lm.getString("privacy.deleteAccountDialog"));
        dialogStage.setResizable(false);

        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setMinWidth(450);

        Label headerLabel = new Label(lm.getString("privacy.deleteAccountDialog"));
        headerLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #D32F2F;");

        Label warningLabel = new Label(lm.getString("privacy.deleteAccountWarning"));
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-font-size: 13px;");

        Label confirmLabel = new Label(lm.getString("privacy.typeDeleteConfirm"));
        confirmLabel.setStyle("-fx-font-weight: bold;");

        TextField confirmField = new TextField();
        confirmField.setPromptText(lm.getString("privacy.typeDelete"));

        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");

        Button deleteButton = new Button(lm.getString("privacy.deletePermanentlyButton"));
        deleteButton.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8 20;");

        Button cancelButton = new Button(lm.getString("common.cancel"));
        cancelButton.setCancelButton(true);
        cancelButton.setStyle("-fx-font-size: 14px; -fx-padding: 8 20;");

        HBox buttonBox = new HBox(10, deleteButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(
            headerLabel,
            warningLabel,
            confirmLabel,
            confirmField,
            errorLabel,
            buttonBox
        );

        deleteButton.setOnAction(e -> {
            String confirmation = confirmField.getText().trim();

            if (!confirmation.equals("DELETE")) {
                errorLabel.setText(lm.getString("privacy.mustTypeDelete"));
                return;
            }

            // Perform deletion
            try {
                String damoseDir = System.getProperty("user.home") + "/.damose";
                Path damoPath = Paths.get(damoseDir);

                if (Files.exists(damoPath)) {
                    // Delete entire .damose directory
                    try (Stream<Path> walk = Files.walk(damoPath)) {
                        walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    }
                }

                // Show final message
                Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                successAlert.setTitle(lm.getString("privacy.accountDeleted"));
                successAlert.setHeaderText(lm.getString("privacy.accountDeletedMessage"));
                successAlert.setContentText(lm.getString("privacy.accountDeletedDetails"));
                successAlert.showAndWait();

                // Close application
                Platform.exit();
                System.exit(0);

            } catch (Exception ex) {
                showErrorAlert(lm.getString("privacy.error"), lm.getString("privacy.failedToDeleteAccount"),
                    lm.getString("privacy.failedToDeleteAccountDetails") + " " + ex.getMessage());
                ex.printStackTrace();
            }

            dialogStage.close();
        });

        cancelButton.setOnAction(e -> dialogStage.close());

        Scene dialogScene = new Scene(content);
        dialogStage.setScene(dialogScene);
        dialogStage.showAndWait();
    }

    /**
     * Create an invisible button for overlaying on HTML links
     */
    private Button createInvisibleButton(double width, double height) {
        Button button = new Button();
        button.setPrefSize(width, height);
        button.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-cursor: hand;");
        button.setOpacity(0.0); // Completely invisible
        return button;
    }

    // Helper methods for alerts
    private void showInfoAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showWarningAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground(WebView webView) {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("settings/privacy/privacy.html");
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
