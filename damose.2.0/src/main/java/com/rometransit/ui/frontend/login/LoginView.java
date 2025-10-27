package com.rometransit.ui.frontend.login;

import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.ui.frontend.register.RegisterView;
import com.rometransit.ui.frontend.settings.AccountSettingsView;
import com.rometransit.service.auth.AuthService;
import com.rometransit.util.language.LanguageManager;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.AnchorPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class LoginView {

    private Stage stage;
    private AuthService authService;
    private Runnable onLoginSuccess;
    private WebView webView;
    private WebEngine webEngine;

    public LoginView(Stage stage, AuthService authService, Runnable onLoginSuccess) {
        this.stage = stage;
        this.authService = authService;
        this.onLoginSuccess = onLoginSuccess;
    }

    /**
     * Loads HTML background template based on current language and replaces placeholders with translations.
     * The HTML contains ONLY decorative elements (panels, SVG icons, static text).
     * All interactive controls (TextField, Button) are JavaFX components positioned above the HTML.
     */
    private String loadHTMLWithTranslations() {
        try {
            // Get HTML folder based on current language
            LanguageManager lm = LanguageManager.getInstance();
            String languageFolder = lm.getCurrentLocale().getLanguage().equals("it") ? "html_it" : "html_en";
            String htmlPath = "/" + languageFolder + "/login/login.html";

            InputStream inputStream = getClass().getResourceAsStream(htmlPath);
            if (inputStream == null) {
                System.err.println("Could not find login.html at path: " + htmlPath);
                // Fallback to Italian HTML if English not found
                htmlPath = "/html_it/login/login.html";
                inputStream = getClass().getResourceAsStream(htmlPath);
                if (inputStream == null) {
                    System.err.println("Could not find fallback login.html at: " + htmlPath);
                    return "";
                }
            }

            String htmlContent = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Replace placeholders with translations
            htmlContent = htmlContent.replace("{{login.login}}", lm.getString("login.login"));
            htmlContent = htmlContent.replace("{{login.username}}", lm.getString("login.username"));
            htmlContent = htmlContent.replace("{{login.password}}", lm.getString("login.password"));
            htmlContent = htmlContent.replace("{{login.dontHaveAccount}}", lm.getString("login.dontHaveAccount"));

            return htmlContent;
        } catch (Exception e) {
            System.err.println("Error loading HTML file: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Apply the current theme accent color to all HTML elements with class "accent-panel"
     */
    private void applyThemeToHTML() {
        if (webEngine != null && webEngine.getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED) {
            String color = ThemeManager.getAccent();
            webEngine.executeScript(
                "document.querySelectorAll('.accent-panel')" +
                ".forEach(div => div.style.background = '" + color + "');"
            );
        }
    }

    private void refreshHTML() {
        String htmlContent = loadHTMLWithTranslations();
        webEngine.loadContent(htmlContent);

        // Apply accent color theme after HTML loads
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                applyThemeToHTML();
            }
        });
    }

    // Store controls as fields to update them on language change
    private TextField usernameField;
    private PasswordField passwordField;

    public void show() {
        // WebView for HTML rendering
        webView = new WebView();
        webEngine = webView.getEngine();

        refreshHTML();

        // Home Button
        Button homeButton = new Button();
        homeButton.setPrefSize(35, 35);
        homeButton.setStyle(
                "-fx-background-color: rgba(0,0,0,0); " +
                "-fx-border-color: #ccc; " +
                "-fx-border-radius: 5px; " +
                "-fx-background-radius: 5px; " +
                "-fx-font-size: 12px;"
        );

        homeButton.setOnAction(e -> {
            // Check if user is already logged in
            if (authService.isLoggedIn() && onLoginSuccess != null) {
                onLoginSuccess.run();
            } else {
                System.out.println("User not logged in, cannot navigate to Home");
            }
        });

        // Username TextField
        usernameField = new TextField();
        usernameField.setPrefSize(280, 41);
        usernameField.setPromptText(LanguageManager.getInstance().getString("login.username"));
        usernameField.setStyle(
                "-fx-background-color: white; " +
                "-fx-border-color: transparent; " +
                "-fx-font-family: 'Poppins'; " +
                "-fx-font-size: 15px; " +
                "-fx-padding: 0 0 0 40px;"
        );

        // Password TextField
        passwordField = new PasswordField();
        passwordField.setPrefSize(280, 40);
        passwordField.setPromptText(LanguageManager.getInstance().getString("login.password"));
        passwordField.setStyle(
                "-fx-background-color: white; " +
                "-fx-border-color: transparent; " +
                "-fx-font-family: 'Poppins'; " +
                "-fx-font-size: 15px; " +
                "-fx-padding: 0 0 0 40px;"
        );

        // Login Button (invisible, over HTML text)
        Button loginButton = new Button();
        loginButton.setPrefSize(93, 34);
        loginButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        loginButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();

            if (username.isEmpty() || password.isEmpty()) {
                Alert alert = new Alert(AlertType.WARNING);
                alert.setTitle(LanguageManager.getInstance().getString("login.emptyFields"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("login.emptyFieldsMessage"));
                alert.showAndWait();
                return;
            }

            if (authService.loginUser(username, password)) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.setTitle(LanguageManager.getInstance().getString("login.success"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("login.successMessage"));
                alert.showAndWait();

                // Open Account View
                AccountSettingsView accountView = new AccountSettingsView(stage, authService);
                accountView.show();
            } else {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("login.failed"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("login.failedMessage"));
                alert.showAndWait();
            }
        });

        // Register Button (invisible, over HTML text)
        Button registerButton = new Button();
        registerButton.setPrefSize(100, 20);  // Increased size for better clickability
        registerButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent; -fx-cursor: hand;");
        registerButton.setOnAction(e -> {
            try {
                System.out.println("Register button clicked!");
                RegisterView registerView = new RegisterView(stage, authService, onLoginSuccess);
                registerView.show();
            } catch (Exception ex) {
                System.err.println("Error opening Register: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Layout
        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(webView, homeButton, usernameField, passwordField, loginButton, registerButton);

        // Position components
        AnchorPane.setTopAnchor(homeButton, 70.0);
        AnchorPane.setLeftAnchor(homeButton, 28.0);

        AnchorPane.setTopAnchor(usernameField, 409.0);
        AnchorPane.setLeftAnchor(usernameField, 580.0);

        AnchorPane.setTopAnchor(passwordField, 512.0);
        AnchorPane.setLeftAnchor(passwordField, 580.0);

        AnchorPane.setTopAnchor(loginButton, 640.0);
        AnchorPane.setLeftAnchor(loginButton, 673.0);

        AnchorPane.setTopAnchor(registerButton, 574.0);  // Adjusted for new height
        AnchorPane.setLeftAnchor(registerButton, 680.0);  // Adjusted for new width

        // WebView dimensions
        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        // Listen for language changes - reload HTML and update all text controls
        LanguageManager.getInstance().addLanguageChangeListener(() -> {
            refreshHTML();
            stage.setTitle(LanguageManager.getInstance().getString("login.title"));
            // Update TextField promptText for new language
            usernameField.setPromptText(LanguageManager.getInstance().getString("login.username"));
            passwordField.setPromptText(LanguageManager.getInstance().getString("login.password"));
        });

        // Listen for theme changes - apply new accent color to HTML background
        ThemeManager.addThemeChangeListener(() -> {
            applyThemeToHTML();
        });

        // Configure window
        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("login.title"));
        stage.setScene(scene);
        stage.show();
    }
}
