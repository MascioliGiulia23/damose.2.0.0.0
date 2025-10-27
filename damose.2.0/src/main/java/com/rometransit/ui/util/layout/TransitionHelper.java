package com.rometransit.ui.util.layout;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Utility class for standardized CSS and JavaFX transitions/animations.
 *
 * <p>This class provides consistent animations across the application:
 * <ul>
 *   <li>Fade transitions for view changes</li>
 *   <li>Slide transitions for panels</li>
 *   <li>Loading indicators with progress</li>
 *   <li>Smooth language/theme change transitions</li>
 * </ul>
 *
 * <p><b>Standard durations:</b>
 * <ul>
 *   <li>FAST: 150ms - quick feedback (button clicks, hovers)</li>
 *   <li>NORMAL: 300ms - standard transitions (view changes)</li>
 *   <li>SLOW: 600ms - emphasis transitions (important changes)</li>
 * </ul>
 *
 * <p><b>Usage examples:</b>
 * <pre>
 * // Fade in a view
 * TransitionHelper.fadeIn(loginView.getRootStackPane());
 *
 * // Fade transition between views
 * TransitionHelper.fadeTransition(oldView, newView, () -> {
 *     // Callback when complete
 *     System.out.println("Transition complete");
 * });
 *
 * // Show loading indicator
 * ProgressIndicator loading = TransitionHelper.showLoading(rootPane);
 * // ... do work ...
 * TransitionHelper.hideLoading(rootPane, loading);
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class TransitionHelper {

    // Standard animation durations
    public static final Duration DURATION_FAST = Duration.millis(150);
    public static final Duration DURATION_NORMAL = Duration.millis(300);
    public static final Duration DURATION_SLOW = Duration.millis(600);

    /**
     * Fades in a node from transparent to opaque.
     *
     * @param node the node to fade in
     */
    public static void fadeIn(Node node) {
        fadeIn(node, DURATION_NORMAL, null);
    }

    /**
     * Fades in a node with custom duration.
     *
     * @param node the node to fade in
     * @param duration the fade duration
     * @param onFinished callback when animation finishes (can be null)
     */
    public static void fadeIn(Node node, Duration duration, Runnable onFinished) {
        if (node == null) {
            return;
        }

        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        if (onFinished != null) {
            fade.setOnFinished(e -> onFinished.run());
        }

        node.setOpacity(0.0);
        fade.play();
    }

    /**
     * Fades out a node from opaque to transparent.
     *
     * @param node the node to fade out
     */
    public static void fadeOut(Node node) {
        fadeOut(node, DURATION_NORMAL, null);
    }

    /**
     * Fades out a node with custom duration.
     *
     * @param node the node to fade out
     * @param duration the fade duration
     * @param onFinished callback when animation finishes (can be null)
     */
    public static void fadeOut(Node node, Duration duration, Runnable onFinished) {
        if (node == null) {
            return;
        }

        FadeTransition fade = new FadeTransition(duration, node);
        fade.setFromValue(1.0);
        fade.setToValue(0.0);

        if (onFinished != null) {
            fade.setOnFinished(e -> onFinished.run());
        }

        fade.play();
    }

    /**
     * Performs a smooth fade transition from one node to another.
     * The old node fades out while the new node fades in.
     *
     * @param oldNode the node to fade out (can be null)
     * @param newNode the node to fade in
     * @param onFinished callback when both transitions finish (can be null)
     */
    public static void fadeTransition(Node oldNode, Node newNode, Runnable onFinished) {
        if (newNode == null) {
            return;
        }

        if (oldNode == null) {
            // No old node, just fade in the new one
            fadeIn(newNode, DURATION_NORMAL, onFinished);
            return;
        }

        // Fade out old, fade in new
        fadeOut(oldNode, DURATION_NORMAL, () -> {
            oldNode.setVisible(false);

            newNode.setVisible(true);
            fadeIn(newNode, DURATION_NORMAL, onFinished);
        });
    }

    /**
     * Slides a node in from the right.
     *
     * @param node the node to slide in
     */
    public static void slideInFromRight(Node node) {
        slideInFromRight(node, DURATION_NORMAL, null);
    }

    /**
     * Slides a node in from the right with custom duration.
     *
     * @param node the node to slide in
     * @param duration the slide duration
     * @param onFinished callback when animation finishes (can be null)
     */
    public static void slideInFromRight(Node node, Duration duration, Runnable onFinished) {
        if (node == null) {
            return;
        }

        double startX = node.getLayoutBounds().getWidth();

        TranslateTransition slide = new TranslateTransition(duration, node);
        slide.setFromX(startX);
        slide.setToX(0);

        if (onFinished != null) {
            slide.setOnFinished(e -> onFinished.run());
        }

        slide.play();
    }

    /**
     * Slides a node out to the left.
     *
     * @param node the node to slide out
     */
    public static void slideOutToLeft(Node node) {
        slideOutToLeft(node, DURATION_NORMAL, null);
    }

    /**
     * Slides a node out to the left with custom duration.
     *
     * @param node the node to slide out
     * @param duration the slide duration
     * @param onFinished callback when animation finishes (can be null)
     */
    public static void slideOutToLeft(Node node, Duration duration, Runnable onFinished) {
        if (node == null) {
            return;
        }

        double endX = -node.getLayoutBounds().getWidth();

        TranslateTransition slide = new TranslateTransition(duration, node);
        slide.setFromX(0);
        slide.setToX(endX);

        if (onFinished != null) {
            slide.setOnFinished(e -> onFinished.run());
        }

        slide.play();
    }

    /**
     * Creates a scale transition (zoom in/out effect).
     *
     * @param node the node to scale
     * @param fromScale starting scale (1.0 = normal size)
     * @param toScale ending scale (1.0 = normal size)
     * @param duration animation duration
     * @param onFinished callback when animation finishes (can be null)
     */
    public static void scale(Node node, double fromScale, double toScale, Duration duration, Runnable onFinished) {
        if (node == null) {
            return;
        }

        ScaleTransition scale = new ScaleTransition(duration, node);
        scale.setFromX(fromScale);
        scale.setFromY(fromScale);
        scale.setToX(toScale);
        scale.setToY(toScale);

        if (onFinished != null) {
            scale.setOnFinished(e -> onFinished.run());
        }

        scale.play();
    }

    /**
     * Shows a loading indicator centered on a pane.
     * The loading indicator is added as a child of the pane.
     *
     * @param pane the pane to show the loading indicator on
     * @return the created ProgressIndicator (to later hide it)
     */
    public static ProgressIndicator showLoading(StackPane pane) {
        if (pane == null) {
            return null;
        }

        ProgressIndicator loading = new ProgressIndicator();
        loading.setMaxSize(60, 60);

        // Dim the background slightly
        pane.setStyle("-fx-background-color: rgba(0, 0, 0, 0.3);");

        pane.getChildren().add(loading);

        // Fade in the loading indicator
        fadeIn(loading, DURATION_FAST, null);

        return loading;
    }

    /**
     * Hides a loading indicator and removes it from the pane.
     *
     * @param pane the pane containing the loading indicator
     * @param loading the loading indicator to hide
     */
    public static void hideLoading(StackPane pane, ProgressIndicator loading) {
        if (pane == null || loading == null) {
            return;
        }

        // Fade out and remove
        fadeOut(loading, DURATION_FAST, () -> {
            pane.getChildren().remove(loading);
            pane.setStyle(""); // Remove background dim
        });
    }

    /**
     * Creates a smooth transition for language changes.
     * Fades out, executes the reload action, then fades back in.
     *
     * @param node the node to transition
     * @param reloadAction the action to execute during the transition (e.g., reload HTML)
     */
    public static void languageChangeTransition(Node node, Runnable reloadAction) {
        if (node == null || reloadAction == null) {
            return;
        }

        // Fade out
        fadeOut(node, DURATION_NORMAL, () -> {
            // Execute reload while invisible
            reloadAction.run();

            // Fade back in (with small delay to ensure reload is complete)
            Timeline delay = new Timeline(new KeyFrame(Duration.millis(100), e -> {
                fadeIn(node, DURATION_NORMAL, null);
            }));
            delay.play();
        });
    }

    /**
     * Creates a smooth transition for theme changes.
     * Similar to language change but with a faster transition.
     *
     * @param node the node to transition
     * @param themeAction the action to execute during the transition (e.g., apply theme)
     */
    public static void themeChangeTransition(Node node, Runnable themeAction) {
        if (node == null || themeAction == null) {
            return;
        }

        // Quick fade for theme changes (feels more responsive)
        fadeOut(node, DURATION_FAST, () -> {
            themeAction.run();
            fadeIn(node, DURATION_FAST, null);
        });
    }

    /**
     * Creates a pulsing animation (useful for notifications or attention).
     *
     * @param node the node to pulse
     * @param cycles number of pulse cycles (use Animation.INDEFINITE for continuous)
     */
    public static Animation pulse(Node node, int cycles) {
        if (node == null) {
            return null;
        }

        FadeTransition pulse = new FadeTransition(Duration.millis(800), node);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.5);
        pulse.setCycleCount(cycles);
        pulse.setAutoReverse(true);

        pulse.play();
        return pulse;
    }

    /**
     * Creates a shake animation (useful for error feedback).
     *
     * @param node the node to shake
     */
    public static void shake(Node node) {
        if (node == null) {
            return;
        }

        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);

        shake.setOnFinished(e -> node.setTranslateX(0));

        shake.play();
    }

    /**
     * Applies CSS transition styles to HTML elements via JavaScript.
     * This should be called on a WebEngine to enable smooth CSS transitions.
     *
     * @param webEngine the WebEngine to apply transitions to
     */
    public static void applyHTMLTransitions(javafx.scene.web.WebEngine webEngine) {
        if (webEngine == null) {
            return;
        }

        try {
            String script =
                "var style = document.createElement('style');" +
                "style.textContent = '" +
                "  * {" +
                "    transition: background 300ms ease, color 300ms ease, opacity 300ms ease;" +
                "  }" +
                "  .fade-in {" +
                "    animation: fadeIn 300ms ease;" +
                "  }" +
                "  @keyframes fadeIn {" +
                "    from { opacity: 0; }" +
                "    to { opacity: 1; }" +
                "  }" +
                "';" +
                "document.head.appendChild(style);";

            webEngine.executeScript(script);
        } catch (Exception e) {
            // Ignore if HTML not loaded yet
        }
    }
}
