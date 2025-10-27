package com.rometransit.ui.frontend.settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.prefs.Preferences;

public class ThemeManager {
    private static final String KEY_ACCENT = "accent_color";
    private static final String KEY_DARK_MODE = "dark_mode";

    // Listeners for theme changes - using WeakReference to prevent memory leaks
    private static final List<WeakReference<Runnable>> themeChangeListeners = new ArrayList<>();

    /**
     * Register a listener to be notified when the theme (accent color) changes.
     * Uses WeakReference to prevent memory leaks - listeners are automatically
     * removed when their views are garbage collected.
     */
    public static void addThemeChangeListener(Runnable listener) {
        if (listener == null) return;

        // Clean up dead references first
        cleanupDeadReferences();

        // Add new listener wrapped in WeakReference
        themeChangeListeners.add(new WeakReference<>(listener));
    }

    /**
     * Remove a theme change listener
     */
    public static void removeThemeChangeListener(Runnable listener) {
        if (listener == null) return;

        Iterator<WeakReference<Runnable>> iterator = themeChangeListeners.iterator();
        while (iterator.hasNext()) {
            WeakReference<Runnable> ref = iterator.next();
            Runnable r = ref.get();
            if (r == null || r == listener) {
                iterator.remove();
            }
        }
    }

    /**
     * Remove listeners that have been garbage collected
     */
    private static void cleanupDeadReferences() {
        themeChangeListeners.removeIf(ref -> ref.get() == null);
    }

    /**
     * Notify all listeners that the theme has changed.
     * Automatically cleans up listeners that have been garbage collected.
     */
    private static void notifyThemeChangeListeners() {
        // Clean up dead references first
        cleanupDeadReferences();

        // Notify active listeners
        for (WeakReference<Runnable> ref : themeChangeListeners) {
            Runnable listener = ref.get();
            if (listener != null) {
                try {
                    listener.run();
                } catch (Exception e) {
                    System.err.println("Error notifying theme change listener: " + e.getMessage());
                }
            }
        }
    }

    public static void saveAccent(String color) {
        Preferences.userNodeForPackage(ThemeManager.class).put(KEY_ACCENT, color);
        // Notify all listeners that theme changed
        notifyThemeChangeListeners();
    }

    public static String getAccent() {
        return Preferences.userNodeForPackage(ThemeManager.class)
                .get(KEY_ACCENT, "#D9D9D9"); // default grigio
    }

    /**
     * Set dark mode preference
     * @param darkMode true to enable dark mode, false for light mode
     */
    public static void setDarkMode(boolean darkMode) {
        Preferences.userNodeForPackage(ThemeManager.class).putBoolean(KEY_DARK_MODE, darkMode);
        System.out.println("Dark mode " + (darkMode ? "enabled" : "disabled"));
        // TODO: Apply theme to application UI
        // This would require access to the Scene to apply CSS stylesheets
    }

    /**
     * Check if dark mode is enabled
     * @return true if dark mode is enabled, false otherwise
     */
    public static boolean isDarkMode() {
        return Preferences.userNodeForPackage(ThemeManager.class).getBoolean(KEY_DARK_MODE, false);
    }
}
