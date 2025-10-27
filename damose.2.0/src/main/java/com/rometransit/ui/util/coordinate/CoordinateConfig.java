package com.rometransit.ui.util.coordinate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rometransit.util.logging.Logger;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Manages control coordinates from a JSON configuration file.
 *
 * <p>This class allows positioning coordinates to be defined in a JSON file
 * instead of hardcoded in Java, making it easier to adjust layouts without
 * recompiling the application.
 *
 * <p><b>JSON structure:</b>
 * <pre>
 * {
 *   "LoginView": {
 *     "usernameField": {"x": 100, "y": 200, "width": 300, "height": 40},
 *     "passwordField": {"x": 100, "y": 250, "width": 300, "height": 40},
 *     "loginButton": {"x": 250, "y": 310, "width": 150, "height": 45}
 *   },
 *   "RegisterView": {
 *     "usernameField": {"x": 100, "y": 150, "width": 300, "height": 40}
 *   }
 * }
 * </pre>
 *
 * <p><b>Usage:</b>
 * <pre>
 * CoordinateConfig config = CoordinateConfig.getInstance();
 *
 * // Get coordinates for a control
 * ControlCoordinates coords = config.getCoordinates("LoginView", "usernameField");
 * controlManager.addControl(usernameField, coords.x, coords.y, coords.width, coords.height);
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class CoordinateConfig {

    private static final Logger logger = Logger.getLogger(CoordinateConfig.class);
    private static CoordinateConfig instance;

    private static final String CONFIG_FILE = "/coordinates.json";
    private Map<String, Map<String, ControlCoordinates>> coordinates;

    /**
     * Represents the coordinates and dimensions of a control.
     */
    public static class ControlCoordinates {
        public final double x;
        public final double y;
        public final double width;
        public final double height;

        public ControlCoordinates(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("(x=%.1f, y=%.1f, width=%.1f, height=%.1f)", x, y, width, height);
        }
    }

    /**
     * Private constructor for singleton pattern.
     */
    private CoordinateConfig() {
        this.coordinates = new HashMap<>();
        loadCoordinates();
    }

    /**
     * Gets the singleton instance.
     *
     * @return the CoordinateConfig instance
     */
    public static synchronized CoordinateConfig getInstance() {
        if (instance == null) {
            instance = new CoordinateConfig();
        }
        return instance;
    }

    /**
     * Loads coordinates from the JSON configuration file.
     */
    private void loadCoordinates() {
        try {
            InputStream is = getClass().getResourceAsStream(CONFIG_FILE);

            if (is == null) {
                logger.warn("Coordinates config file not found: " + CONFIG_FILE);
                logger.info("Using default/hardcoded coordinates");
                return;
            }

            // Parse JSON using Jackson
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(is);

            // Parse each view's coordinates
            Iterator<String> viewNames = root.fieldNames();
            while (viewNames.hasNext()) {
                String viewName = viewNames.next();
                JsonNode viewCoords = root.get(viewName);
                Map<String, ControlCoordinates> controlMap = new HashMap<>();

                Iterator<String> controlNames = viewCoords.fieldNames();
                while (controlNames.hasNext()) {
                    String controlName = controlNames.next();
                    JsonNode coordObj = viewCoords.get(controlName);

                    double x = coordObj.get("x").asDouble();
                    double y = coordObj.get("y").asDouble();
                    double width = coordObj.get("width").asDouble();
                    double height = coordObj.get("height").asDouble();

                    ControlCoordinates coords = new ControlCoordinates(x, y, width, height);
                    controlMap.put(controlName, coords);

                    logger.debug(String.format("Loaded %s.%s: %s", viewName, controlName, coords));
                }

                coordinates.put(viewName, controlMap);
            }

            logger.info("Loaded coordinates for " + coordinates.size() + " views");

        } catch (Exception e) {
            logger.error("Error loading coordinates config", e);
            logger.info("Using default/hardcoded coordinates");
        }
    }

    /**
     * Gets coordinates for a specific control in a view.
     *
     * @param viewName the view name (e.g., "LoginView")
     * @param controlName the control name (e.g., "usernameField")
     * @return the coordinates, or null if not found
     */
    public ControlCoordinates getCoordinates(String viewName, String controlName) {
        Map<String, ControlCoordinates> viewCoords = coordinates.get(viewName);

        if (viewCoords == null) {
            logger.debug("No coordinates found for view: " + viewName);
            return null;
        }

        ControlCoordinates coords = viewCoords.get(controlName);

        if (coords == null) {
            logger.debug("No coordinates found for control: " + viewName + "." + controlName);
        }

        return coords;
    }

    /**
     * Gets coordinates for a control, with fallback to default values.
     *
     * @param viewName the view name
     * @param controlName the control name
     * @param defaultX default x coordinate
     * @param defaultY default y coordinate
     * @param defaultWidth default width
     * @param defaultHeight default height
     * @return the coordinates (from config or defaults)
     */
    public ControlCoordinates getCoordinatesOrDefault(
            String viewName, String controlName,
            double defaultX, double defaultY,
            double defaultWidth, double defaultHeight) {

        ControlCoordinates coords = getCoordinates(viewName, controlName);

        if (coords == null) {
            logger.debug(String.format(
                "Using default coordinates for %s.%s",
                viewName, controlName
            ));
            return new ControlCoordinates(defaultX, defaultY, defaultWidth, defaultHeight);
        }

        return coords;
    }

    /**
     * Checks if coordinates are available for a view.
     *
     * @param viewName the view name
     * @return true if coordinates exist for this view
     */
    public boolean hasCoordinates(String viewName) {
        return coordinates.containsKey(viewName);
    }

    /**
     * Gets all control names defined for a view.
     *
     * @param viewName the view name
     * @return array of control names, or empty array if view not found
     */
    public String[] getControlNames(String viewName) {
        Map<String, ControlCoordinates> viewCoords = coordinates.get(viewName);

        if (viewCoords == null) {
            return new String[0];
        }

        return viewCoords.keySet().toArray(new String[0]);
    }

    /**
     * Reloads coordinates from the configuration file.
     * Useful for development when coordinates are changed.
     */
    public void reload() {
        logger.info("Reloading coordinates configuration...");
        coordinates.clear();
        loadCoordinates();
    }

    /**
     * Creates a template JSON configuration file content for a view.
     * This can be used to generate starter config files.
     *
     * @param viewName the view name
     * @param controlNames the control names
     * @return JSON template string
     */
    public static String generateTemplate(String viewName, String... controlNames) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"").append(viewName).append("\": {\n");

        for (int i = 0; i < controlNames.length; i++) {
            String controlName = controlNames[i];
            json.append("    \"").append(controlName).append("\": ");
            json.append("{\"x\": 0, \"y\": 0, \"width\": 100, \"height\": 40}");

            if (i < controlNames.length - 1) {
                json.append(",");
            }

            json.append("\n");
        }

        json.append("  }\n");
        json.append("}\n");

        return json.toString();
    }
}
