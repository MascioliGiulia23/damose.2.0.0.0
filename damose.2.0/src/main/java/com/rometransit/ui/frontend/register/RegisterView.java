package com.rometransit.ui.frontend.register;

import com.rometransit.ui.frontend.login.LoginView;
import com.rometransit.ui.frontend.home.HomeView;
import com.rometransit.ui.frontend.settings.ThemeManager;
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

public class RegisterView {

    private Stage stage;
    private AuthService authService;
    private Runnable onLoginSuccess;
    private WebView webView;
    private WebEngine webEngine;

    // Store controls as fields to update them on language change
    private TextField usernameField;
    private PasswordField passwordField;
    private PasswordField confirmPasswordField;

    public RegisterView(Stage stage, AuthService authService, Runnable onLoginSuccess) {
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
            String htmlPath = "/" + languageFolder + "/register/register.html";

            InputStream inputStream = getClass().getResourceAsStream(htmlPath);
            if (inputStream == null) {
                System.err.println("Could not find register.html at path: " + htmlPath);
                // Fallback to Italian HTML if English not found
                htmlPath = "/html_it/register/register.html";
                inputStream = getClass().getResourceAsStream(htmlPath);
                if (inputStream == null) {
                    System.err.println("Could not find fallback register.html at: " + htmlPath);
                    return "";
                }
            }

            String htmlContent = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

            // Replace placeholders with translations
            htmlContent = htmlContent.replace("{{register.createAccount}}", lm.getString("register.createAccount"));
            htmlContent = htmlContent.replace("{{register.username}}", lm.getString("register.username"));
            htmlContent = htmlContent.replace("{{register.password}}", lm.getString("register.password"));
            htmlContent = htmlContent.replace("{{register.confirmPassword}}", lm.getString("register.confirmPassword"));
            htmlContent = htmlContent.replace("{{register.register}}", lm.getString("register.register"));
            htmlContent = htmlContent.replace("{{register.alreadyHaveAccount}}", lm.getString("register.alreadyHaveAccount"));
            htmlContent = htmlContent.replace("{{register.tagline}}", lm.getString("register.tagline"));

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
            try {
                HomeView homeView = new HomeView(stage);
                homeView.show();
            } catch (Exception ex) {
                System.err.println("Error opening Home: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Username TextField
        usernameField = new TextField();
        usernameField.setPrefSize(249, 35);
        usernameField.setPromptText(LanguageManager.getInstance().getString("register.username"));
        usernameField.setStyle(
                "-fx-background-color: white; " +
                "-fx-border-color: transparent; " +
                "-fx-font-family: 'Poppins'; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 0 0 0 40px;"
        );

        // Password TextField
        passwordField = new PasswordField();
        passwordField.setPrefSize(249, 35);
        passwordField.setPromptText(LanguageManager.getInstance().getString("register.password"));
        passwordField.setStyle(
                "-fx-background-color: white; " +
                "-fx-border-color: transparent; " +
                "-fx-font-family: 'Poppins'; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 0 0 0 40px;"
        );

        // Confirm Password TextField
        confirmPasswordField = new PasswordField();
        confirmPasswordField.setPrefSize(249, 35);
        confirmPasswordField.setPromptText(LanguageManager.getInstance().getString("register.confirmPassword"));
        confirmPasswordField.setStyle(
                "-fx-background-color: white; " +
                "-fx-border-color: transparent; " +
                "-fx-font-family: 'Poppins'; " +
                "-fx-font-size: 12px; " +
                "-fx-padding: 0 0 0 40px;"
        );

        // Register Button (invisible, over HTML text)
        Button registerButton = new Button();
        registerButton.setPrefSize(117, 31);
        registerButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        registerButton.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String confirmPassword = confirmPasswordField.getText();

            if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Alert alert = new Alert(AlertType.WARNING);
                alert.setTitle(LanguageManager.getInstance().getString("register.emptyFields"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("register.emptyFieldsMessage"));
                alert.showAndWait();
                return;
            }

            if (!password.equals(confirmPassword)) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("register.passwordMismatch"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("register.passwordMismatchMessage"));
                alert.showAndWait();
                return;
            }

            try {
                if (authService.registerUser(username, password)) {
                    Alert alert = new Alert(AlertType.INFORMATION);
                    alert.setTitle(LanguageManager.getInstance().getString("register.success"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("register.successMessage"));
                    alert.showAndWait();

                    // Navigate to Login
                    LoginView loginView = new LoginView(stage, authService, onLoginSuccess);
                    loginView.show();
                } else {
                    Alert alert = new Alert(AlertType.ERROR);
                    alert.setTitle(LanguageManager.getInstance().getString("register.failed"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("register.failedMessage"));
                    alert.showAndWait();
                }
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("register.error"));
                alert.setHeaderText(null);
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });

        // Login Button (invisible, over HTML underline)
        Button loginButton = new Button();
        loginButton.setPrefSize(40, 14);
        loginButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        loginButton.setOnAction(e -> {
            try {
                LoginView loginView = new LoginView(stage, authService, onLoginSuccess);
                loginView.show();
            } catch (Exception ex) {
                System.err.println("Error opening Login: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        // Layout
        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(webView, homeButton, usernameField, passwordField, confirmPasswordField, registerButton, loginButton);

        // Position components
        AnchorPane.setTopAnchor(homeButton, 70.0);
        AnchorPane.setLeftAnchor(homeButton, 28.0);

        AnchorPane.setTopAnchor(usernameField, 354.0);
        AnchorPane.setLeftAnchor(usernameField, 821.0);

        AnchorPane.setTopAnchor(passwordField, 433.0);
        AnchorPane.setLeftAnchor(passwordField, 821.0);

        AnchorPane.setTopAnchor(confirmPasswordField, 512.0);
        AnchorPane.setLeftAnchor(confirmPasswordField, 821.0);

        AnchorPane.setTopAnchor(registerButton, 625.0);
        AnchorPane.setLeftAnchor(registerButton, 887.0);

        AnchorPane.setTopAnchor(loginButton, 667.0);
        AnchorPane.setLeftAnchor(loginButton, 980.0);

        // WebView dimensions
        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        // Listen for language changes - reload HTML and update all text controls
        LanguageManager.getInstance().addLanguageChangeListener(() -> {
            refreshHTML();
            stage.setTitle(LanguageManager.getInstance().getString("register.title"));
            // Update TextField promptText for new language
            usernameField.setPromptText(LanguageManager.getInstance().getString("register.username"));
            passwordField.setPromptText(LanguageManager.getInstance().getString("register.password"));
            confirmPasswordField.setPromptText(LanguageManager.getInstance().getString("register.confirmPassword"));
        });

        // Listen for theme changes - apply new accent color to HTML background
        ThemeManager.addThemeChangeListener(() -> {
            applyThemeToHTML();
        });

        // Configure window
        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("register.title"));
        stage.setScene(scene);
        stage.show();
    }
}
