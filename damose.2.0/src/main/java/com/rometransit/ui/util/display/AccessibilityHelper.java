package com.rometransit.ui.util.display;

import com.rometransit.util.language.LanguageManager;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Utility class for implementing accessibility (a11y) features in JavaFX controls.
 *
 * <p>This class helps make the application more accessible by providing:
 * <ul>
 *   <li>Screen reader support (accessible text and help)</li>
 *   <li>Keyboard navigation and shortcuts</li>
 *   <li>Focus indicators and management</li>
 *   <li>High contrast mode support</li>
 *   <li>ARIA-like roles for controls</li>
 * </ul>
 *
 * <p><b>Usage examples:</b>
 * <pre>
 * // Setup button with accessibility
 * Button loginButton = new Button("Login");
 * AccessibilityHelper.setupButton(loginButton, "login.button", "login.button.help");
 *
 * // Setup text field
 * TextField usernameField = new TextField();
 * AccessibilityHelper.setupTextField(usernameField, "login.username", "login.username.help");
 *
 * // Add keyboard shortcut
 * AccessibilityHelper.addKeyboardShortcut(scene, KeyCode.L, loginButton::fire, "Ctrl+L");
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class AccessibilityHelper {

    private static final LanguageManager languageManager = LanguageManager.getInstance();

    /**
     * Sets up accessibility for a Button control.
     *
     * @param button the button to setup
     * @param textKey language key for button text
     * @param helpKey language key for accessible help text
     */
    public static void setupButton(Button button, String textKey, String helpKey) {
        if (button == null) {
            return;
        }

        // Set accessible text (for screen readers)
        String text = languageManager.getString(textKey);
        button.setAccessibleText(text);

        // Set accessible help (additional context)
        if (helpKey != null && !helpKey.isEmpty()) {
            String help = languageManager.getString(helpKey);
            button.setAccessibleHelp(help);
        }

        // Set accessible role
        button.setAccessibleRoleDescription("button");

        // Ensure focusable for keyboard navigation
        button.setFocusTraversable(true);

        // Add visual focus indicator
        addFocusIndicator(button);
    }

    /**
     * Sets up accessibility for a TextField control.
     *
     * @param textField the text field to setup
     * @param labelKey language key for field label/prompt
     * @param helpKey language key for accessible help text
     */
    public static void setupTextField(TextField textField, String labelKey, String helpKey) {
        if (textField == null) {
            return;
        }

        // Set accessible text
        String label = languageManager.getString(labelKey);
        textField.setAccessibleText(label);
        textField.setPromptText(label);

        // Set accessible help
        if (helpKey != null && !helpKey.isEmpty()) {
            String help = languageManager.getString(helpKey);
            textField.setAccessibleHelp(help);
        }

        // Set accessible role
        textField.setAccessibleRoleDescription("text input");

        // Ensure focusable
        textField.setFocusTraversable(true);

        // Add focus indicator
        addFocusIndicator(textField);
    }

    /**
     * Sets up accessibility for a PasswordField control.
     *
     * @param passwordField the password field to setup
     * @param labelKey language key for field label/prompt
     * @param helpKey language key for accessible help text
     */
    public static void setupPasswordField(PasswordField passwordField, String labelKey, String helpKey) {
        if (passwordField == null) {
            return;
        }

        // Set accessible text
        String label = languageManager.getString(labelKey);
        passwordField.setAccessibleText(label);
        passwordField.setPromptText(label);

        // Set accessible help
        if (helpKey != null && !helpKey.isEmpty()) {
            String help = languageManager.getString(helpKey);
            passwordField.setAccessibleHelp(help);
        }

        // Set accessible role
        passwordField.setAccessibleRoleDescription("password input");

        // Ensure focusable
        passwordField.setFocusTraversable(true);

        // Add focus indicator
        addFocusIndicator(passwordField);
    }

    /**
     * Sets up accessibility for a Label control.
     *
     * @param label the label to setup
     * @param textKey language key for label text
     */
    public static void setupLabel(Label label, String textKey) {
        if (label == null) {
            return;
        }

        String text = languageManager.getString(textKey);
        label.setAccessibleText(text);
        label.setAccessibleRoleDescription("label");
    }

    /**
     * Sets up accessibility for a CheckBox control.
     *
     * @param checkBox the checkbox to setup
     * @param textKey language key for checkbox text
     * @param helpKey language key for accessible help text
     */
    public static void setupCheckBox(CheckBox checkBox, String textKey, String helpKey) {
        if (checkBox == null) {
            return;
        }

        String text = languageManager.getString(textKey);
        checkBox.setAccessibleText(text);

        if (helpKey != null && !helpKey.isEmpty()) {
            String help = languageManager.getString(helpKey);
            checkBox.setAccessibleHelp(help);
        }

        checkBox.setAccessibleRoleDescription("checkbox");
        checkBox.setFocusTraversable(true);

        addFocusIndicator(checkBox);
    }

    /**
     * Sets up accessibility for a ListView control.
     *
     * @param listView the list view to setup
     * @param labelKey language key for list label
     * @param helpKey language key for accessible help text
     */
    public static void setupListView(ListView<?> listView, String labelKey, String helpKey) {
        if (listView == null) {
            return;
        }

        String label = languageManager.getString(labelKey);
        listView.setAccessibleText(label);

        if (helpKey != null && !helpKey.isEmpty()) {
            String help = languageManager.getString(helpKey);
            listView.setAccessibleHelp(help);
        }

        listView.setAccessibleRoleDescription("list");
        listView.setFocusTraversable(true);

        addFocusIndicator(listView);
    }

    /**
     * Adds a visible focus indicator to a control.
     * This makes keyboard navigation more visible.
     *
     * @param node the node to add focus indicator to
     */
    public static void addFocusIndicator(Node node) {
        if (node == null) {
            return;
        }

        node.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (isNowFocused) {
                // Add focus indicator style
                String currentStyle = node.getStyle();
                if (!currentStyle.contains("-fx-border-color")) {
                    node.setStyle(currentStyle + "; -fx-border-color: #2196F3; -fx-border-width: 2px;");
                }
            } else {
                // Remove focus indicator
                String currentStyle = node.getStyle();
                currentStyle = currentStyle.replace("; -fx-border-color: #2196F3; -fx-border-width: 2px;", "");
                node.setStyle(currentStyle);
            }
        });
    }

    /**
     * Adds a keyboard shortcut to trigger an action.
     *
     * @param scene the scene to add the shortcut to
     * @param keyCode the key code
     * @param action the action to execute
     * @param description description of the shortcut (for accessibility)
     */
    public static void addKeyboardShortcut(javafx.scene.Scene scene, KeyCode keyCode, Runnable action, String description) {
        if (scene == null || keyCode == null || action == null) {
            return;
        }

        KeyCombination keyCombination = new KeyCodeCombination(keyCode, KeyCombination.CONTROL_DOWN);

        scene.getAccelerators().put(keyCombination, action::run);
    }

    /**
     * Adds Enter key activation for a button.
     * When the button is focused, Enter key will trigger it.
     *
     * @param button the button to setup
     */
    public static void addEnterKeyActivation(Button button) {
        if (button == null) {
            return;
        }

        button.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                button.fire();
            }
        });
    }

    /**
     * Sets up Tab navigation order for a group of controls.
     *
     * @param controls the controls in the desired tab order
     */
    public static void setupTabOrder(Node... controls) {
        if (controls == null || controls.length == 0) {
            return;
        }

        for (int i = 0; i < controls.length; i++) {
            Node control = controls[i];

            if (control != null) {
                control.setFocusTraversable(true);

                // Set tab index (if supported)
                // Note: JavaFX doesn't have explicit tab index, but setting focusTraversable
                // ensures the control is in the tab order based on scene graph order
            }
        }
    }

    /**
     * Requests focus on a control safely.
     * Ensures the control is visible and focusable before requesting focus.
     *
     * @param control the control to focus
     */
    public static void requestFocus(Node control) {
        if (control == null) {
            return;
        }

        if (control.isVisible() && control.isFocusTraversable()) {
            javafx.application.Platform.runLater(control::requestFocus);
        }
    }

    /**
     * Checks if high contrast mode should be enabled.
     * This can be based on system settings or user preferences.
     *
     * @return true if high contrast mode should be enabled
     */
    public static boolean isHighContrastMode() {
        // Check system property or user preference
        return Boolean.getBoolean("high.contrast.mode");
    }

    /**
     * Applies high contrast styles to a node.
     * Uses higher contrast colors for better visibility.
     *
     * @param node the node to apply high contrast styles to
     */
    public static void applyHighContrastStyles(Node node) {
        if (node == null || !isHighContrastMode()) {
            return;
        }

        // Apply high contrast styles
        String highContrastStyle =
            "-fx-text-fill: #000000; " +
            "-fx-background-color: #FFFFFF; " +
            "-fx-border-color: #000000; " +
            "-fx-border-width: 2px;";

        String currentStyle = node.getStyle();
        node.setStyle(currentStyle + "; " + highContrastStyle);
    }

    /**
     * Announces a message to screen readers.
     * This creates an invisible Label with the message that screen readers will pick up.
     *
     * @param message the message to announce
     * @param parent the parent pane to add the announcement to
     */
    public static void announceToScreenReader(String message, javafx.scene.layout.Pane parent) {
        if (message == null || parent == null) {
            return;
        }

        // Create invisible label for screen reader announcement
        Label announcement = new Label(message);
        announcement.setVisible(false);
        announcement.setManaged(false);

        parent.getChildren().add(announcement);

        // Remove after a short delay
        javafx.animation.Timeline timeline = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(2),
                event -> parent.getChildren().remove(announcement)
            )
        );
        timeline.play();
    }

    /**
     * Sets up error feedback with accessibility support.
     * Shows visual error and announces it to screen readers.
     *
     * @param control the control with the error
     * @param errorMessage the error message
     * @param parent the parent pane (for screen reader announcement)
     */
    public static void showAccessibleError(Control control, String errorMessage, javafx.scene.layout.Pane parent) {
        if (control == null || errorMessage == null) {
            return;
        }

        // Visual error indicator
        control.setStyle(control.getStyle() + "; -fx-border-color: #d32f2f; -fx-border-width: 2px;");

        // Tooltip for visual users
        Tooltip errorTooltip = new Tooltip(errorMessage);
        control.setTooltip(errorTooltip);

        // Screen reader announcement
        if (parent != null) {
            announceToScreenReader("Error: " + errorMessage, parent);
        }

        // Set accessible help to the error
        control.setAccessibleHelp(errorMessage);
    }

    /**
     * Clears error feedback from a control.
     *
     * @param control the control to clear error from
     */
    public static void clearAccessibleError(Control control) {
        if (control == null) {
            return;
        }

        // Remove error border
        String style = control.getStyle();
        style = style.replace("; -fx-border-color: #d32f2f; -fx-border-width: 2px;", "");
        control.setStyle(style);

        // Remove tooltip
        control.setTooltip(null);

        // Clear accessible help
        control.setAccessibleHelp(null);
    }
}
