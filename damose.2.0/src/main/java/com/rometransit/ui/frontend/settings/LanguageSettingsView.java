package com.rometransit.ui.frontend.settings;

import com.rometransit.service.auth.AuthService;
import com.rometransit.service.user.PreferencesService;
import com.rometransit.ui.frontend.ViewBase;
import com.rometransit.ui.frontend.settings.SettingsView;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.stage.Stage;

/**
 * Language selection view - allows users to change the application language.
 * Uses JavaFX controls (ComboBox, Button) positioned over HTML background.
 */
public class LanguageSettingsView extends ViewBase {

    // Services
    private PreferencesService preferencesService;
    private AuthService authService;

    // JavaFX Controls
    private ComboBox<LanguageOption> languageComboBox;
    private Button applyButton;
    private Button settingsButton;

    // State
    private String selectedLanguage;

    public LanguageSettingsView(Stage stage) {
        super(stage, "LanguageSettingsView");
        this.preferencesService = new PreferencesService();
        this.authService = AuthService.getInstance();
    }

    @Override
    protected String getHTMLPath() {
        return "settings/language/Language.html";
    }

    @Override
    protected void createControls() {
        // Language selector ComboBox
        languageComboBox = new ComboBox<>();
        languageComboBox.getItems().addAll(
            new LanguageOption("it", "Italiano"),
            new LanguageOption("en", "English")
        );
        languageComboBox.setPrefSize(200, 40);
        languageComboBox.setStyle(
            "-fx-font-size: 14px; " +
            "-fx-font-family: 'Inter'; " +
            "-fx-background-color: white; " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 8px; " +
            "-fx-background-radius: 8px;"
        );

        // Apply button
        applyButton = new Button();
        applyButton.setPrefSize(100, 40);
        applyButton.setStyle(
            "-fx-background-color: #4CAF50; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 14px; " +
            "-fx-font-family: 'Inter'; " +
            "-fx-border-radius: 8px; " +
            "-fx-background-radius: 8px; " +
            "-fx-cursor: hand;"
        );

        // Settings button (back to settings)
        settingsButton = new Button();
        settingsButton.setPrefSize(35, 35);
        settingsButton.setStyle(
            "-fx-background-color: rgba(0,0,0,0); " +
            "-fx-border-color: #ccc; " +
            "-fx-border-radius: 5px; " +
            "-fx-background-radius: 5px;"
        );
    }

    @Override
    protected void positionControls() {
        // Language ComboBox
        controlManager.addControl(languageComboBox, 146, 320, 200, 40);

        // Apply Button
        controlManager.addControl(applyButton, 1290, 920, 100, 40);

        // Settings Button (back arrow)
        controlManager.addControl(settingsButton, 28, 70, 35, 35);
    }

    @Override
    protected void setupEventHandlers() {
        // Apply button click
        applyButton.setOnAction(e -> handleApplyLanguage());

        // Settings button click (back to settings)
        settingsButton.setOnAction(e -> {
            try {
                new SettingsView(stage).show();
            } catch (Exception ex) {
                logger.error("Error opening Settings", ex);
            }
        });

        // Update selected language when combo box changes
        languageComboBox.setOnAction(e -> {
            LanguageOption selected = languageComboBox.getValue();
            if (selected != null) {
                selectedLanguage = selected.getCode();
                logger.info("Language selected: " + selectedLanguage);
            }
        });
    }

    @Override
    protected void updateTexts() {
        // Update button text based on current language
        String currentLang = languageManager.getCurrentLanguageCode();
        applyButton.setText(languageManager.getString("language.applyButton"));

        // Set current language in ComboBox
        for (LanguageOption option : languageComboBox.getItems()) {
            if (option.getCode().equals(currentLang)) {
                languageComboBox.setValue(option);
                selectedLanguage = currentLang;
                break;
            }
        }
    }

    @Override
    protected void preserveControlState() {
        // Save current selection
        LanguageOption selected = languageComboBox.getValue();
        if (selected != null) {
            selectedLanguage = selected.getCode();
        }
    }

    @Override
    protected void restoreControlState() {
        // Restore selection after language change
        if (selectedLanguage != null) {
            for (LanguageOption option : languageComboBox.getItems()) {
                if (option.getCode().equals(selectedLanguage)) {
                    languageComboBox.setValue(option);
                    break;
                }
            }
        }
    }

    /**
     * Handle apply button click - change language
     */
    private void handleApplyLanguage() {
        if (selectedLanguage == null || selectedLanguage.isEmpty()) {
            logger.warn("No language selected");
            return;
        }

        logger.info("Applying language change to: " + selectedLanguage);

        // Update LanguageManager (applies to the whole application)
        languageManager.setLanguage(selectedLanguage);

        // Save preference if user is logged in
        if (authService.getCurrentUser() != null) {
            String userId = authService.getCurrentUser().getUsername();
            try {
                preferencesService.setPreferredLanguage(userId, selectedLanguage);
                logger.info("Language preference saved for user: " + userId);
            } catch (Exception e) {
                logger.error("Failed to save language preference", e);
            }
        }

        logger.info("Language changed successfully to: " + selectedLanguage);
        // Note: The language change listener will automatically reload the view
    }

    /**
     * Language option for ComboBox
     */
    private static class LanguageOption {
        private final String code;
        private final String displayName;

        public LanguageOption(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
