package com.rometransit.ui.util.html;

import com.rometransit.util.language.LanguageManager;
import com.rometransit.util.logging.Logger;

import java.net.URL;
import java.util.Locale;

/**
 * Utility class for loading HTML resources with fallback mechanisms.
 * Handles validation, error handling, and fallback to default language
 * when HTML files are missing.
 *
 * <p>Usage example:
 * <pre>
 * URL htmlUrl = HTMLResourceLoader.loadHTML("login/login.html", currentLocale);
 * if (htmlUrl != null) {
 *     webEngine.load(htmlUrl.toExternalForm());
 * }
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class HTMLResourceLoader {

    private static final Logger logger = Logger.getLogger(HTMLResourceLoader.class);

    // Fallback language (Italian)
    private static final String FALLBACK_LANGUAGE_FOLDER = "html_it";
    private static final Locale FALLBACK_LOCALE = new Locale("it");

    /**
     * Private constructor to prevent instantiation (utility class).
     */
    private HTMLResourceLoader() {
        throw new UnsupportedOperationException("Utility class - cannot be instantiated");
    }

    /**
     * Loads an HTML resource from the current language folder with automatic fallback.
     * If the HTML is not found in the current language, falls back to Italian.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @return URL to the HTML resource, or null if not found even with fallback
     */
    public static URL loadHTML(String relativePath) {
        Locale currentLocale = LanguageManager.getInstance().getCurrentLocale();
        return loadHTML(relativePath, currentLocale);
    }

    /**
     * Loads an HTML resource with the specified locale and automatic fallback.
     * First tries to load from the language-specific folder, then falls back to Italian.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @param locale the locale to use for loading
     * @return URL to the HTML resource, or null if not found even with fallback
     */
    public static URL loadHTML(String relativePath, Locale locale) {
        // Validate input
        if (relativePath == null || relativePath.trim().isEmpty()) {
            logger.error("Invalid HTML path: null or empty");
            return null;
        }

        // Try loading from the specified locale
        String languageFolder = getLanguageFolderForLocale(locale);
        URL htmlUrl = loadFromFolder(relativePath, languageFolder);

        if (htmlUrl != null) {
            logger.info("Successfully loaded HTML: " + htmlUrl);
            return htmlUrl;
        }

        // Fallback to Italian if not the same as current
        if (!languageFolder.equals(FALLBACK_LANGUAGE_FOLDER)) {
            logger.warn("HTML not found in " + languageFolder + ", trying fallback to " + FALLBACK_LANGUAGE_FOLDER);
            htmlUrl = loadFromFolder(relativePath, FALLBACK_LANGUAGE_FOLDER);

            if (htmlUrl != null) {
                logger.info("Successfully loaded HTML from fallback: " + htmlUrl);
                return htmlUrl;
            }
        }

        // If still not found, log error and return null
        logger.error("HTML resource not found: " + relativePath + " (tried " + languageFolder +
                     " and " + FALLBACK_LANGUAGE_FOLDER + ")");
        return null;
    }

    /**
     * Checks if an HTML resource exists in the current language folder.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @return true if the HTML file exists, false otherwise
     */
    public static boolean exists(String relativePath) {
        URL htmlUrl = loadHTML(relativePath);
        return htmlUrl != null;
    }

    /**
     * Checks if an HTML resource exists for the specified locale.
     *
     * @param relativePath relative path to HTML file (e.g., "login/login.html")
     * @param locale the locale to check
     * @return true if the HTML file exists for this locale, false otherwise
     */
    public static boolean exists(String relativePath, Locale locale) {
        String languageFolder = getLanguageFolderForLocale(locale);
        URL htmlUrl = loadFromFolder(relativePath, languageFolder);
        return htmlUrl != null;
    }

    /**
     * Gets the full resource path for an HTML file based on locale.
     *
     * @param relativePath relative path (e.g., "login/login.html")
     * @param locale the locale
     * @return full resource path (e.g., "/html_it/login/login.html")
     */
    public static String getResourcePath(String relativePath, Locale locale) {
        String languageFolder = getLanguageFolderForLocale(locale);
        return buildResourcePath(relativePath, languageFolder);
    }

    /**
     * Loads HTML from a specific language folder.
     *
     * @param relativePath relative path to HTML file
     * @param languageFolder language folder name (e.g., "html_it", "html_en")
     * @return URL to the HTML resource, or null if not found
     */
    private static URL loadFromFolder(String relativePath, String languageFolder) {
        try {
            String resourcePath = buildResourcePath(relativePath, languageFolder);
            logger.debug("Attempting to load HTML from: " + resourcePath);

            URL htmlUrl = HTMLResourceLoader.class.getResource(resourcePath);

            if (htmlUrl == null) {
                logger.debug("HTML not found at: " + resourcePath);
                return null;
            }

            // Validate that the URL is accessible
            try {
                htmlUrl.openStream().close();
                logger.debug("HTML validated successfully: " + resourcePath);
                return htmlUrl;
            } catch (Exception e) {
                logger.warn("HTML URL found but not accessible: " + resourcePath, e);
                return null;
            }

        } catch (Exception e) {
            logger.error("Error loading HTML from " + languageFolder + "/" + relativePath, e);
            return null;
        }
    }

    /**
     * Builds a full resource path from relative path and language folder.
     *
     * @param relativePath relative path
     * @param languageFolder language folder
     * @return full resource path with leading slash
     */
    private static String buildResourcePath(String relativePath, String languageFolder) {
        // Ensure relativePath doesn't start with /
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return "/" + languageFolder + "/" + relativePath;
    }

    /**
     * Gets the language folder name for a given locale.
     *
     * @param locale the locale
     * @return language folder name (e.g., "html_it", "html_en")
     */
    private static String getLanguageFolderForLocale(Locale locale) {
        if (locale == null) {
            logger.warn("Null locale provided, using fallback");
            return FALLBACK_LANGUAGE_FOLDER;
        }

        String languageCode = locale.getLanguage();
        switch (languageCode) {
            case "it":
                return "html_it";
            case "en":
                return "html_en";
            default:
                logger.warn("Unknown language code: " + languageCode + ", using fallback");
                return FALLBACK_LANGUAGE_FOLDER;
        }
    }

    /**
     * Creates a minimal safe HTML content for error display.
     *
     * @param errorMessage error message to display
     * @param viewName name of the view that failed to load
     * @return HTML string with error message
     */
    public static String createErrorHTML(String errorMessage, String viewName) {
        return String.format(
            "<!DOCTYPE html>" +
            "<html>" +
            "<head>" +
            "    <meta charset='UTF-8'>" +
            "    <title>Error - %s</title>" +
            "    <style>" +
            "        body {" +
            "            font-family: 'Segoe UI', Arial, sans-serif;" +
            "            padding: 40px;" +
            "            background: linear-gradient(135deg, #f5f5f5 0%%, #e0e0e0 100%%);" +
            "            margin: 0;" +
            "        }" +
            "        .error-container {" +
            "            background: white;" +
            "            border-radius: 8px;" +
            "            padding: 30px;" +
            "            box-shadow: 0 4px 6px rgba(0,0,0,0.1);" +
            "            max-width: 600px;" +
            "            margin: 0 auto;" +
            "        }" +
            "        h2 {" +
            "            color: #d32f2f;" +
            "            margin-top: 0;" +
            "        }" +
            "        p {" +
            "            color: #333;" +
            "            line-height: 1.6;" +
            "        }" +
            "        .details {" +
            "            color: #666;" +
            "            font-size: 14px;" +
            "            border-left: 3px solid #d32f2f;" +
            "            padding-left: 15px;" +
            "            margin-top: 20px;" +
            "        }" +
            "    </style>" +
            "</head>" +
            "<body>" +
            "    <div class='error-container'>" +
            "        <h2>⚠ Error Loading View</h2>" +
            "        <p><strong>View:</strong> %s</p>" +
            "        <p><strong>Error:</strong> %s</p>" +
            "        <div class='details'>" +
            "            <p>The HTML template for this view could not be loaded.</p>" +
            "            <p>Please check the application logs for more details.</p>" +
            "        </div>" +
            "    </div>" +
            "</body>" +
            "</html>",
            viewName,
            viewName,
            errorMessage
        );
    }
}
