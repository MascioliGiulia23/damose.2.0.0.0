package com.rometransit.ui.util.html;

import com.rometransit.ui.frontend.settings.ThemeManager;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.util.logging.Logger;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for managing HTML background rendering in JavaFX Views.
 * This class handles WebView as a background layer for visual structure,
 * while JavaFX controls are overlaid on top for interactivity.
 *
 * <p>Architecture:
 * <ul>
 *   <li>Layer 0 (bottom): WebView with HTML background (decorative only)</li>
 *   <li>Layer 1+ (top): JavaFX controls (Button, TextField, ListView, etc)</li>
 * </ul>
 *
 * <p>HTML files should contain ONLY decorative elements:
 * <ul>
 *   <li>Panels (div with .accent-panel class for theming)</li>
 *   <li>SVG icons and decorations</li>
 *   <li>Static text labels</li>
 *   <li>NO interactive elements (no input, button, form)</li>
 * </ul>
 *
 * <p><b>Performance Optimizations:</b>
 * <ul>
 *   <li>URL caching: Resolved resource URLs are cached for faster subsequent loads</li>
 *   <li>Lazy loading: HTML is loaded only when View.show() is called</li>
 *   <li>Pre-fetching: Optional pre-loading of likely next views</li>
 * </ul>
 *
 * @author Damose Team
 * @version 2.0
 */
public class HTMLBackgroundView {

    private static final Logger logger = Logger.getLogger(HTMLBackgroundView.class);

    // Static URL cache shared across all instances for performance
    private static final Map<String, URL> urlCache = new HashMap<>();

    // Retry configuration
    private static final int MAX_LOAD_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 500;

    private final WebView webView;
    private final WebEngine webEngine;
    private String currentHTMLPath;
    private boolean isLoaded = false;
    private int loadAttempts = 0;

    /**
     * Creates a new HTMLBackgroundView with a fresh WebView instance.
     */
    public HTMLBackgroundView() {
        this.webView = new WebView();
        this.webEngine = webView.getEngine();

        // Disable context menu (right-click) on WebView
        webView.setContextMenuEnabled(false);

        // Make WebView non-focusable (it's just a background)
        webView.setFocusTraversable(false);

        logger.info("HTMLBackgroundView initialized");
    }

    /**
     * Gets the WebView component that can be added to the scene graph.
     * This should be placed as the bottom layer in a StackPane or similar container.
     *
     * @return the WebView instance
     */
    public WebView getWebView() {
        return webView;
    }

    /**
     * Gets the WebEngine for advanced operations.
     *
     * @return the WebEngine instance
     */
    public WebEngine getWebEngine() {
        return webEngine;
    }

    /**
     * Loads HTML background from the appropriate language folder.
     * The HTML path is automatically resolved based on current language setting.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @return true if loading started successfully, false otherwise
     */
    public boolean loadBackground(String relativePath) {
        String htmlPath = getHTMLPath(relativePath);
        return loadBackgroundAbsolute(htmlPath);
    }

    /**
     * Loads HTML background from an absolute resource path.
     * Uses URL caching for improved performance and includes retry mechanism.
     *
     * @param absolutePath absolute resource path (e.g., "/html_it/login/login.html")
     * @return true if loading started successfully, false otherwise
     */
    public boolean loadBackgroundAbsolute(String absolutePath) {
        return loadBackgroundAbsoluteWithRetry(absolutePath, 0);
    }

    /**
     * Internal method for loading HTML with retry support.
     *
     * @param absolutePath absolute resource path
     * @param attemptNumber current attempt number (0-based)
     * @return true if loading started successfully, false otherwise
     */
    private boolean loadBackgroundAbsoluteWithRetry(String absolutePath, int attemptNumber) {
        try {
            logger.info("Loading HTML attempt " + (attemptNumber + 1) + "/" + MAX_LOAD_RETRIES + ": " + absolutePath);

            // Check cache first
            URL htmlUrl = urlCache.get(absolutePath);

            if (htmlUrl == null) {
                // Not in cache, try to resolve it
                htmlUrl = resolveHTMLUrl(absolutePath);

                if (htmlUrl == null) {
                    // Try fallback paths
                    htmlUrl = tryFallbackPaths(absolutePath);

                    if (htmlUrl == null) {
                        logger.error("Could not find HTML file after trying all paths: " + absolutePath);
                        loadErrorHTML("HTML file not found: " + absolutePath);
                        return false;
                    }
                }

                // Cache the resolved URL for future use
                urlCache.put(absolutePath, htmlUrl);
                logger.debug("Cached URL for: " + absolutePath);
            } else {
                logger.debug("Using cached URL for: " + absolutePath);
            }

            currentHTMLPath = absolutePath;
            loadAttempts = attemptNumber + 1;
            isLoaded = false;

            logger.info("Loading HTML from: " + htmlUrl);

            // Load HTML
            webEngine.load(htmlUrl.toExternalForm());

            // Setup load worker listener
            webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    logger.info("HTML loaded successfully on attempt " + loadAttempts + ": " + absolutePath);
                    isLoaded = true;
                    loadAttempts = 0; // Reset for next load
                    // Automatically apply theme when HTML loads
                    applyTheme();

                } else if (newState == Worker.State.FAILED) {
                    logger.error("Failed to load HTML on attempt " + loadAttempts + ": " + absolutePath);

                    // Retry if we haven't exceeded max retries
                    if (loadAttempts < MAX_LOAD_RETRIES) {
                        logger.info("Retrying HTML load in " + RETRY_DELAY_MS + "ms...");

                        // Schedule retry after delay
                        new Thread(() -> {
                            try {
                                Thread.sleep(RETRY_DELAY_MS);
                                javafx.application.Platform.runLater(() ->
                                    loadBackgroundAbsoluteWithRetry(absolutePath, loadAttempts)
                                );
                            } catch (InterruptedException e) {
                                logger.error("Retry interrupted", e);
                            }
                        }).start();

                    } else {
                        logger.error("All retry attempts exhausted for: " + absolutePath);
                        loadErrorHTML("Failed to load HTML after " + MAX_LOAD_RETRIES + " attempts: " + absolutePath);
                        isLoaded = false;
                        loadAttempts = 0;
                    }
                }
            });

            return true;

        } catch (Exception e) {
            logger.error("Exception loading HTML background (attempt " + (attemptNumber + 1) + "): " + absolutePath, e);

            // Retry on exception if we haven't exceeded max retries
            if (attemptNumber < MAX_LOAD_RETRIES - 1) {
                logger.info("Retrying after exception in " + RETRY_DELAY_MS + "ms...");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                    return loadBackgroundAbsoluteWithRetry(absolutePath, attemptNumber + 1);
                } catch (InterruptedException ie) {
                    logger.error("Retry interrupted", ie);
                }
            }

            loadErrorHTML("Error loading HTML: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the URL for an HTML file path.
     *
     * @param path the resource path
     * @return the resolved URL, or null if not found
     */
    private URL resolveHTMLUrl(String path) {
        try {
            URL url = getClass().getResource(path);
            if (url != null) {
                logger.debug("Resolved URL: " + path + " -> " + url);
            } else {
                logger.warn("Failed to resolve URL: " + path);
            }
            return url;
        } catch (Exception e) {
            logger.error("Exception resolving URL: " + path, e);
            return null;
        }
    }

    /**
     * Tries fallback paths if the primary path doesn't work.
     * For example, if /html_en/view.html fails, try /html_it/view.html
     *
     * @param originalPath the original path that failed
     * @return a fallback URL if found, or null
     */
    private URL tryFallbackPaths(String originalPath) {
        logger.info("Trying fallback paths for: " + originalPath);

        // Extract the relative path (everything after language folder)
        String relativePath = originalPath;

        if (originalPath.startsWith("/html_it/")) {
            relativePath = originalPath.substring("/html_it/".length());
            String fallbackPath = "/html_en/" + relativePath;
            logger.debug("Trying fallback: " + fallbackPath);
            URL url = getClass().getResource(fallbackPath);
            if (url != null) {
                logger.info("Found fallback HTML: " + fallbackPath);
                return url;
            }
        } else if (originalPath.startsWith("/html_en/")) {
            relativePath = originalPath.substring("/html_en/".length());
            String fallbackPath = "/html_it/" + relativePath;
            logger.debug("Trying fallback: " + fallbackPath);
            URL url = getClass().getResource(fallbackPath);
            if (url != null) {
                logger.info("Found fallback HTML: " + fallbackPath);
                return url;
            }
        }

        logger.warn("No fallback path found for: " + originalPath);
        return null;
    }

    /**
     * Gets the full HTML resource path based on current language.
     * Automatically prepends the correct language folder (html_it/ or html_en/).
     *
     * @param relativePath relative path (e.g., "login/login.html")
     * @return full resource path (e.g., "/html_it/login/login.html")
     */
    public String getHTMLPath(String relativePath) {
        LanguageManager languageManager = LanguageManager.getInstance();
        String languageFolder = languageManager.getHTMLFolderPrefix();

        // Ensure relativePath doesn't start with /
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        String fullPath = "/" + languageFolder + "/" + relativePath;
        logger.debug("Resolved HTML path: " + fullPath);

        return fullPath;
    }

    /**
     * Applies the current accent color theme to all .accent-panel elements in the HTML.
     * This method uses executeScript to modify CSS properties.
     * Should be called after HTML is loaded successfully.
     */
    public void applyTheme() {
        if (!isLoaded) {
            logger.warn("Cannot apply theme: HTML not loaded yet");
            return;
        }

        try {
            String accentColor = ThemeManager.getAccent();
            logger.debug("Applying theme color: " + accentColor);

            // JavaScript to update all .accent-panel elements
            String script = String.format(
                "document.querySelectorAll('.accent-panel').forEach(function(element) {" +
                "    element.style.background = '%s';" +
                "});",
                accentColor
            );

            webEngine.executeScript(script);
            logger.info("Theme applied successfully");

        } catch (Exception e) {
            logger.error("Error applying theme to HTML", e);
        }
    }

    /**
     * Reloads the current HTML background (useful for language changes).
     *
     * @return true if reload started successfully, false otherwise
     */
    public boolean reload() {
        if (currentHTMLPath == null) {
            logger.warn("Cannot reload: no HTML loaded yet");
            return false;
        }

        logger.info("Reloading HTML: " + currentHTMLPath);
        return loadBackgroundAbsolute(currentHTMLPath);
    }

    /**
     * Checks if HTML is currently loaded and ready.
     *
     * @return true if HTML is loaded, false otherwise
     */
    public boolean isLoaded() {
        return isLoaded;
    }

    /**
     * Gets the current HTML path that is loaded.
     *
     * @return current HTML path or null if nothing loaded
     */
    public String getCurrentHTMLPath() {
        return currentHTMLPath;
    }

    /**
     * Loads a minimal error HTML to display when the actual HTML fails to load.
     *
     * @param errorMessage error message to display
     */
    private void loadErrorHTML(String errorMessage) {
        String errorHtml = String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head><title>Error</title></head>" +
            "<body style='font-family: Arial; padding: 20px; background: #f0f0f0;'>" +
            "<h2 style='color: #d32f2f;'>Error Loading View</h2>" +
            "<p>%s</p>" +
            "<p style='color: #666; font-size: 12px;'>Please check the application logs for more details.</p>" +
            "</body>" +
            "</html>",
            errorMessage
        );

        webEngine.loadContent(errorHtml);
        isLoaded = false;
    }

    /**
     * Pre-fetches (caches) HTML URLs for likely next views without loading them.
     * This improves performance by resolving URLs ahead of time.
     * Use this after a view is shown to prepare the likely next view.
     *
     * <p>Example usage after showing HomeView:
     * <pre>
     * HTMLBackgroundView.prefetchHTML("settings/settings.html");
     * </pre>
     *
     * @param relativePaths one or more relative paths to pre-fetch
     */
    public static void prefetchHTML(String... relativePaths) {
        LanguageManager languageManager = LanguageManager.getInstance();
        String languageFolder = languageManager.getHTMLFolderPrefix();

        for (String relativePath : relativePaths) {
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }

            String fullPath = "/" + languageFolder + "/" + relativePath;

            // Skip if already cached
            if (urlCache.containsKey(fullPath)) {
                logger.debug("URL already cached: " + fullPath);
                continue;
            }

            try {
                URL htmlUrl = HTMLBackgroundView.class.getResource(fullPath);

                if (htmlUrl != null) {
                    urlCache.put(fullPath, htmlUrl);
                    logger.debug("Pre-fetched and cached URL: " + fullPath);
                } else {
                    logger.warn("Could not pre-fetch HTML (not found): " + fullPath);
                }

            } catch (Exception e) {
                logger.warn("Error pre-fetching HTML: " + fullPath, e);
            }
        }
    }

    /**
     * Clears the static URL cache.
     * Use this if resources change at runtime (rare in production).
     * Mainly useful for development/testing.
     */
    public static void clearCache() {
        int size = urlCache.size();
        urlCache.clear();
        logger.info("Cleared URL cache (" + size + " entries)");
    }

    /**
     * Gets the current cache size (for monitoring/debugging).
     *
     * @return number of cached URLs
     */
    public static int getCacheSize() {
        return urlCache.size();
    }

    /**
     * Cleans up resources used by this HTMLBackgroundView.
     * Call this when the view is no longer needed to free memory.
     */
    public void dispose() {
        try {
            webEngine.load(null);
            currentHTMLPath = null;
            isLoaded = false;
            logger.info("HTMLBackgroundView disposed");
        } catch (Exception e) {
            logger.error("Error disposing HTMLBackgroundView", e);
        }
    }
}
