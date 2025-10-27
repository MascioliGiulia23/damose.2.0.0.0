package com.rometransit.ui.util.html;

import com.rometransit.util.logging.Logger;
import javafx.application.Platform;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTML Hot-Reload Service for development mode.
 *
 * <p>This service monitors HTML resource directories and automatically reloads
 * views when HTML files are modified. This feature is designed for development
 * and is enabled with the JVM flag: -Ddev.mode=true
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Watches html_it/ and html_en/ directories for changes</li>
 *   <li>Automatically reloads affected views when HTML is modified</li>
 *   <li>Preserves JavaFX control state during reload</li>
 *   <li>Disabled in production builds (unless explicitly enabled)</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * // Start monitoring (usually at app startup in dev mode)
 * if (HTMLHotReloadService.isDevMode()) {
 *     HTMLHotReloadService.getInstance().start();
 * }
 *
 * // Register a view for auto-reload
 * HTMLHotReloadService.getInstance().registerView(loginView);
 *
 * // Stop monitoring (at app shutdown)
 * HTMLHotReloadService.getInstance().stop();
 * </pre>
 *
 * <p><b>Enable dev mode:</b>
 * <pre>
 * java -Ddev.mode=true -jar damose.jar
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class HTMLHotReloadService {

    private static final Logger logger = Logger.getLogger(HTMLHotReloadService.class);
    private static HTMLHotReloadService instance;

    private final Map<String, com.rometransit.ui.frontend.ViewBase> registeredViews;
    private WatchService watchService;
    private ExecutorService executorService;
    private volatile boolean running = false;

    // Debounce delay to avoid multiple reloads for the same change
    private static final long DEBOUNCE_DELAY_MS = 300;
    private final Map<String, Long> lastReloadTimes;

    /**
     * Private constructor for singleton pattern.
     */
    private HTMLHotReloadService() {
        this.registeredViews = new HashMap<>();
        this.lastReloadTimes = new HashMap<>();
    }

    /**
     * Gets the singleton instance.
     *
     * @return the HTMLHotReloadService instance
     */
    public static synchronized HTMLHotReloadService getInstance() {
        if (instance == null) {
            instance = new HTMLHotReloadService();
        }
        return instance;
    }

    /**
     * Checks if development mode is enabled.
     * Development mode is enabled with JVM flag: -Ddev.mode=true
     *
     * @return true if dev mode is enabled
     */
    public static boolean isDevMode() {
        return Boolean.getBoolean("dev.mode");
    }

    /**
     * Starts the hot-reload service.
     * This will begin monitoring HTML directories for changes.
     *
     * @throws IOException if watch service cannot be started
     */
    public void start() throws IOException {
        if (!isDevMode()) {
            logger.info("Hot-reload disabled (not in dev mode)");
            return;
        }

        if (running) {
            logger.warn("Hot-reload service already running");
            return;
        }

        logger.info("Starting HTML hot-reload service...");

        // Create watch service
        watchService = FileSystems.getDefault().newWatchService();

        // Try to watch HTML directories (these might be in JAR, so we handle that)
        // In dev mode, we typically run from IDE with resource folders accessible
        registerWatchDirectories();

        // Create executor for watch thread
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HTML-HotReload-Watcher");
            t.setDaemon(true);
            return t;
        });

        running = true;

        // Start watching in background thread
        executorService.submit(this::watchLoop);

        logger.info("HTML hot-reload service started");
    }

    /**
     * Registers directories to watch for changes.
     */
    private void registerWatchDirectories() {
        // In dev mode, HTML files are typically in: src/main/resources/html_it/ and html_en/
        // We need to register the resource directories

        try {
            // Try to get the resources directory path
            // This works when running from IDE with resources as files (not in JAR)
            Path resourcesPath = Paths.get("src", "main", "resources");

            if (Files.exists(resourcesPath)) {
                registerDirectoryRecursive(resourcesPath.resolve("html_it"));
                registerDirectoryRecursive(resourcesPath.resolve("html_en"));
                logger.info("Registered HTML directories for hot-reload");
            } else {
                logger.warn("Resources directory not found, hot-reload may not work");
                logger.warn("Expected: " + resourcesPath.toAbsolutePath());
            }

        } catch (Exception e) {
            logger.error("Failed to register watch directories", e);
        }
    }

    /**
     * Registers a directory and all subdirectories for watching.
     *
     * @param dir the directory to watch
     */
    private void registerDirectoryRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            logger.debug("Directory does not exist, skipping: " + dir);
            return;
        }

        // Register this directory
        dir.register(watchService,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_CREATE);

        logger.debug("Watching: " + dir);

        // Register subdirectories
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isDirectory)
                  .forEach(subDir -> {
                      if (!subDir.equals(dir)) {
                          try {
                              subDir.register(watchService,
                                  StandardWatchEventKinds.ENTRY_MODIFY,
                                  StandardWatchEventKinds.ENTRY_CREATE);
                              logger.debug("Watching: " + subDir);
                          } catch (IOException e) {
                              logger.error("Failed to watch directory: " + subDir, e);
                          }
                      }
                  });
        }
    }

    /**
     * Main watch loop that monitors for file changes.
     */
    private void watchLoop() {
        logger.debug("Watch loop started");

        while (running) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();

                    // Only process HTML files
                    if (fileName.toString().endsWith(".html")) {
                        handleHTMLFileChange(fileName.toString());
                    }
                }

                key.reset();

            } catch (InterruptedException e) {
                logger.info("Watch loop interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error in watch loop", e);
            }
        }

        logger.debug("Watch loop stopped");
    }

    /**
     * Handles an HTML file change event.
     *
     * @param fileName the name of the file that changed
     */
    private void handleHTMLFileChange(String fileName) {
        logger.info("HTML file changed: " + fileName);

        // Debounce: check if we recently reloaded this file
        long now = System.currentTimeMillis();
        Long lastReload = lastReloadTimes.get(fileName);

        if (lastReload != null && (now - lastReload) < DEBOUNCE_DELAY_MS) {
            logger.debug("Ignoring duplicate reload event (debounced): " + fileName);
            return;
        }

        lastReloadTimes.put(fileName, now);

        // Clear URL cache to force reload
        HTMLBackgroundView.clearCache();

        // Reload all affected views on JavaFX thread
        Platform.runLater(() -> {
            for (var view : registeredViews.values()) {
                if (view != null && view.isShowing()) {
                    // TODO: Fix access to getHtmlBackground() - currently protected in ViewBase
                    // String viewHTMLPath = view.getHtmlBackground().getCurrentHTMLPath();

                    // Temporary workaround: reload all views when HTML changes
                    logger.info("Auto-reloading view: " + view.getViewName());
                    view.reloadForLanguageChange(); // Reuses the language change reload logic
                }
            }
        });
    }

    /**
     * Registers a view for automatic hot-reload.
     *
     * @param view the view to register
     */
    public void registerView(com.rometransit.ui.frontend.ViewBase view) {
        if (view == null || !isDevMode()) {
            return;
        }

        registeredViews.put(view.getViewName(), view);
        logger.debug("Registered view for hot-reload: " + view.getViewName());
    }

    /**
     * Unregisters a view from hot-reload.
     *
     * @param view the view to unregister
     */
    public void unregisterView(com.rometransit.ui.frontend.ViewBase view) {
        if (view == null) {
            return;
        }

        registeredViews.remove(view.getViewName());
        logger.debug("Unregistered view from hot-reload: " + view.getViewName());
    }

    /**
     * Stops the hot-reload service.
     */
    public void stop() {
        if (!running) {
            return;
        }

        logger.info("Stopping HTML hot-reload service...");

        running = false;

        try {
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            logger.error("Error closing watch service", e);
        }

        if (executorService != null) {
            executorService.shutdownNow();
        }

        registeredViews.clear();
        lastReloadTimes.clear();

        logger.info("HTML hot-reload service stopped");
    }

    /**
     * Checks if the service is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running;
    }
}
