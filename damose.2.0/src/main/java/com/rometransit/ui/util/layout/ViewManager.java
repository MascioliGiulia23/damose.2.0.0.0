package com.rometransit.ui.util.layout;

import com.rometransit.ui.frontend.ViewBase;
import com.rometransit.util.logging.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manages the lifecycle and memory usage of View instances in the application.
 * This class helps optimize memory by tracking active views and disposing
 * of views that are no longer visible.
 *
 * <p><b>Features:</b>
 * <ul>
 *   <li>Tracks currently active (visible) views</li>
 *   <li>Uses weak references for inactive views to allow garbage collection</li>
 *   <li>Automatically disposes views when they become inactive</li>
 *   <li>Suggests garbage collection after disposing heavy views (WebView-based)</li>
 *   <li>Provides memory usage monitoring</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>
 * ViewManager viewManager = ViewManager.getInstance();
 *
 * // Register when showing a view
 * viewManager.registerView(loginView);
 *
 * // Mark view as active
 * viewManager.setActiveView(loginView);
 *
 * // Cleanup when switching views
 * viewManager.disposeInactiveViews();
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class ViewManager {

    private static final Logger logger = Logger.getLogger(ViewManager.class);
    private static ViewManager instance;

    // Currently active (visible) view
    private ViewBase activeView;

    // List of all registered views (using weak references for inactive ones)
    private final List<WeakReference<ViewBase>> registeredViews;

    // Memory threshold for suggesting garbage collection (in MB)
    private static final long GC_THRESHOLD_MB = 100;

    /**
     * Private constructor for singleton pattern.
     */
    private ViewManager() {
        this.registeredViews = new ArrayList<>();
        logger.info("ViewManager initialized");
    }

    /**
     * Gets the singleton instance of ViewManager.
     *
     * @return the ViewManager instance
     */
    public static synchronized ViewManager getInstance() {
        if (instance == null) {
            instance = new ViewManager();
        }
        return instance;
    }

    /**
     * Registers a view with the manager.
     * This should be called when a view is first created.
     *
     * @param view the view to register
     */
    public void registerView(ViewBase view) {
        if (view == null) {
            logger.warn("Attempted to register null view");
            return;
        }

        // Clean up any dead weak references first
        cleanupDeadReferences();

        // Add as weak reference (allows GC if view becomes unreachable)
        registeredViews.add(new WeakReference<>(view));
        logger.debug("Registered view: " + view.getViewName());
    }

    /**
     * Sets the currently active (visible) view.
     * This will dispose of the previously active view if it's different.
     *
     * @param view the view that is now active
     */
    public void setActiveView(ViewBase view) {
        if (view == null) {
            logger.warn("Attempted to set null active view");
            return;
        }

        // If there's a different active view, dispose it
        if (activeView != null && activeView != view) {
            logger.info("Disposing previous active view: " + activeView.getViewName());
            disposeView(activeView);
        }

        activeView = view;
        logger.info("Active view set to: " + view.getViewName());

        // Log current memory usage
        logMemoryUsage();
    }

    /**
     * Gets the currently active view.
     *
     * @return the active view, or null if none
     */
    public ViewBase getActiveView() {
        return activeView;
    }

    /**
     * Disposes all inactive (non-showing) views to free memory.
     * The active view is NOT disposed.
     */
    public void disposeInactiveViews() {
        logger.info("Disposing inactive views...");

        int disposedCount = 0;
        Iterator<WeakReference<ViewBase>> iterator = registeredViews.iterator();

        while (iterator.hasNext()) {
            WeakReference<ViewBase> ref = iterator.next();
            ViewBase view = ref.get();

            if (view == null) {
                // Weak reference was garbage collected
                iterator.remove();
                continue;
            }

            // Dispose if not the active view and not showing
            if (view != activeView && !view.isShowing()) {
                logger.debug("Disposing inactive view: " + view.getViewName());
                disposeView(view);
                iterator.remove();
                disposedCount++;
            }
        }

        logger.info("Disposed " + disposedCount + " inactive views");

        // Suggest GC if we disposed views
        if (disposedCount > 0) {
            suggestGarbageCollection();
        }
    }

    /**
     * Disposes a specific view and releases its resources.
     *
     * @param view the view to dispose
     */
    private void disposeView(ViewBase view) {
        if (view == null) {
            return;
        }

        try {
            view.dispose();
            logger.debug("View disposed: " + view.getViewName());
        } catch (Exception e) {
            logger.error("Error disposing view: " + view.getViewName(), e);
        }
    }

    /**
     * Removes dead weak references from the registered views list.
     */
    private void cleanupDeadReferences() {
        registeredViews.removeIf(ref -> ref.get() == null);
    }

    /**
     * Suggests garbage collection if memory usage is high.
     * This is only a suggestion; the JVM decides when to actually run GC.
     */
    private void suggestGarbageCollection() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        if (usedMemory > GC_THRESHOLD_MB) {
            logger.info("High memory usage (" + usedMemory + " MB), suggesting garbage collection");
            System.gc();
        }
    }

    /**
     * Logs current memory usage for monitoring.
     */
    public void logMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        logger.debug(String.format(
            "Memory usage: %d MB used / %d MB total (max: %d MB)",
            usedMemory, totalMemory, maxMemory
        ));
    }

    /**
     * Gets the number of currently registered views (including weak references).
     *
     * @return number of registered views
     */
    public int getRegisteredViewCount() {
        cleanupDeadReferences();
        return registeredViews.size();
    }

    /**
     * Disposes all views and clears the manager.
     * Use this when shutting down the application.
     */
    public void disposeAll() {
        logger.info("Disposing all views...");

        for (WeakReference<ViewBase> ref : registeredViews) {
            ViewBase view = ref.get();
            if (view != null) {
                disposeView(view);
            }
        }

        registeredViews.clear();
        activeView = null;

        logger.info("All views disposed");
        suggestGarbageCollection();
    }
}
