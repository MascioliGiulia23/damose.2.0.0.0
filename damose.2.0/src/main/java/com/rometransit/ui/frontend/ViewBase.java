package com.rometransit.ui.frontend;

import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.util.logging.Logger;
import com.rometransit.ui.util.html.HTMLBackgroundView;
import com.rometransit.ui.util.layout.OverlayControlManager;
import com.rometransit.ui.util.layout.ViewManager;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;


public abstract class ViewBase {

    protected static final Logger logger = Logger.getLogger(ViewBase.class);

    // Core components
    protected final Stage stage;
    protected final String viewName;

    // Layout containers
    protected StackPane rootStackPane;
    protected AnchorPane controlsPane;

    // HTML background
    protected HTMLBackgroundView htmlBackground;
    protected WebView webView;
    protected WebEngine webEngine;

    // Control management
    protected OverlayControlManager controlManager;

    // Managers
    protected LanguageManager languageManager;
    protected ThemeManager themeManager;
    protected ViewManager viewManager;

    // State
    protected boolean isInitialized = false;
    protected boolean isShowing = false;

    /**
     * Creates a new ViewBase.
     *
     * @param stage the Stage where this view will be shown
     * @param viewName a descriptive name for this view (for logging)
     */
    public ViewBase(Stage stage, String viewName) {
        this.stage = stage;
        this.viewName = viewName;
        this.languageManager = LanguageManager.getInstance();
        this.viewManager = ViewManager.getInstance();

        // Register this view with the manager for memory management
        viewManager.registerView(this);

        logger.info("ViewBase created: " + viewName);
    }

    /**
     * Gets the HTML file path (relative to language folder).
     * Example: "login/login.html" will resolve to "/html_it/login/login.html"
     *
     * @return relative path to HTML file
     */
    protected abstract String getHTMLPath();

    /**
     * Creates all JavaFX controls for this view.
     * Called once during initialization.
     * Do NOT position controls here, use positionControls() instead.
     */
    protected abstract void createControls();

    /**
     * Positions all JavaFX controls on top of the HTML background.
     * Use controlManager.addControl() to position each control.
     * Called after controls are created and after language changes.
     */
    protected abstract void positionControls();

    /**
     * Sets up event handlers for controls (click, change, etc).
     * Called once during initialization.
     */
    protected abstract void setupEventHandlers();

    /**
     * Updates all text content in JavaFX controls based on current language.
     * Called after language changes.
     * Example: button.setText(languageManager.getString("login.button"));
     */
    protected abstract void updateTexts();

    /**
     * Shows this view in the Stage.
     * This method orchestrates the entire view setup:
     * 1. Initialize (if first time)
     * 2. Load HTML background
     * 3. Create and position controls
     * 4. Setup event handlers
     * 5. Setup language and theme listeners
     * 6. Display in Stage
     */
    public void show() {
        logger.info("Showing view: " + viewName);

        if (!isInitialized) {
            initialize();
        }

        // Load or reload HTML
        loadHTMLBackground();

        // Show the view
        Scene scene = new Scene(rootStackPane, 1440, 1024);
        stage.setScene(scene);
        stage.setTitle(viewName);

        if (!stage.isShowing()) {
            stage.show();
        }

        isShowing = true;

        // Notify ViewManager that this view is now active
        viewManager.setActiveView(this);

        // Dispose inactive views to free memory
        viewManager.disposeInactiveViews();

        logger.info("View shown: " + viewName);
    }

    /**
     * Initializes the view (called once, on first show).
     */
    protected void initialize() {
        logger.info("Initializing view: " + viewName);

        // Create layout containers
        rootStackPane = new StackPane();
        controlsPane = new AnchorPane();

        // Create HTML background
        htmlBackground = new HTMLBackgroundView();
        webView = htmlBackground.getWebView();
        webEngine = htmlBackground.getWebEngine();

        // Create control manager
        controlManager = new OverlayControlManager(controlsPane, stage);

        // Add layers to stack pane (bottom to top)
        rootStackPane.getChildren().add(webView);  // Layer 0: HTML background

        // Layer 1 can be added by subclasses (e.g., NativeMapView)
        addMiddleLayer();

        rootStackPane.getChildren().add(controlsPane);  // Layer 2: Controls

        // Create controls
        createControls();

        // Setup event handlers
        setupEventHandlers();

        // Setup language change listener
        setupLanguageListener();

        // Setup theme change listener
        setupThemeListener();

        isInitialized = true;
        logger.info("View initialized: " + viewName);
    }

    /**
     * Optional: Add a middle layer between HTML and controls.
     * Override this in subclasses if needed (e.g., for NativeMapView).
     * Default implementation does nothing.
     */
    protected void addMiddleLayer() {
        // Override in subclasses if needed
    }

    /**
     * Loads the HTML background based on current language.
     */
    protected void loadHTMLBackground() {
        String htmlPath = getHTMLPath();
        logger.info("Loading HTML background: " + htmlPath);

        boolean success = htmlBackground.loadBackground(htmlPath);

        if (!success) {
            logger.error("Failed to load HTML for " + viewName);
            return;
        }

        // Wait for HTML to load, then position controls
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                // HTML loaded successfully
                Platform.runLater(() -> {
                    // Position controls
                    positionControls();

                    // Update texts
                    updateTexts();

                    // Apply theme
                    applyTheme();
                });
            }
        });
    }

    /**
     * Applies the current accent color theme to the HTML background.
     */
    protected void applyTheme() {
        logger.debug("Applying theme to " + viewName);
        htmlBackground.applyTheme();
    }

    /**
     * Sets up a listener for language changes.
     * When language changes, reloads HTML and updates texts.
     */
    protected void setupLanguageListener() {
        languageManager.addLanguageChangeListener(() -> {
            logger.info("Language changed, reloading view: " + viewName);
            reloadForLanguageChange();
        });
    }

    /**
     * Sets up a listener for theme changes.
     * When theme changes, reapplies the accent color.
     */
    protected void setupThemeListener() {
        // Note: ThemeManager doesn't have a listener mechanism yet,
        // but we can manually call applyTheme() when needed
        // For now, this is a placeholder for future implementation
    }

    /**
     * Reloads the view when language changes.
     * Preserves control state and reloads HTML background.
     */
    public void reloadForLanguageChange() {
        logger.info("Reloading for language change: " + viewName);

        // Preserve control values if needed (override in subclass)
        preserveControlState();

        // Clear current controls
        controlsPane.getChildren().clear();

        // Reload HTML background (will trigger control repositioning)
        loadHTMLBackground();

        // Restore control values
        restoreControlState();

        logger.info("Reload complete: " + viewName);
    }

    /**
     * Preserves control state before language change.
     * Override in subclasses to save field values, selections, etc.
     */
    protected void preserveControlState() {
        // Override in subclasses if needed
    }

    /**
     * Restores control state after language change.
     * Override in subclasses to restore field values, selections, etc.
     */
    protected void restoreControlState() {
        // Override in subclasses if needed
    }

    /**
     * Disposes of this view and releases resources.
     * Call this when the view is no longer needed.
     */
    public void dispose() {
        logger.info("Disposing view: " + viewName);

        if (htmlBackground != null) {
            htmlBackground.dispose();
        }

        if (controlManager != null) {
            controlManager.dispose();
        }

        isShowing = false;
        isInitialized = false;

        logger.info("View disposed: " + viewName);
    }

    /**
     * Checks if this view is currently showing.
     *
     * @return true if showing, false otherwise
     */
    public boolean isShowing() {
        return isShowing;
    }

    /**
     * Checks if this view is initialized.
     *
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Gets the Stage associated with this view.
     *
     * @return the Stage
     */
    public Stage getStage() {
        return stage;
    }

    /**
     * Gets the view name.
     *
     * @return the view name
     */
    public String getViewName() {
        return viewName;
    }

    /**
     * Gets the root StackPane (for advanced customization).
     *
     * @return the root StackPane
     */
    protected StackPane getRootStackPane() {
        return rootStackPane;
    }

    /**
     * Gets the controls AnchorPane (for advanced customization).
     *
     * @return the controls AnchorPane
     */
    protected AnchorPane getControlsPane() {
        return controlsPane;
    }

    /**
     * Gets the HTMLBackgroundView instance.
     *
     * @return the HTMLBackgroundView
     */
    protected HTMLBackgroundView getHtmlBackground() {
        return htmlBackground;
    }

    /**
     * Gets the OverlayControlManager instance.
     *
     * @return the OverlayControlManager
     */
    protected OverlayControlManager getControlManager() {
        return controlManager;
    }
}
