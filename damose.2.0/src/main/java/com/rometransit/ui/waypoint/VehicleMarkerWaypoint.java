package com.rometransit.ui.waypoint;

import com.rometransit.model.dto.VehiclePosition;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Waypoint che rappresenta un veicolo (autobus) sulla mappa
 * Visualizzato come pallino verde con direzione e info
 */
public class VehicleMarkerWaypoint extends Waypoint {

    private VehiclePosition currentPosition;
    private VehiclePosition previousPosition;
    private long transitionStartTime;
    private static final long TRANSITION_DURATION = 30000; // 30 secondi

    private final Color vehicleColor;
    private final double baseRadius;
    private boolean showLabel;
    private boolean showDirection;

    public VehicleMarkerWaypoint(String id, VehiclePosition position) {
        this(id, position, Color.web("#27ae60"), 8.0, true, true); // Verde default
    }

    public VehicleMarkerWaypoint(String id, VehiclePosition position,
                                Color color, double radius,
                                boolean showLabel, boolean showDirection) {
        super(id);
        this.currentPosition = position;
        this.previousPosition = null;
        this.transitionStartTime = System.currentTimeMillis();
        this.vehicleColor = color;
        this.baseRadius = radius;
        this.showLabel = showLabel;
        this.showDirection = showDirection;
    }

    @Override
    public void render(GraphicsContext gc, int zoom,
                      GeoToScreenConverter geoToScreenConverter) {
        if (!visible) {
            System.out.println("⚠️  [VehicleMarkerWaypoint] SKIPPED rendering - waypoint NOT visible");
            return;
        }

        if (currentPosition == null) {
            System.out.println("⚠️  [VehicleMarkerWaypoint] currentPosition is NULL");
            return;
        }

        // Usa posizione interpolata per movimento smooth
        double[] interpolatedPos = getInterpolatedPosition();
        double[] screenCoords = geoToScreenConverter.convert(interpolatedPos[0], interpolatedPos[1]);

        double screenX = screenCoords[0];
        double screenY = screenCoords[1];

        System.out.println("🚍 [VehicleMarkerWaypoint] Rendering veicolo " + currentPosition.getVehicleId() +
                         " routeId=" + currentPosition.getRouteId() +
                         " at geo[" + interpolatedPos[0] + "," + interpolatedPos[1] + "]" +
                         " screen[" + screenX + "," + screenY + "]" +
                         " zoom=" + zoom);

        // Calcola raggio in base allo zoom
        double radius = baseRadius + Math.max(0, (zoom - 12) * 0.5);

        // Disegna pallino verde (veicolo)
        gc.setFill(vehicleColor);
        gc.fillOval(screenX - radius, screenY - radius, radius * 2, radius * 2);

        // Bordo nero per visibilità
        // Se simulato, usa linea tratteggiata
        if (currentPosition.isSimulated()) {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.setLineDashes(5, 5); // Linea tratteggiata
            gc.strokeOval(screenX - radius, screenY - radius, radius * 2, radius * 2);
            gc.setLineDashes(null); // Reset a linea continua
        } else {
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(2);
            gc.strokeOval(screenX - radius, screenY - radius, radius * 2, radius * 2);
        }

        // Bordo bianco interno per effetto "glow"
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeOval(screenX - radius + 1, screenY - radius + 1,
                     (radius - 1) * 2, (radius - 1) * 2);

        // Freccia direzionale (bearing)
        if (showDirection && currentPosition.getBearing() > 0) {
            drawDirectionArrow(gc, screenX, screenY, radius,
                             currentPosition.getBearing());
        }

        // Label con numero linea
        if (showLabel && zoom >= 14) {
            String label = currentPosition.getRouteShortName();
            if (label != null && !label.isEmpty()) {
                gc.setFill(Color.BLACK);
                gc.setFont(Font.font("Arial", FontWeight.BOLD, 10));

                // Background bianco per leggibilità
                double textWidth = label.length() * 6;
                gc.setFill(Color.WHITE);
                gc.fillRect(screenX - textWidth/2, screenY + radius + 2,
                          textWidth, 14);

                gc.setFill(Color.BLACK);
                gc.fillText(label, screenX - textWidth/2 + 2,
                          screenY + radius + 13);
            }
        }
    }

    /**
     * Disegna freccia che indica la direzione del veicolo
     */
    private void drawDirectionArrow(GraphicsContext gc, double x, double y,
                                    double radius, double bearing) {
        double arrowLength = radius * 1.5;
        double bearingRad = Math.toRadians(bearing);

        // Punta della freccia
        double tipX = x + Math.sin(bearingRad) * arrowLength;
        double tipY = y - Math.cos(bearingRad) * arrowLength;

        // Disegna linea
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2.5);
        gc.strokeLine(x, y, tipX, tipY);

        // Disegna punta freccia (triangolo)
        double arrowSize = 5;
        double angle1 = bearingRad - Math.PI / 6;
        double angle2 = bearingRad + Math.PI / 6;

        double x1 = tipX - Math.sin(angle1) * arrowSize;
        double y1 = tipY + Math.cos(angle1) * arrowSize;
        double x2 = tipX - Math.sin(angle2) * arrowSize;
        double y2 = tipY + Math.cos(angle2) * arrowSize;

        gc.setFill(Color.BLACK);
        gc.fillPolygon(new double[]{tipX, x1, x2},
                      new double[]{tipY, y1, y2}, 3);
    }

    /**
     * Calcola posizione interpolata per rendering smooth
     * Interpola linearmente tra posizione precedente e corrente
     */
    private double[] getInterpolatedPosition() {
        if (previousPosition == null || currentPosition == null) {
            return new double[]{
                currentPosition.getLatitude(),
                currentPosition.getLongitude()
            };
        }

        long elapsed = System.currentTimeMillis() - transitionStartTime;
        double progress = Math.min(1.0, elapsed / (double) TRANSITION_DURATION);

        // Interpolazione lineare
        double lat = previousPosition.getLatitude() +
            (currentPosition.getLatitude() - previousPosition.getLatitude()) * progress;
        double lon = previousPosition.getLongitude() +
            (currentPosition.getLongitude() - previousPosition.getLongitude()) * progress;

        return new double[]{lat, lon};
    }

    /**
     * Aggiorna la posizione del veicolo (chiamato ogni 30s)
     */
    public void updatePosition(VehiclePosition newPosition) {
        this.previousPosition = this.currentPosition;
        this.currentPosition = newPosition;
        this.transitionStartTime = System.currentTimeMillis();
    }

    /**
     * Verifica se coordinate schermo sono dentro il veicolo (per click detection)
     */
    public boolean containsPoint(double screenX, double screenY,
                                GeoToScreenConverter geoToScreenConverter) {
        if (currentPosition == null) return false;

        double[] interpolatedPos = getInterpolatedPosition();
        double[] screenCoords = geoToScreenConverter.convert(interpolatedPos[0], interpolatedPos[1]);

        double dx = screenX - screenCoords[0];
        double dy = screenY - screenCoords[1];
        double distance = Math.sqrt(dx * dx + dy * dy);

        return distance <= (baseRadius + 5); // +5 per area click più grande
    }

    /**
     * Genera testo tooltip con informazioni dettagliate
     */
    public String getTooltipText() {
        if (currentPosition == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("🚍 Autobus ").append(currentPosition.getRouteShortName());
        sb.append("\n📍 Veicolo: ").append(currentPosition.getVehicleId());

        if (currentPosition.getHeadsign() != null) {
            sb.append("\n➡️ Direzione: ").append(currentPosition.getHeadsign());
        }

        if (currentPosition.getBearing() > 0) {
            sb.append("\n🧭 Bearing: ").append(String.format("%.0f°",
                                           currentPosition.getBearing()));
        }

        if (currentPosition.getSpeed() > 0) {
            sb.append("\n⚡ Velocità: ").append(String.format("%.1f km/h",
                                            currentPosition.getSpeed()));
        }

        if (currentPosition.getDelaySeconds() != 0) {
            int minutes = currentPosition.getDelaySeconds() / 60;
            sb.append("\n⏱️ Ritardo: ").append(minutes).append(" min");
        }

        if (currentPosition.getLastUpdate() != null) {
            sb.append("\n🔄 Aggiornato: ").append(currentPosition.getLastUpdate());
        }

        // Occupancy and capacity information
        if (currentPosition.getCapacity() > 0) {
            int occupancy = currentPosition.getOccupancyLevel();
            int capacity = currentPosition.getCapacity();
            double percentage = (double) occupancy / capacity * 100;

            sb.append("\n👥 Passeggeri: ").append(occupancy).append("/").append(capacity);
            sb.append(" (").append(String.format("%.0f%%", percentage)).append(")");

            // Add crowding level indicator
            if (percentage < 50) {
                sb.append(" 🟢 Poco affollato");
            } else if (percentage < 80) {
                sb.append(" 🟡 Moderatamente affollato");
            } else {
                sb.append(" 🔴 Molto affollato");
            }
        }

        return sb.toString();
    }

    // Getters
    public VehiclePosition getVehiclePosition() {
        return currentPosition;
    }

    public String getVehicleId() {
        return currentPosition != null ? currentPosition.getVehicleId() : null;
    }

    public String getRouteId() {
        return currentPosition != null ? currentPosition.getRouteId() : null;
    }

    public void setShowLabel(boolean show) {
        this.showLabel = show;
    }

    public void setShowDirection(boolean show) {
        this.showDirection = show;
    }

    public boolean isShowLabel() {
        return showLabel;
    }

    public boolean isShowDirection() {
        return showDirection;
    }
}
