package com.rometransit.ui.frontend.settings;

import com.rometransit.ui.frontend.settings.SettingsView;
import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.util.language.LanguageManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.net.URL;

public class AppearanceSettingsView {
    private Stage stage;
    private WebEngine webEngine;
    private LanguageManager languageManager;

    public AppearanceSettingsView(Stage stage) {
        this.stage = stage;
        this.languageManager = LanguageManager.getInstance();
    }

    public void show() {
        WebView webView = new WebView();
        this.webEngine = webView.getEngine();

        // Load HTML with language-aware path
        loadHTMLBackground(webView);

        // Setup language change listener
        setupLanguageListener(webView);

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

        // ====== Radio Buttons ======
        ToggleGroup group = new ToggleGroup();
        RadioButton rbGrey  = new RadioButton(LanguageManager.getInstance().getString("appearance.grey"));
        RadioButton rbRed   = new RadioButton(LanguageManager.getInstance().getString("appearance.red"));
        RadioButton rbGreen = new RadioButton(LanguageManager.getInstance().getString("appearance.green"));
        RadioButton rbBlue  = new RadioButton(LanguageManager.getInstance().getString("appearance.blue"));
        rbGrey.setToggleGroup(group);
        rbRed.setToggleGroup(group);
        rbGreen.setToggleGroup(group);
        rbBlue.setToggleGroup(group);

        // Seleziona il RadioButton in base al colore salvato
        String savedColor = ThemeManager.getAccent();
        if (savedColor.equals("#E57373")) {
            rbRed.setSelected(true);
        } else if (savedColor.equals("#81C784")) {
            rbGreen.setSelected(true);
        } else if (savedColor.equals("#64B5F6")) {
            rbBlue.setSelected(true);
        } else {
            rbGrey.setSelected(true);
        }

        VBox colorBox = new VBox(6, rbGrey, rbRed, rbGreen, rbBlue);

        // ====== Apply Change ======
        Button applyButton = new Button(LanguageManager.getInstance().getString("appearance.applyChange"));
        applyButton.setPrefSize(150, 40);
        applyButton.setStyle("-fx-background-color:#ddd; -fx-background-radius:10; -fx-border-radius:10; -fx-font-size:14px;");

        AnchorPane root = new AnchorPane();

        applyButton.setOnAction(e -> {
            RadioButton sel = (RadioButton) group.getSelectedToggle();
            if (sel == null) return;

            String t = sel.getText().toLowerCase();
            String color =
                    (t.contains("red")   || t.contains("rosso")) ? "#E57373" :
                            (t.contains("green") || t.contains("verde")) ? "#81C784" :
                                    (t.contains("blue")  || t.contains("blu"))   ? "#64B5F6" :
                                            "#D9D9D9"; // grey

            // Salva e applica subito
            ThemeManager.saveAccent(color);
            applyAccentToWebView(color);

            // ✅ Popup elegante
            Label toast = new Label("✔ " + LanguageManager.getInstance().getString("appearance.colorUpdated"));
            toast.setStyle(
                    "-fx-background-color: rgba(30, 30, 30, 0.9);" +
                            "-fx-text-fill: white;" +
                            "-fx-padding: 10px 25px;" +
                            "-fx-background-radius: 15px;" +
                            "-fx-font-size: 14px;" +
                            "-fx-font-family: 'Segoe UI', sans-serif;"
            );

            AnchorPane.setTopAnchor(toast, 50.0);
            AnchorPane.setRightAnchor(toast, 50.0);
            root.getChildren().add(toast);

            FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
            fadeIn.setFromValue(0);
            fadeIn.setToValue(1);
            fadeIn.play();

            // Fade-out automatico dopo 2 secondi
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                javafx.application.Platform.runLater(() -> {
                    FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toast);
                    fadeOut.setFromValue(1);
                    fadeOut.setToValue(0);
                    fadeOut.setOnFinished(ev -> root.getChildren().remove(toast));
                    fadeOut.play();
                });
            }).start();
        });

        root.getChildren().addAll(webView, settingsButton, colorBox, applyButton);

        AnchorPane.setTopAnchor(settingsButton, 70.0);
        AnchorPane.setLeftAnchor(settingsButton, 28.0);

        // Position color radio buttons
        AnchorPane.setTopAnchor(colorBox, 300.0);
        AnchorPane.setLeftAnchor(colorBox, 100.0);

        // Position apply button
        AnchorPane.setTopAnchor(applyButton, 450.0);
        AnchorPane.setLeftAnchor(applyButton, 100.0);

        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("settings.mainTitle"));
        stage.setScene(scene);
        stage.show();
    }

    /** Cambia SOLO i <div class="accent-panel"> nell'HTML caricato */
    private void applyAccentToWebView(String color) {
        if (webEngine == null) return;
        String js =
                "document.querySelectorAll('.accent-panel')" +
                        ".forEach(function(div){ div.style.background='" + color + "'; });";
        try {
            webEngine.executeScript(js);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground(WebView webView) {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("settings/appearance/Appearance.html");
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
