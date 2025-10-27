package com.rometransit.ui.waypoint;

import com.rometransit.model.entity.Stop;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Waypoint for rendering a selected stop on the map
 * Stays fixed at geographic coordinates during map pan/zoom
 */
public class StopWaypoint extends Waypoint {
    private final Stop stop;
    private final double lat;
    private final double lon;

    public StopWaypoint(Stop stop) {
        super(stop.getStopId());
        this.stop = stop;
        this.lat = stop.getStopLat();
        this.lon = stop.getStopLon();
    }

    @Override
    public void render(GraphicsContext gc, int zoom, GeoToScreenConverter geoToScreenConverter) {
        if (!visible) return;

        // Convert geographic coordinates to screen coordinates
        double[] screenCoords = geoToScreenConverter.convert(lat, lon);
        double screenX = screenCoords[0];
        double screenY = screenCoords[1];

        // Get stop color based on type
        Color stopColor = getStopColor();
        double radius = getStopRadius(zoom);

        // Draw outer glow for selected stop
        gc.setFill(stopColor.deriveColor(0, 1, 1, 0.3));
        gc.fillOval(screenX - radius * 2, screenY - radius * 2, radius * 4, radius * 4);

        // Draw stop marker (larger than normal stops)
        gc.setFill(stopColor);
        gc.fillOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

        // Draw black border
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

        // Draw white inner circle for contrast
        gc.setFill(Color.WHITE);
        gc.fillOval(screenX - radius * 0.4, screenY - radius * 0.4, radius * 0.8, radius * 0.8);

        // Draw stop name with background
        if (zoom >= 12) {
            String stopName = stop.getStopName();
            if (stopName != null) {
                // Truncate long names
                if (stopName.length() > 30) {
                    stopName = stopName.substring(0, 27) + "...";
                }

                // Draw text background
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 11));
                double textWidth = stopName.length() * 6.5;
                double textX = screenX + radius + 5;
                double textY = screenY - radius - 5;

                gc.setFill(Color.WHITE);
                gc.fillRoundRect(textX - 3, textY - 13, textWidth + 6, 18, 4, 4);

                gc.setStroke(Color.BLACK);
                gc.setLineWidth(1);
                gc.strokeRoundRect(textX - 3, textY - 13, textWidth + 6, 18, 4, 4);

                // Draw text
                gc.setFill(Color.BLACK);
                gc.fillText(stopName, textX, textY);
            }
        }
    }

    private Color getStopColor() {
        if (stop.getStopName() == null) return Color.web("#e74c3c");

        String stopName = stop.getStopName().toUpperCase();

        // Metro lines
        if (stopName.contains("(MA)") || stopName.contains("METRO A")) {
            return Color.web("#f39c12"); // Orange for Metro A
        } else if (stopName.contains("(MB)") || stopName.contains("METRO B") || stopName.contains("(MB1)")) {
            return Color.web("#3498db"); // Blue for Metro B/B1
        } else if (stopName.contains("(MC)") || stopName.contains("METRO C")) {
            return Color.web("#27ae60"); // Green for Metro C
        }

        // Trams
        if (stopName.contains("TRAM") || stopName.contains("(TR)")) {
            return Color.web("#9b59b6"); // Purple for trams
        }

        // Trains
        if (stopName.contains("(FL)") || stopName.contains("STAZIONE") || stopName.contains("(FS)")) {
            return Color.web("#34495e"); // Dark blue for trains
        }

        return Color.web("#e74c3c"); // Red for bus stops
    }

    private double getStopRadius(int zoom) {
        // Selected stops are larger
        double baseRadius = Math.max(8, zoom - 4);

        if (stop.getStopName() != null) {
            String stopName = stop.getStopName().toUpperCase();

            // Metro stations are larger
            if (stopName.contains("(MA)") || stopName.contains("(MB)") || stopName.contains("(MC)") ||
                stopName.contains("METRO")) {
                return baseRadius * 1.5;
            }

            // Train stations are also larger
            if (stopName.contains("STAZIONE") || stopName.contains("(FL)") || stopName.contains("(FS)")) {
                return baseRadius * 1.3;
            }
        }

        return baseRadius;
    }

    public Stop getStop() {
        return stop;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}
