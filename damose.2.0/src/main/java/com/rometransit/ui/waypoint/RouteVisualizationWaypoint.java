package com.rometransit.ui.waypoint;

import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Shape;
import com.rometransit.model.entity.Stop;
import com.rometransit.util.language.LanguageManager;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoint composito che combina:
 * - Linea rossa del percorso (RoutePathWaypoint)
 * - Pallini blu delle fermate (StopMarkerWaypoint)
 * - Pallini verdi degli autobus (VehicleMarkerWaypoint) - NUOVO
 *
 * Visualizza l'intero percorso di una linea con fermate e veicoli real-time
 */
public class RouteVisualizationWaypoint extends Waypoint {
    private final Route route;
    private final List<Stop> stopsOnRoute;
    private final List<Shape> routeShapePoints;
    private final String routeName;
    private final String routeId;

    // Waypoint componenti
    private final RoutePathWaypoint pathWaypoint;
    private final List<StopMarkerWaypoint> stopMarkers;
    private final List<VehicleMarkerWaypoint> vehicleMarkers; // NUOVO

    // Colori personalizzabili
    private Color routePathColor;
    private Color stopMarkerColor;
    private Color vehicleColor; // NUOVO

    /**
     * Costruttore con colori di default (rosso percorso, blu fermate, verde veicoli)
     */
    public RouteVisualizationWaypoint(String id, Route route, List<Stop> stopsOnRoute,
                                     List<Shape> routeShapePoints, String routeName) {
        this(id, route, stopsOnRoute, routeShapePoints, routeName,
             Color.web("#e74c3c"),  // Rosso per il percorso
             Color.web("#3498db"),  // Blu per le fermate
             Color.web("#27ae60")); // Verde per i veicoli
    }

    /**
     * Costruttore con colori personalizzati
     */
    public RouteVisualizationWaypoint(String id, Route route, List<Stop> stopsOnRoute,
                                     List<Shape> routeShapePoints, String routeName,
                                     Color routePathColor, Color stopMarkerColor) {
        this(id, route, stopsOnRoute, routeShapePoints, routeName,
             routePathColor, stopMarkerColor, Color.web("#27ae60"));
    }

    /**
     * Costruttore completo con tutti i colori personalizzati
     */
    public RouteVisualizationWaypoint(String id, Route route, List<Stop> stopsOnRoute,
                                     List<Shape> routeShapePoints, String routeName,
                                     Color routePathColor, Color stopMarkerColor, Color vehicleColor) {
        super(id);
        this.route = route;
        this.routeId = route != null ? route.getRouteId() : null;
        this.stopsOnRoute = stopsOnRoute != null ? stopsOnRoute : new ArrayList<>();
        this.routeShapePoints = routeShapePoints;
        this.routeName = routeName;
        this.routePathColor = routePathColor;
        this.stopMarkerColor = stopMarkerColor;
        this.vehicleColor = vehicleColor;

        // Crea il waypoint per il percorso (linea rossa)
        this.pathWaypoint = new RoutePathWaypoint(
            id + "_path",
            route,
            routeShapePoints,
            routeName,
            routePathColor,
            3.5,
            true  // Mostra frecce direzionali
        );

        // Crea i waypoint per le fermate (pallini blu)
        this.stopMarkers = new ArrayList<>();
        for (Stop stop : this.stopsOnRoute) {
            StopMarkerWaypoint stopMarker = new StopMarkerWaypoint(stop, stopMarkerColor, true);
            this.stopMarkers.add(stopMarker);
        }

        // Inizializza lista veicoli vuota (sarà popolata dinamicamente)
        this.vehicleMarkers = new ArrayList<>();
    }

    @Override
    public void render(GraphicsContext gc, int zoom, GeoToScreenConverter geoToScreenConverter) {
        if (!visible) return;

        // 1. Prima disegna il percorso (linea rossa) - layer più basso
        pathWaypoint.render(gc, zoom, geoToScreenConverter);

        // 2. Poi disegna le fermate (pallini blu) sopra il percorso
        // SEMPRE VISIBILI a tutti i livelli di zoom
        for (StopMarkerWaypoint stopMarker : stopMarkers) {
            stopMarker.render(gc, zoom, geoToScreenConverter);
        }

        // 3. Infine disegna i veicoli (pallini verdi) - layer più alto
        // SEMPRE VISIBILI a tutti i livelli di zoom
        if (!vehicleMarkers.isEmpty()) {
            // Configura label in base allo zoom
            boolean showLabels = zoom >= 14;
            for (VehicleMarkerWaypoint vehicleMarker : vehicleMarkers) {
                vehicleMarker.setShowLabel(showLabels);
                vehicleMarker.render(gc, zoom, geoToScreenConverter);
            }
        }

        // 4. Disegna statistiche SEMPRE in basso a sinistra
        drawStatistics(gc, zoom);
    }

    /**
     * Disegna statistiche del percorso (numero fermate, lunghezza, ecc.)
     * SEMPRE VISIBILE a tutti i livelli di zoom
     */
    private void drawStatistics(GraphicsContext gc, int zoom) {
        // Posizione in basso a sinistra (sarà visibile sulla mappa)
        double statX = 15;
        double statY = gc.getCanvas().getHeight() - 80;

        // Box semi-trasparente per le statistiche
        gc.setFill(Color.color(1, 1, 1, 0.85));
        gc.fillRoundRect(statX, statY, 180, 70, 6, 6);

        gc.setStroke(routePathColor);
        gc.setLineWidth(2);
gc.strokeRoundRect(statX, statY, 180, 70, 6, 6);

        // Testo delle statistiche
        gc.setFill(Color.BLACK);
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11));

        // Titolo
        gc.fillText(LanguageManager.getInstance().getString("map.routeLabel") + ": " + routeName, statX + 8, statY + 18);

        // Numero di fermate
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.NORMAL, 10));
        gc.fillText(LanguageManager.getInstance().getString("map.stopsLabel") + ": " + stopsOnRoute.size(), statX + 8, statY + 35);

        // Numero veicoli attivi - NUOVO
        int activeVehicles = vehicleMarkers.size();
        if (activeVehicles > 0) {
            gc.setFill(vehicleColor);
            gc.fillText("🚍 " + LanguageManager.getInstance().getString("map.busesLabel") + ": " + activeVehicles, statX + 8, statY + 50);
            gc.setFill(Color.BLACK);
        } else {
            gc.fillText(LanguageManager.getInstance().getString("map.busesLabel") + ": 0", statX + 8, statY + 50);
        }

        // Tipo di route
        if (route != null && route.getRouteType() != null) {
            String routeType = route.getRouteType().toString();
            gc.fillText(LanguageManager.getInstance().getString("map.typeLabel") + ": " + routeType, statX + 8, statY + 65);
        }
    }

    /**
     * Aggiorna il colore del percorso
     */
    public void setRoutePathColor(Color color) {
        this.routePathColor = color;
        // Ricrea il pathWaypoint con il nuovo colore
        // (in una implementazione più avanzata, si potrebbe usare un setter nel pathWaypoint)
    }

    /**
     * Aggiorna il colore delle fermate
     */
    public void setStopMarkerColor(Color color) {
        this.stopMarkerColor = color;
        // Ricrea gli stopMarkers con il nuovo colore
        // (in una implementazione più avanzata, si potrebbe usare un setter negli stopMarkers)
    }

    /**
     * Mostra/nascondi solo il percorso (mantiene le fermate)
     */
    public void setPathVisible(boolean visible) {
        pathWaypoint.setVisible(visible);
    }

    /**
     * Mostra/nascondi solo le fermate (mantiene il percorso)
     */
    public void setStopsVisible(boolean visible) {
        for (StopMarkerWaypoint marker : stopMarkers) {
            marker.setVisible(visible);
        }
    }

    /**
     * Calcola i bounds geografici di questo percorso (utile per auto-zoom)
     */
    public GeographicBounds getGeographicBounds() {
        if (routeShapePoints == null || routeShapePoints.isEmpty()) {
            return null;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (Shape shape : routeShapePoints) {
            minLat = Math.min(minLat, shape.getShapePtLat());
            maxLat = Math.max(maxLat, shape.getShapePtLat());
            minLon = Math.min(minLon, shape.getShapePtLon());
            maxLon = Math.max(maxLon, shape.getShapePtLon());
        }

        return new GeographicBounds(minLat, maxLat, minLon, maxLon);
    }

    // Getters
    public Route getRoute() {
        return route;
    }

    public List<Stop> getStopsOnRoute() {
        return stopsOnRoute;
    }

    public List<Shape> getRouteShapePoints() {
        return routeShapePoints;
    }

    public String getRouteName() {
        return routeName;
    }

    public int getStopCount() {
        return stopsOnRoute.size();
    }

    public int getShapePointCount() {
        return routeShapePoints != null ? routeShapePoints.size() : 0;
    }

    // === VEHICLE MANAGEMENT METHODS - NUOVO ===

    /**
     * Aggiunge un veicolo alla visualizzazione
     * Verifica automaticamente che il veicolo appartenga a questa route
     */
    public void addVehicle(VehiclePosition position) {
        if (position == null) return;

        // Verifica che il veicolo appartenga a questa route
        if (!routeId.equals(position.getRouteId())) {
            return;
        }

        String vehicleId = "vehicle_" + position.getVehicleId();

        // Rimuovi vecchio marker se esiste
        vehicleMarkers.removeIf(v -> v.getVehicleId().equals(position.getVehicleId()));

        // Aggiungi nuovo marker
        VehicleMarkerWaypoint marker = new VehicleMarkerWaypoint(
            vehicleId, position, vehicleColor, 8.0, true, true
        );
        vehicleMarkers.add(marker);
    }

    /**
     * Aggiorna tutti i veicoli sulla route
     * Sostituisce completamente i veicoli esistenti
     */
    public void updateVehicles(List<VehiclePosition> positions) {
        // Rimuovi veicoli vecchi
        vehicleMarkers.clear();

        // Aggiungi veicoli aggiornati che appartengono a questa route
        for (VehiclePosition pos : positions) {
            if (routeId.equals(pos.getRouteId())) {
                addVehicle(pos);
            }
        }
    }

    /**
     * Rimuove un veicolo specifico
     */
    public void removeVehicle(String vehicleId) {
        vehicleMarkers.removeIf(v -> v.getVehicleId().equals(vehicleId));
    }

    /**
     * Rimuove tutti i veicoli
     */
    public void clearVehicles() {
        vehicleMarkers.clear();
    }

    /**
     * Ottiene lista veicoli sulla route
     */
    public List<VehicleMarkerWaypoint> getVehicleMarkers() {
        return new ArrayList<>(vehicleMarkers);
    }

    /**
     * Conta veicoli attivi sulla route
     */
    public int getVehicleCount() {
        return vehicleMarkers.size();
    }

    /**
     * Verifica se ci sono veicoli sulla route
     */
    public boolean hasVehicles() {
        return !vehicleMarkers.isEmpty();
    }

    /**
     * Ottiene ID della route
     */
    public String getRouteId() {
        return routeId;
    }

    /**
     * Classe helper per rappresentare i bounds geografici
     */
    public static class GeographicBounds {
        public final double minLat;
        public final double maxLat;
        public final double minLon;
        public final double maxLon;

        public GeographicBounds(double minLat, double maxLat, double minLon, double maxLon) {
            this.minLat = minLat;
            this.maxLat = maxLat;
            this.minLon = minLon;
            this.maxLon = maxLon;
        }

        public double getCenterLat() {
            return (minLat + maxLat) / 2.0;
        }

        public double getCenterLon() {
            return (minLon + maxLon) / 2.0;
        }

        public double getLatSpan() {
            return maxLat - minLat;
        }

        public double getLonSpan() {
            return maxLon - minLon;
        }

        @Override
        public String toString() {
            return String.format("Bounds[lat: %.6f to %.6f, lon: %.6f to %.6f]",
                minLat, maxLat, minLon, maxLon);
        }
    }
}
