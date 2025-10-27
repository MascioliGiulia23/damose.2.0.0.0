package com.rometransit.ui.frontend.settings;

import com.rometransit.ui.frontend.ViewBase;
import com.rometransit.ui.frontend.home.HomeView;
import javafx.scene.control.Button;
import javafx.stage.Stage;

/**
 * Settings main menu view - displays all settings categories as navigation buttons.
 * Built using ViewBase pattern for automatic language change and theme management.
 */
public class SettingsView extends ViewBase {

    // Navigation buttons
    private Button homeButton;
    private Button accountButton;
    private Button notificationsButton;
    private Button appearanceButton;
    private Button languageButton;
    private Button privacyButton;
    private Button aboutButton;

    public SettingsView(Stage stage) {
        super(stage, "SettingsView");
    }

    @Override
    protected String getHTMLPath() {
        return "settings/settings.html";
    }

    @Override
    protected void createControls() {
        // Home button (back to home)
        homeButton = new Button();
        homeButton.setPrefSize(35, 35);
        homeButton.setStyle(
            "-fx-background-color: rgba(0,0,0,0); " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-font-size: 12px;"
        );

        // Settings category buttons
        accountButton = createCategoryButton();
        notificationsButton = createCategoryButton();
        appearanceButton = createCategoryButton();
        languageButton = createCategoryButton();
        privacyButton = createCategoryButton();
        aboutButton = createCategoryButton();
    }

    /**
     * Helper method to create category navigation buttons with consistent styling
     */
    private Button createCategoryButton() {
        Button button = new Button();
        button.setPrefSize(35, 35);
        button.setStyle(
            "-fx-background-color: rgba(0,0,0,0); " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px; " +
            "-fx-font-size: 12px;"
        );
        return button;
    }

    @Override
    protected void positionControls() {
        // Home button on the left
        controlManager.addControl(homeButton, 28, 70, 35, 35);

        // Settings category buttons on the right
        controlManager.addControl(accountButton, 373, 210, 35, 35);
        controlManager.addControl(notificationsButton, 373, 330, 35, 35);
        controlManager.addControl(appearanceButton, 373, 460, 35, 35);
        controlManager.addControl(languageButton, 373, 587, 35, 35);
        controlManager.addControl(privacyButton, 373, 719, 35, 35);
        controlManager.addControl(aboutButton, 373, 845, 35, 35);
    }

    @Override
    protected void setupEventHandlers() {
        // Home button - navigate to HomeView
        homeButton.setOnAction(e -> {
            try {
                HomeView homeView = new HomeView(stage);
                homeView.show();
            } catch (Exception ex) {
                logger.error("Error opening Home", ex);
            }
        });

        // Account button - navigate to AccountSettingsView
        accountButton.setOnAction(e -> {
            try {
                new AccountSettingsView(stage, com.rometransit.service.auth.AuthService.getInstance()).show();
            } catch (Exception ex) {
                logger.error("Error opening Account settings", ex);
            }
        });

        // Notifications button - navigate to NotificationSettingsView
        notificationsButton.setOnAction(e -> {
            try {
                new NotificationSettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening Notification settings", ex);
            }
        });

        // Appearance button - navigate to AppearanceSettingsView
        appearanceButton.setOnAction(e -> {
            try {
                new AppearanceSettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening Appearance settings", ex);
            }
        });

        // Language button - navigate to LanguageSettingsView
        languageButton.setOnAction(e -> {
            try {
                new LanguageSettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening Language settings", ex);
            }
        });

        // Privacy button - navigate to PrivacySettingsView
        privacyButton.setOnAction(e -> {
            try {
                new PrivacySettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening Privacy settings", ex);
            }
        });

        // About button - navigate to AboutSettingsView
        aboutButton.setOnAction(e -> {
            try {
                new AboutSettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening About settings", ex);
            }
        });
    }

    @Override
    protected void updateTexts() {
        // Buttons are icon-based, no text to update
        // If we had text labels, we would update them here:
        // Example: homeButton.setText(languageManager.getString("nav.home"));

        logger.debug("Settings view texts updated (no text elements present)");
    }

    @Override
    protected void preserveControlState() {
        // No state to preserve in settings menu (no form fields)
    }

    @Override
    protected void restoreControlState() {
        // No state to restore in settings menu (no form fields)
    }
}
