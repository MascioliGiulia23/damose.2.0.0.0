package com.rometransit.ui.util.display;

import com.rometransit.ui.frontend.settings.ThemeManager;

/**
 * Utility class for standardized JavaFX control styling.
 *
 * <p>This class centralizes CSS inline styles for JavaFX controls,
 * ensuring consistency across the application and making it easy
 * to update styles globally.
 *
 * <p><b>Usage examples:</b>
 * <pre>
 * // Style a button with accent color
 * Button loginButton = new Button("Login");
 * loginButton.setStyle(StyleHelper.getAccentButtonStyle());
 *
 * // Style a text field
 * TextField usernameField = new TextField();
 * usernameField.setStyle(StyleHelper.getTextFieldStyle());
 *
 * // Style a label
 * Label titleLabel = new Label("Welcome");
 * titleLabel.setStyle(StyleHelper.getLabelStyle(24, true));
 * </pre>
 *
 * @author Damose Team
 * @version 2.0
 */
public class StyleHelper {

    // Common colors
    private static final String COLOR_WHITE = "#FFFFFF";
    private static final String COLOR_BLACK = "#000000";
    private static final String COLOR_GRAY_LIGHT = "#F5F5F5";
    private static final String COLOR_GRAY = "#9E9E9E";
    private static final String COLOR_GRAY_DARK = "#424242";
    private static final String COLOR_ERROR = "#d32f2f";
    private static final String COLOR_SUCCESS = "#388E3C";
    private static final String COLOR_WARNING = "#F57C00";

    // Common sizes
    private static final int BORDER_RADIUS = 8;
    private static final int PADDING = 10;

    /**
     * Gets the style for an accent-colored button (primary action).
     * Uses the current theme accent color.
     *
     * @return CSS style string
     */
    public static String getAccentButtonStyle() {
        String accentColor = ThemeManager.getAccent();
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: %d; " +
            "-fx-cursor: hand;",
            accentColor, COLOR_WHITE, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for an accent-colored button with custom accent color.
     *
     * @param accentColor the custom accent color (hex format)
     * @return CSS style string
     */
    public static String getAccentButtonStyle(String accentColor) {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: %d; " +
            "-fx-cursor: hand;",
            accentColor, COLOR_WHITE, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for a secondary button (less prominent).
     *
     * @return CSS style string
     */
    public static String getSecondaryButtonStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: 10 20; " +
            "-fx-background-radius: %d; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1px; " +
            "-fx-cursor: hand;",
            COLOR_WHITE, COLOR_GRAY_DARK, BORDER_RADIUS, COLOR_GRAY
        );
    }

    /**
     * Gets the style for a text link button (looks like a hyperlink).
     *
     * @return CSS style string
     */
    public static String getLinkButtonStyle() {
        String accentColor = ThemeManager.getAccent();
        return String.format(
            "-fx-background-color: transparent; " +
            "-fx-text-fill: %s; " +
            "-fx-underline: true; " +
            "-fx-font-size: 14px; " +
            "-fx-cursor: hand;",
            accentColor
        );
    }

    /**
     * Gets the style for a TextField.
     *
     * @return CSS style string
     */
    public static String getTextFieldStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-padding: %d; " +
            "-fx-background-radius: %d; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1px; " +
            "-fx-border-radius: %d;",
            COLOR_WHITE, COLOR_BLACK, PADDING, BORDER_RADIUS, COLOR_GRAY, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for a PasswordField.
     * Same as TextField style.
     *
     * @return CSS style string
     */
    public static String getPasswordFieldStyle() {
        return getTextFieldStyle();
    }

    /**
     * Gets the style for a Label with custom font size and weight.
     *
     * @param fontSize the font size in pixels
     * @param bold whether the label should be bold
     * @return CSS style string
     */
    public static String getLabelStyle(int fontSize, boolean bold) {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: %dpx; " +
            "%s",
            COLOR_BLACK, fontSize, bold ? "-fx-font-weight: bold;" : ""
        );
    }

    /**
     * Gets the style for a title Label (large, bold).
     *
     * @return CSS style string
     */
    public static String getTitleLabelStyle() {
        return getLabelStyle(24, true);
    }

    /**
     * Gets the style for a subtitle Label (medium, bold).
     *
     * @return CSS style string
     */
    public static String getSubtitleLabelStyle() {
        return getLabelStyle(18, true);
    }

    /**
     * Gets the style for a body Label (normal size).
     *
     * @return CSS style string
     */
    public static String getBodyLabelStyle() {
        return getLabelStyle(14, false);
    }

    /**
     * Gets the style for a small Label (caption text).
     *
     * @return CSS style string
     */
    public static String getCaptionLabelStyle() {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 12px;",
            COLOR_GRAY
        );
    }

    /**
     * Gets the style for a ListView.
     *
     * @return CSS style string
     */
    public static String getListViewStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-border-color: %s; " +
            "-fx-border-width: 1px; " +
            "-fx-background-radius: %d; " +
            "-fx-border-radius: %d;",
            COLOR_WHITE, COLOR_GRAY, BORDER_RADIUS, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for a Panel/Pane container.
     *
     * @return CSS style string
     */
    public static String getPanelStyle() {
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-padding: %d; " +
            "-fx-background-radius: %d; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);",
            COLOR_WHITE, PADDING, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for an accent-colored Panel.
     *
     * @return CSS style string
     */
    public static String getAccentPanelStyle() {
        String accentColor = ThemeManager.getAccent();
        return String.format(
            "-fx-background-color: %s; " +
            "-fx-padding: %d; " +
            "-fx-background-radius: %d;",
            accentColor, PADDING, BORDER_RADIUS
        );
    }

    /**
     * Gets the style for an error message.
     *
     * @return CSS style string
     */
    public static String getErrorStyle() {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold;",
            COLOR_ERROR
        );
    }

    /**
     * Gets the style for a success message.
     *
     * @return CSS style string
     */
    public static String getSuccessStyle() {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold;",
            COLOR_SUCCESS
        );
    }

    /**
     * Gets the style for a warning message.
     *
     * @return CSS style string
     */
    public static String getWarningStyle() {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px; " +
            "-fx-font-weight: bold;",
            COLOR_WARNING
        );
    }

    /**
     * Gets the style for a CheckBox.
     *
     * @return CSS style string
     */
    public static String getCheckBoxStyle() {
        return String.format(
            "-fx-text-fill: %s; " +
            "-fx-font-size: 14px;",
            COLOR_BLACK
        );
    }

    /**
     * Gets the style for a disabled control.
     *
     * @return CSS style string
     */
    public static String getDisabledStyle() {
        return String.format(
            "-fx-opacity: 0.5; " +
            "-fx-cursor: not-allowed;"
        );
    }

    /**
     * Combines multiple style strings into one.
     *
     * @param styles the styles to combine
     * @return combined CSS style string
     */
    public static String combineStyles(String... styles) {
        StringBuilder combined = new StringBuilder();

        for (String style : styles) {
            if (style != null && !style.isEmpty()) {
                if (combined.length() > 0 && !combined.toString().endsWith(";")) {
                    combined.append("; ");
                }
                combined.append(style);
            }
        }

        return combined.toString();
    }

    /**
     * Adds hover effect to a button.
     * Note: This requires setting onMouseEntered and onMouseExited handlers.
     *
     * @param button the button to add hover effect to
     * @param normalStyle the normal button style
     * @param hoverStyle the hover button style
     */
    public static void addButtonHoverEffect(javafx.scene.control.Button button, String normalStyle, String hoverStyle) {
        if (button == null) {
            return;
        }

        button.setStyle(normalStyle);

        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(normalStyle));
    }

    /**
     * Gets a darker version of a color (for hover effects).
     *
     * @param hexColor the original color in hex format
     * @param amount the amount to darken (0.0 to 1.0)
     * @return darkened color in hex format
     */
    public static String darkenColor(String hexColor, double amount) {
        if (hexColor == null || hexColor.isEmpty()) {
            return hexColor;
        }

        // Remove # if present
        hexColor = hexColor.replace("#", "");

        try {
            int rgb = Integer.parseInt(hexColor, 16);

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            r = (int) (r * (1 - amount));
            g = (int) (g * (1 - amount));
            b = (int) (b * (1 - amount));

            return String.format("#%02X%02X%02X", r, g, b);

        } catch (NumberFormatException e) {
            return hexColor;
        }
    }

    /**
     * Gets a lighter version of a color.
     *
     * @param hexColor the original color in hex format
     * @param amount the amount to lighten (0.0 to 1.0)
     * @return lightened color in hex format
     */
    public static String lightenColor(String hexColor, double amount) {
        if (hexColor == null || hexColor.isEmpty()) {
            return hexColor;
        }

        hexColor = hexColor.replace("#", "");

        try {
            int rgb = Integer.parseInt(hexColor, 16);

            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            r = Math.min(255, (int) (r + (255 - r) * amount));
            g = Math.min(255, (int) (g + (255 - g) * amount));
            b = Math.min(255, (int) (b + (255 - b) * amount));

            return String.format("#%02X%02X%02X", r, g, b);

        } catch (NumberFormatException e) {
            return hexColor;
        }
    }
}
