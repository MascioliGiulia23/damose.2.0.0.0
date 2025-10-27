package com.rometransit.ui.waypoint;

import com.rometransit.model.entity.Stop;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Waypoint per disegnare pallini blu per le fermate di una linea specifica
 * I pallini blu rappresentano le fermate lungo il percorso di una linea/autobus
 */
public class StopMarkerWaypoint extends Waypoint {
    private final Stop stop;
    private final double lat;
    private final double lon;
    private final Color markerColor;
    private final boolean showLabel;

    /**
     * Costruttore con colore blu di default
     */
    public StopMarkerWaypoint(Stop stop) {
        this(stop, Color.web("#3498db"), true); // Blu di default
    }

    /**
     * Costruttore con colore personalizzato
     */
    public StopMarkerWaypoint(Stop stop, Color markerColor, boolean showLabel) {
        super("stop_marker_" + stop.getStopId());
        this.stop = stop;
        this.lat = stop.getStopLat();
        this.lon = stop.getStopLon();
        this.markerColor = markerColor;
        this.showLabel = showLabel;
    }

    @Override
    public void render(GraphicsContext gc, int zoom, GeoToScreenConverter geoToScreenConverter) {
        if (!visible) {
            return;
        }

        // Converti coordinate geografiche in coordinate schermo
        double[] screenCoords = geoToScreenConverter.convert(lat, lon);
        double screenX = screenCoords[0];
        double screenY = screenCoords[1];

        // Dimensione del pallino in base allo zoom
        double radius = getMarkerRadius(zoom);

        // Disegna alone esterno (glow effect) per migliore visibilità
        gc.setFill(markerColor.deriveColor(0, 1, 1, 0.3));
        gc.fillOval(screenX - radius * 1.8, screenY - radius * 1.8, radius * 3.6, radius * 3.6);

        // Disegna il pallino principale (blu o colore personalizzato)
        gc.setFill(markerColor);
        gc.fillOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

        // Disegna bordo nero per contrasto
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(1.5);
        gc.strokeOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

        // Disegna cerchio bianco interno per effetto "pin"
        double innerRadius = radius * 0.35;
        gc.setFill(Color.WHITE);
        gc.fillOval(screenX - innerRadius, screenY - innerRadius, innerRadius * 2, innerRadius * 2);

        // Disegna il nome della fermata a zoom elevati
        if (showLabel && zoom >= 14) {
            drawStopLabel(gc, screenX, screenY, radius);
        }
    }

    private void drawStopLabel(GraphicsContext gc, double screenX, double screenY, double radius) {
        String stopName = stop.getStopName();
        if (stopName == null || stopName.isEmpty()) return;

        // Tronca nomi troppo lunghi
        if (stopName.length() > 25) {
            stopName = stopName.substring(0, 22) + "...";
        }

        // Impostazioni testo
        gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));
        double textWidth = stopName.length() * 6;
        double textX = screenX + radius + 6;
        double textY = screenY + 4;

        // Disegna sfondo bianco con bordo per il testo
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(textX - 2, textY - 11, textWidth + 4, 14, 3, 3);

        gc.setStroke(markerColor.darker());
        gc.setLineWidth(1);
        gc.strokeRoundRect(textX - 2, textY - 11, textWidth + 4, 14, 3, 3);

        // Disegna il testo
        gc.setFill(Color.BLACK);
        gc.fillText(stopName, textX, textY);
    }

    private double getMarkerRadius(int zoom) {
        // Dimensione adattiva in base allo zoom
        if (zoom >= 16) return 7;
        if (zoom >= 14) return 6;
        if (zoom >= 12) return 5;
        return 4;
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

    public Color getMarkerColor() {
        return markerColor;
    }
}
