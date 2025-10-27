package com.rometransit.ui.frontend.settings;

import com.rometransit.ui.frontend.settings.SettingsView;
import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.ui.frontend.home.HomeView;
import com.rometransit.service.auth.AuthService;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.service.user.FavoriteService;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.model.entity.User;
import com.rometransit.model.entity.Favorite;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Stop;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.data.repository.StopRepository;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.scene.input.MouseButton;
import java.net.URL;
import java.util.List;
import java.util.Optional;

public class AccountSettingsView {
    private Stage stage;
    private AuthService authService;
    private FavoriteService favoriteService;
    private RouteRepository routeRepository;
    private StopRepository stopRepository;
    private GTFSDataManager gtfsDataManager;
    private LanguageManager languageManager;
    private ListView<String> favoritesListView;
    private boolean showingRoutes = false;
    private WebEngine webEngine;

    public AccountSettingsView(Stage stage, AuthService authService) {
        this.stage = stage;
        this.authService = authService;
        this.favoriteService = new FavoriteService();
        this.routeRepository = new RouteRepository();
        this.stopRepository = new StopRepository();
        this.gtfsDataManager = GTFSDataManager.getInstance();
        this.languageManager = LanguageManager.getInstance();
    }

    public void show() {
        WebView webView = new WebView();
        this.webEngine = webView.getEngine();

        // Load HTML with language-aware path
        loadHTMLBackground(webView);

        // Setup language change listener
        setupLanguageListener(webView);

        // Get current user
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            System.err.println("No user logged in!");
            return;
        }

        // Settings Button
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

        // Username Label
        Label usernameLabel = new Label(currentUser.getUsername());
        usernameLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 20px; -fx-text-fill: black;");

        // Password Label (showing asterisks)
        Label passwordLabel = new Label("********");
        passwordLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 20px; -fx-text-fill: black;");

        // Change Password Button (invisible, over HTML text)
        Button changePasswordButton = new Button();
        changePasswordButton.setPrefSize(118, 25);
        changePasswordButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        changePasswordButton.setOnAction(e -> showChangePasswordDialog());

        // Routes View Button (invisible, over HTML text)
        Button routesButton = new Button();
        routesButton.setPrefSize(109, 23);
        routesButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        routesButton.setOnAction(e -> {
            showingRoutes = true;
            loadFavorites();
        });

        // Stops View Button (invisible, over HTML text)
        Button stopsButton = new Button();
        stopsButton.setPrefSize(109, 23);
        stopsButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        stopsButton.setOnAction(e -> {
            showingRoutes = false;
            loadFavorites();
        });

        // Favorites ListView (in the right panel)
        favoritesListView = new ListView<>();
        favoritesListView.setPrefSize(408, 486);
        favoritesListView.setStyle("-fx-background-color: #FFFAFA; -fx-border-radius: 25px; -fx-background-radius: 25px; -fx-font-family: 'Poppins'; -fx-font-size: 14px;");

        // Double-click to open HomeView with selected favorite
        favoritesListView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openFavoriteInHome();
            }
        });

        // Add Button (invisible, over HTML text)
        Button addButton = new Button();
        addButton.setPrefSize(100, 24);
        addButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        addButton.setOnAction(e -> showAddFavoriteDialog());

        // Remove Button (invisible, over HTML text)
        Button removeButton = new Button();
        removeButton.setPrefSize(100, 24);
        removeButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        removeButton.setOnAction(e -> removeFavorite());

        // Logout Button (invisible, over HTML text)
        Button logoutButton = new Button();
        logoutButton.setPrefSize(153, 40);
        logoutButton.setStyle("-fx-background-color: rgba(0,0,0,0); -fx-border-color: transparent;");
        logoutButton.setOnAction(e -> {
            authService.logout();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(LanguageManager.getInstance().getString("account.logout"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.logoutSuccess"));
            alert.showAndWait();
            // Navigate back to home or login
            try {
                new SettingsView(stage).show();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        AnchorPane root = new AnchorPane();
        root.getChildren().addAll(webView, settingsButton, usernameLabel, passwordLabel,
                changePasswordButton, routesButton, stopsButton, favoritesListView,
                addButton, removeButton, logoutButton);

        // Position components
        AnchorPane.setTopAnchor(settingsButton, 70.0);
        AnchorPane.setLeftAnchor(settingsButton, 28.0);

        AnchorPane.setTopAnchor(usernameLabel, 373.0);
        AnchorPane.setLeftAnchor(usernameLabel, 213.0);

        AnchorPane.setTopAnchor(passwordLabel, 404.0);
        AnchorPane.setLeftAnchor(passwordLabel, 213.0);

        AnchorPane.setTopAnchor(changePasswordButton, 386.0);
        AnchorPane.setLeftAnchor(changePasswordButton, 392.0);

        AnchorPane.setTopAnchor(routesButton, 516.0);
        AnchorPane.setLeftAnchor(routesButton, 392.0);

        AnchorPane.setTopAnchor(stopsButton, 554.0);
        AnchorPane.setLeftAnchor(stopsButton, 392.0);

        AnchorPane.setTopAnchor(favoritesListView, 311.0);
        AnchorPane.setLeftAnchor(favoritesListView, 965.0);

        AnchorPane.setTopAnchor(addButton, 805.0);
        AnchorPane.setLeftAnchor(addButton, 1157.0);

        AnchorPane.setTopAnchor(removeButton, 805.0);
        AnchorPane.setLeftAnchor(removeButton, 1269.0);

        AnchorPane.setTopAnchor(logoutButton, 925.0);
        AnchorPane.setLeftAnchor(logoutButton, 1236.0);

        webView.setPrefSize(1440, 1000);
        AnchorPane.setTopAnchor(webView, 0.0);
        AnchorPane.setLeftAnchor(webView, 0.0);

        Scene scene = new Scene(root, 1440, 1000);
        stage.setTitle(LanguageManager.getInstance().getString("account.title"));
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Show dialog to change password
     */
    private void showChangePasswordDialog() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle(LanguageManager.getInstance().getString("account.changePassword"));
        dialog.setHeaderText(LanguageManager.getInstance().getString("account.changePasswordHeader"));

        ButtonType changeButtonType = new ButtonType(LanguageManager.getInstance().getString("account.changePasswordButton"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(changeButtonType, ButtonType.CANCEL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(20));

        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText(LanguageManager.getInstance().getString("account.oldPassword"));

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText(LanguageManager.getInstance().getString("account.newPassword"));

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText(LanguageManager.getInstance().getString("account.confirmNewPassword"));

        vbox.getChildren().addAll(
            new Label(LanguageManager.getInstance().getString("account.oldPasswordLabel")), oldPasswordField,
            new Label(LanguageManager.getInstance().getString("account.newPasswordLabel")), newPasswordField,
            new Label(LanguageManager.getInstance().getString("account.confirmPasswordLabel")), confirmPasswordField
        );

        dialog.getDialogPane().setContent(vbox);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == changeButtonType) {
                return new String[]{oldPasswordField.getText(), newPasswordField.getText(), confirmPasswordField.getText()};
            }
            return null;
        });

        Optional<String[]> result = dialog.showAndWait();
        result.ifPresent(passwords -> {
            String oldPassword = passwords[0];
            String newPassword = passwords[1];
            String confirmPassword = passwords[2];

            if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(LanguageManager.getInstance().getString("account.emptyFields"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("account.emptyFieldsMessage"));
                alert.showAndWait();
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("account.passwordMismatch"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("account.passwordMismatchMessage"));
                alert.showAndWait();
                return;
            }

            try {
                if (authService.changePassword(oldPassword, newPassword)) {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle(LanguageManager.getInstance().getString("account.passwordChanged"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("account.passwordChangedMessage"));
                    alert.showAndWait();
                } else {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(LanguageManager.getInstance().getString("account.error"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("account.wrongOldPassword"));
                    alert.showAndWait();
                }
            } catch (IllegalArgumentException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("account.error"));
                alert.setHeaderText(null);
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * Load favorites into ListView
     */
    private void loadFavorites() {
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) return;

        favoritesListView.getItems().clear();

        List<Favorite> favorites;
        if (showingRoutes) {
            favorites = favoriteService.getUserFavoriteRoutes(currentUser.getUserId());
            for (Favorite fav : favorites) {
                String displayText = getRouteDisplayName(fav.getRouteId());
                favoritesListView.getItems().add(displayText);
            }
        } else {
            favorites = favoriteService.getUserFavoriteStops(currentUser.getUserId());
            for (Favorite fav : favorites) {
                String displayText = getStopDisplayName(fav.getStopId());
                favoritesListView.getItems().add(displayText);
            }
        }
    }

    /**
     * Get display name for route (e.g., "🚌 Linea 71 - Termini-Clodio")
     */
    private String getRouteDisplayName(String routeId) {
        Optional<Route> route = routeRepository.findById(routeId);
        if (route.isPresent()) {
            Route r = route.get();
            String shortName = r.getRouteShortName() != null ? r.getRouteShortName() : routeId;
            String longName = r.getRouteLongName() != null ? " - " + r.getRouteLongName() : "";
            return LanguageManager.getInstance().getString("account.routeDisplayPrefix") + " " + shortName + longName;
        }
        return LanguageManager.getInstance().getString("account.routeFallback") + " " + routeId;
    }

    /**
     * Get display name for stop (e.g., "🚏 Viminale")
     */
    private String getStopDisplayName(String stopId) {
        Optional<Stop> stop = stopRepository.findById(stopId);
        if (stop.isPresent()) {
            Stop s = stop.get();
            String name = s.getStopName() != null ? s.getStopName() : stopId;
            return LanguageManager.getInstance().getString("account.stopDisplayPrefix") + " " + name;
        }
        return LanguageManager.getInstance().getString("account.stopFallback") + " " + stopId;
    }

    /**
     * Show dialog to add a favorite
     */
    private void showAddFavoriteDialog() {
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(LanguageManager.getInstance().getString("account.addFavorite"));
        dialog.setHeaderText(showingRoutes ? LanguageManager.getInstance().getString("account.addRouteHeader") : LanguageManager.getInstance().getString("account.addStopHeader"));
        dialog.setContentText(showingRoutes ? LanguageManager.getInstance().getString("account.routeLabel") : LanguageManager.getInstance().getString("account.stopLabel"));

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            if (input.trim().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(LanguageManager.getInstance().getString("account.emptyField"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("account.emptyFieldMessage"));
                alert.showAndWait();
                return;
            }

            String itemId = null;
            Favorite.FavoriteType type = showingRoutes ? Favorite.FavoriteType.ROUTE : Favorite.FavoriteType.STOP;

            // Search for route/stop by name or ID
            if (showingRoutes) {
                // Try to find route by short name or search
                List<Route> routes = gtfsDataManager.searchRoutes(input.trim());
                if (routes.isEmpty()) {
                    // Try by ID
                    Optional<Route> route = routeRepository.findById(input.trim());
                    if (route.isPresent()) {
                        itemId = route.get().getRouteId();
                    }
                } else {
                    itemId = routes.get(0).getRouteId();
                }

                if (itemId == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(LanguageManager.getInstance().getString("account.routeNotFound"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("account.routeNotFoundMessage", input.trim()));
                    alert.showAndWait();
                    return;
                }
            } else {
                // Try to find stop by name or search
                List<Stop> stops = gtfsDataManager.searchStops(input.trim());
                if (stops.isEmpty()) {
                    // Try by ID
                    Optional<Stop> stop = stopRepository.findById(input.trim());
                    if (stop.isPresent()) {
                        itemId = stop.get().getStopId();
                    }
                } else {
                    itemId = stops.get(0).getStopId();
                }

                if (itemId == null) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle(LanguageManager.getInstance().getString("account.stopNotFound"));
                    alert.setHeaderText(null);
                    alert.setContentText(LanguageManager.getInstance().getString("account.stopNotFoundMessage", input.trim()));
                    alert.showAndWait();
                    return;
                }
            }

            // Add favorite
            if (favoriteService.addFavorite(currentUser.getUserId(), itemId, type)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(LanguageManager.getInstance().getString("account.favoriteAdded"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("account.favoriteAddedMessage"));
                alert.showAndWait();
                loadFavorites();
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(LanguageManager.getInstance().getString("account.error"));
                alert.setHeaderText(null);
                alert.setContentText(LanguageManager.getInstance().getString("account.favoriteExists"));
                alert.showAndWait();
            }
        });
    }

    /**
     * Remove selected favorite
     */
    private void removeFavorite() {
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) return;

        String selected = favoritesListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(LanguageManager.getInstance().getString("account.noFavoriteSelected"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.noFavoriteSelectedMessage"));
            alert.showAndWait();
            return;
        }

        // Get the item ID from the selected favorite
        String itemId = extractIdFromDisplayText(selected);
        if (itemId == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(LanguageManager.getInstance().getString("account.error"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.cannotExtractId"));
            alert.showAndWait();
            return;
        }

        Favorite.FavoriteType type = showingRoutes ? Favorite.FavoriteType.ROUTE : Favorite.FavoriteType.STOP;

        if (favoriteService.removeFavorite(currentUser.getUserId(), itemId, type)) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(LanguageManager.getInstance().getString("account.favoriteRemoved"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.favoriteRemovedMessage"));
            alert.showAndWait();
            loadFavorites();
        } else {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(LanguageManager.getInstance().getString("account.error"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.cannotRemoveFavorite"));
            alert.showAndWait();
        }
    }

    /**
     * Extract ID from display text (e.g., "🚌 Linea 71 - Termini-Clodio" or "🚏 Viminale")
     */
    private String extractIdFromDisplayText(String displayText) {
        User currentUser = authService.getCurrentUser();
        if (currentUser == null) return null;

        List<Favorite> favorites;
        if (showingRoutes) {
            favorites = favoriteService.getUserFavoriteRoutes(currentUser.getUserId());
            for (Favorite fav : favorites) {
                if (getRouteDisplayName(fav.getRouteId()).equals(displayText)) {
                    return fav.getRouteId();
                }
            }
        } else {
            favorites = favoriteService.getUserFavoriteStops(currentUser.getUserId());
            for (Favorite fav : favorites) {
                if (getStopDisplayName(fav.getStopId()).equals(displayText)) {
                    return fav.getStopId();
                }
            }
        }
        return null;
    }

    /**
     * Open selected favorite in HomeView
     */
    private void openFavoriteInHome() {
        String selected = favoritesListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Get the item ID from the selected favorite
        String itemId = extractIdFromDisplayText(selected);
        if (itemId == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(LanguageManager.getInstance().getString("account.error"));
            alert.setHeaderText(null);
            alert.setContentText(LanguageManager.getInstance().getString("account.cannotOpenFavorite"));
            alert.showAndWait();
            return;
        }

        try {
            // Create HomeView
            HomeView homeView = new HomeView(stage);
            homeView.show();

            // Wait a moment for the view to initialize
            javafx.application.Platform.runLater(() -> {
                try {
                    if (showingRoutes) {
                        // Load route
                        Optional<Route> route = routeRepository.findById(itemId);
                        if (route.isPresent()) {
                            Route r = route.get();
                            homeView.showRoute(r);
                        }
                    } else {
                        // Load stop
                        Optional<Stop> stop = stopRepository.findById(itemId);
                        if (stop.isPresent()) {
                            Stop s = stop.get();
                            homeView.showStop(s);
                        }
                    }
                } catch (Exception ex) {
                    System.err.println("Error opening favorite in home: " + ex.getMessage());
                    ex.printStackTrace();
                }
            });
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Errore");
            alert.setHeaderText(null);
            alert.setContentText("Impossibile aprire la home: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    /**
     * Load HTML background with language-aware path
     */
    private void loadHTMLBackground(WebView webView) {
        try {
            String htmlPath = languageManager.getHTMLResourcePath("settings/account/Account.html");
            URL htmlUrl = getClass().getResource(htmlPath);

            if (htmlUrl == null) {
                System.err.println("Could not find Account.html at path: " + htmlPath);
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
            javafx.application.Platform.runLater(() -> {
                loadHTMLBackground(webView);
                // Reload favorites to update display text
                loadFavorites();
            });
        });
    }
}
