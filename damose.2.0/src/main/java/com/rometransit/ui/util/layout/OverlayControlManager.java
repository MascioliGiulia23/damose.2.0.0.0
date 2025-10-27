package com.rometransit.ui.util.layout;

import com.rometransit.util.logging.Logger;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for managing JavaFX controls overlaid on top of HTML background.
 * Handles positioning, anchoring, and automatic repositioning on window resize.
 *
 * <p>This class helps maintain the correct positioning of JavaFX controls
 * relative to HTML background elements when the window is resized or layout changes.
 *
 * <p>Usage example:
 * <pre>
 * OverlayControlManager manager = new OverlayControlManager(rootPane, stage);
 * manager.addControl(usernameField, 100, 200, 300, 40);
 * manager.addControl(loginButton, 250, 300, 150, 45);
 * manager.enableAutoReposition();
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class OverlayControlManager {

    private static final Logger logger = Logger.getLogger(OverlayControlManager.class);

    private final AnchorPane rootPane;
    private final Stage stage;
    private final Map<Node, ControlPosition> controlPositions;
    private final List<ChangeListener<Number>> resizeListeners;
    private boolean autoRepositionEnabled;

    /**
     * Represents the position and size of a control.
     */
    private static class ControlPosition {
        double x;
        double y;
        double width;
        double height;

        ControlPosition(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Override
        public String toString() {
            return String.format("Position[x=%.1f, y=%.1f, w=%.1f, h=%.1f]", x, y, width, height);
        }
    }

    /**
     * Creates a new OverlayControlManager.
     *
     * @param rootPane the root AnchorPane that will contain the overlaid controls
     * @param stage the Stage for monitoring window resize events (can be null if resize not needed)
     */
    public OverlayControlManager(AnchorPane rootPane, Stage stage) {
        this.rootPane = rootPane;
        this.stage = stage;
        this.controlPositions = new HashMap<>();
        this.resizeListeners = new ArrayList<>();
        this.autoRepositionEnabled = false;

        logger.info("OverlayControlManager initialized");
    }

    /**
     * Adds a control to the overlay with specified position and size.
     * The control will be added to the root pane if not already present.
     *
     * @param control the JavaFX control to add
     * @param x the X coordinate (left position)
     * @param y the Y coordinate (top position)
     * @param width the width of the control
     * @param height the height of the control
     */
    public void addControl(Node control, double x, double y, double width, double height) {
        if (control == null) {
            logger.warn("Attempted to add null control");
            return;
        }

        // Store position
        ControlPosition position = new ControlPosition(x, y, width, height);
        controlPositions.put(control, position);

        // Apply position
        positionControl(control, position);

        // Add to pane if not already there
        if (!rootPane.getChildren().contains(control)) {
            rootPane.getChildren().add(control);
        }

        logger.debug("Added control: " + control.getClass().getSimpleName() + " at " + position);
    }

    /**
     * Adds a control without specifying size (will use preferred size).
     *
     * @param control the JavaFX control to add
     * @param x the X coordinate (left position)
     * @param y the Y coordinate (top position)
     */
    public void addControl(Node control, double x, double y) {
        if (control == null) {
            logger.warn("Attempted to add null control");
            return;
        }

        // Use preferred or current size
        double width = control.prefWidth(-1);
        double height = control.prefHeight(-1);

        if (width <= 0) width = control.getBoundsInLocal().getWidth();
        if (height <= 0) height = control.getBoundsInLocal().getHeight();

        addControl(control, x, y, width, height);
    }

    /**
     * Removes a control from the overlay.
     *
     * @param control the control to remove
     */
    public void removeControl(Node control) {
        if (control == null) return;

        controlPositions.remove(control);
        rootPane.getChildren().remove(control);

        logger.debug("Removed control: " + control.getClass().getSimpleName());
    }

    /**
     * Updates the position of a control.
     *
     * @param control the control to update
     * @param x new X coordinate
     * @param y new Y coordinate
     */
    public void updatePosition(Node control, double x, double y) {
        ControlPosition position = controlPositions.get(control);
        if (position == null) {
            logger.warn("Cannot update position: control not managed");
            return;
        }

        position.x = x;
        position.y = y;
        positionControl(control, position);

        logger.debug("Updated position for " + control.getClass().getSimpleName() + " to " + position);
    }

    /**
     * Updates all control positions (useful after layout changes).
     */
    public void updateAllPositions() {
        logger.debug("Updating all control positions");

        for (Map.Entry<Node, ControlPosition> entry : controlPositions.entrySet()) {
            positionControl(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Enables automatic repositioning of controls when the window is resized.
     * Requires a Stage to be provided in the constructor.
     */
    public void enableAutoReposition() {
        if (stage == null) {
            logger.warn("Cannot enable auto-reposition: no Stage provided");
            return;
        }

        if (autoRepositionEnabled) {
            logger.debug("Auto-reposition already enabled");
            return;
        }

        // Add listeners for width and height changes
        ChangeListener<Number> widthListener = (obs, oldVal, newVal) -> {
            logger.debug("Stage width changed: " + oldVal + " -> " + newVal);
            updateAllPositions();
        };

        ChangeListener<Number> heightListener = (obs, oldVal, newVal) -> {
            logger.debug("Stage height changed: " + oldVal + " -> " + newVal);
            updateAllPositions();
        };

        stage.widthProperty().addListener(widthListener);
        stage.heightProperty().addListener(heightListener);

        resizeListeners.add(widthListener);
        resizeListeners.add(heightListener);

        autoRepositionEnabled = true;
        logger.info("Auto-reposition enabled");
    }

    /**
     * Disables automatic repositioning.
     */
    public void disableAutoReposition() {
        if (stage == null || !autoRepositionEnabled) {
            return;
        }

        // Remove all listeners
        for (ChangeListener<Number> listener : resizeListeners) {
            stage.widthProperty().removeListener(listener);
            stage.heightProperty().removeListener(listener);
        }

        resizeListeners.clear();
        autoRepositionEnabled = false;

        logger.info("Auto-reposition disabled");
    }

    /**
     * Positions a control using AnchorPane anchors.
     *
     * @param control the control to position
     * @param position the position data
     */
    private void positionControl(Node control, ControlPosition position) {
        // Set anchors for AnchorPane
        AnchorPane.setLeftAnchor(control, position.x);
        AnchorPane.setTopAnchor(control, position.y);

        // Set preferred size if applicable
        if (control instanceof Pane) {
            ((Pane) control).setPrefSize(position.width, position.height);
        } else if (control.getClass().getSimpleName().contains("Control")) {
            // Try to set size using reflection for Control subclasses
            try {
                control.getClass().getMethod("setPrefWidth", double.class).invoke(control, position.width);
                control.getClass().getMethod("setPrefHeight", double.class).invoke(control, position.height);
            } catch (Exception e) {
                // Silently fail if methods not available
                logger.debug("Could not set preferred size for " + control.getClass().getSimpleName());
            }
        }
    }

    /**
     * Gets the current position of a control.
     *
     * @param control the control
     * @return the position, or null if control not managed
     */
    public ControlPosition getPosition(Node control) {
        return controlPositions.get(control);
    }

    /**
     * Gets the number of controls being managed.
     *
     * @return control count
     */
    public int getControlCount() {
        return controlPositions.size();
    }

    /**
     * Clears all controls from the overlay.
     */
    public void clearAll() {
        logger.info("Clearing all controls");

        // Remove all controls from pane
        for (Node control : new ArrayList<>(controlPositions.keySet())) {
            removeControl(control);
        }

        controlPositions.clear();
    }

    /**
     * Cleans up resources used by this manager.
     */
    public void dispose() {
        disableAutoReposition();
        clearAll();
        logger.info("OverlayControlManager disposed");
    }

    /**
     * Logs the current state of all managed controls (for debugging).
     */
    public void logState() {
        logger.info("=== OverlayControlManager State ===");
        logger.info("Total controls: " + controlPositions.size());
        logger.info("Auto-reposition: " + autoRepositionEnabled);

        for (Map.Entry<Node, ControlPosition> entry : controlPositions.entrySet()) {
            Node control = entry.getKey();
            ControlPosition position = entry.getValue();
            logger.info("  - " + control.getClass().getSimpleName() + ": " + position);
        }

        logger.info("==================================");
    }
}
