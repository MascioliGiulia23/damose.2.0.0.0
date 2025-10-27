package com.rometransit.ui.frontend.settings;

import com.rometransit.util.language.LanguageManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.net.URL;

/**
 * View for displaying the Privacy Policy document
 * Shows a full HTML page with privacy policy information
 */
public class PrivacyPolicyView {
    private Stage stage;
    private Stage parentStage;
    private LanguageManager languageManager;
    private WebEngine webEngine;

    /**
     * Constructor with parent stage reference for going back
     * @param stage The main stage
     */
    public PrivacyPolicyView(Stage stage) {
        this.stage = stage;
        this.parentStage = stage;
        this.languageManager = LanguageManager.getInstance();
    }

    public void show() {
        WebView webView = new WebView();
        this.webEngine = webView.getEngine();

        // Load HTML with language-aware path
        loadHTMLBackground(webView);

        // Setup language change listener
        setupLanguageListener(webView);

        // Back button to return to Privacy settings
        Button backButton = new Button();
        backButton.setPrefSize(35, 35);
        backButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-font-size: 12px;");
        backButton.setOnAction(e -> {
            try {
                new PrivacySettingsView(parentStage).show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(webView, backButton);

        // Position back button
        AnchorPane.setTopAnchor(backButton, 20.0);
        AnchorPane.setLeftAnchor(backButton, 20.0);

        // WebView takes full window
        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle("Damose - Privacy Policy");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground(WebView webView) {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("settings/privacy/PrivacyPolicy.html");
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
            String color = com.rometransit.ui.frontend.settings.ThemeManager.getAccent();
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
