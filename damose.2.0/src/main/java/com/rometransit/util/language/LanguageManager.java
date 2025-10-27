package com.rometransit.util.language;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Singleton class to manage application language and internationalization.
 * Supports Italian and English languages with instant switching and persistence.
 */
public class LanguageManager {
    private static LanguageManager instance;

    private static final String BUNDLE_BASE_NAME = "language/messages";
    private static final String LANGUAGE_FILE_PATH = System.getProperty("user.home") + "/.damose/language.txt";

    // Available languages
    public static final Locale ITALIAN = new Locale("it");
    public static final Locale ENGLISH = new Locale("en");

    // Current locale property (observable for instant UI updates)
    private final ObjectProperty<Locale> currentLocale;
    private ResourceBundle resourceBundle;

    private LanguageManager() {
        // Load saved language or default to Italian
        Locale savedLocale = loadSavedLanguage();
        currentLocale = new SimpleObjectProperty<>(savedLocale != null ? savedLocale : ITALIAN);
        loadResourceBundle();

        // Listen for locale changes to reload bundle and save preference
        currentLocale.addListener((obs, oldLocale, newLocale) -> {
            loadResourceBundle();
            saveLanguagePreference(newLocale);
        });
    }

    /**
     * Get the singleton instance of LanguageManager
     */
    public static synchronized LanguageManager getInstance() {
        if (instance == null) {
            instance = new LanguageManager();
        }
        return instance;
    }

    /**
     * Load the resource bundle for the current locale
     */
    private void loadResourceBundle() {
        try {
            resourceBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, currentLocale.get());
            System.out.println("Loaded resource bundle for locale: " + currentLocale.get());
        } catch (Exception e) {
            System.err.println("Failed to load resource bundle for locale: " + currentLocale.get());
            e.printStackTrace();
            // Fallback to default bundle
            resourceBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, ITALIAN);
        }
    }

    /**
     * Get localized string by key
     */
    public String getString(String key) {
        try {
            return resourceBundle.getString(key);
        } catch (Exception e) {
            System.err.println("Missing translation key: " + key);
            return "!" + key + "!";
        }
    }

    /**
     * Get localized string with formatted arguments
     * Example: getString("dashboard.metrics", 10, 5, 2.5, 85)
     */
    public String getString(String key, Object... args) {
        try {
            String pattern = resourceBundle.getString(key);
            return MessageFormat.format(pattern, args);
        } catch (Exception e) {
            System.err.println("Missing translation key or format error: " + key);
            return "!" + key + "!";
        }
    }

    /**
     * Set the current locale and reload resource bundle
     */
    public void setLocale(Locale locale) {
        if (locale != null && !locale.equals(currentLocale.get())) {
            currentLocale.set(locale);

            // Clear HTML URL cache when language changes to force reload with new paths
            try {
                Class<?> htmlBackgroundClass = Class.forName("com.rometransit.util.ui.HTMLBackgroundView");
                java.lang.reflect.Method clearCacheMethod = htmlBackgroundClass.getMethod("clearCache");
                clearCacheMethod.invoke(null);
                System.out.println("Cleared HTML URL cache for language change");
            } catch (Exception e) {
                System.err.println("Note: Could not clear HTML cache (not critical): " + e.getMessage());
            }
        }
    }

    /**
     * Set language by language code ("it" or "en")
     */
    public void setLanguage(String languageCode) {
        switch (languageCode.toLowerCase()) {
            case "it":
                setLocale(ITALIAN);
                break;
            case "en":
                setLocale(ENGLISH);
                break;
            default:
                System.err.println("Unsupported language code: " + languageCode);
        }
    }

    /**
     * Get the current locale
     */
    public Locale getCurrentLocale() {
        return currentLocale.get();
    }

    /**
     * Get the current locale property (for binding)
     */
    public ObjectProperty<Locale> currentLocaleProperty() {
        return currentLocale;
    }

    /**
     * Get the current language code ("it" or "en")
     */
    public String getCurrentLanguageCode() {
        return currentLocale.get().getLanguage();
    }

    /**
     * Check if current language is Italian
     */
    public boolean isItalian() {
        return ITALIAN.equals(currentLocale.get());
    }

    /**
     * Check if current language is English
     */
    public boolean isEnglish() {
        return ENGLISH.equals(currentLocale.get());
    }

    /**
     * Add a listener that will be notified when the language changes
     * @param listener Runnable to execute when language changes
     */
    public void addLanguageChangeListener(Runnable listener) {
        if (listener != null) {
            currentLocale.addListener((obs, oldLocale, newLocale) -> {
                listener.run();
            });
        }
    }

    /**
     * Get the HTML folder prefix based on current language.
     * Used to load HTML files from the correct language-specific folder.
     *
     * @return "html_it" for Italian, "html_en" for English
     */
    public String getHTMLFolderPrefix() {
        String languageCode = getCurrentLanguageCode();
        switch (languageCode) {
            case "it":
                return "html_it";
            case "en":
                return "html_en";
            default:
                System.err.println("Unknown language code: " + languageCode + ", defaulting to html_it");
                return "html_it";
        }
    }

    /**
     * Get the full HTML resource path based on current language.
     * Automatically prepends the correct language folder prefix.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @return full resource path (e.g., "/html_it/login/login.html")
     */
    public String getHTMLResourcePath(String relativePath) {
        String folderPrefix = getHTMLFolderPrefix();

        // Ensure relativePath doesn't start with /
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return "/" + folderPrefix + "/" + relativePath;
    }

    /**
     * Load saved language preference from file
     * @return Saved locale or null if not found
     */
    private Locale loadSavedLanguage() {
        try {
            Path path = Paths.get(LANGUAGE_FILE_PATH);
            if (Files.exists(path)) {
                String languageCode = Files.readString(path).trim();
                System.out.println("Loaded saved language: " + languageCode);

                if ("it".equals(languageCode)) {
                    return ITALIAN;
                } else if ("en".equals(languageCode)) {
                    return ENGLISH;
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to load saved language: " + e.getMessage());
        }
        return null; // Default will be used
    }

    /**
     * Save language preference to file for persistence
     * @param locale Locale to save
     */
    private void saveLanguagePreference(Locale locale) {
        try {
            Path path = Paths.get(LANGUAGE_FILE_PATH);
            // Create directory if it doesn't exist
            Files.createDirectories(path.getParent());

            // Save language code
            String languageCode = locale.getLanguage();
            Files.writeString(path, languageCode);

            System.out.println("Saved language preference: " + languageCode);
        } catch (IOException e) {
            System.err.println("Failed to save language preference: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
