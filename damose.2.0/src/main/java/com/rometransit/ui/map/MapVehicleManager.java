package com.rometransit.ui.map;

import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.ui.listener.VehicleUpdateListener;
import com.rometransit.ui.component.NativeMapView;
import com.rometransit.ui.waypoint.RouteVisualizationWaypoint;
import javafx.application.Platform;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager per gestire la visualizzazione dei veicoli sulla mappa
 * Sincronizza aggiornamenti real-time (ogni 30s) con i waypoints
 *
 * Questo manager:
 * - Ascolta gli aggiornamenti veicoli dal GTFSDataManager
 * - Aggiorna automaticamente i RouteVisualizationWaypoint registrati
 * - Trigger il repaint della mappa quando necessario
 */
public class MapVehicleManager implements VehicleUpdateListener {

    private final NativeMapView mapView;
    private final GTFSDataManager dataManager;

    // Mappa routeId -> RouteVisualizationWaypoint attivo
    private final ConcurrentHashMap<String, RouteVisualizationWaypoint>
        activeRouteVisualizations = new ConcurrentHashMap<>();

    private boolean autoUpdateEnabled = true;
    private int updateCount = 0;

    public MapVehicleManager(NativeMapView mapView, GTFSDataManager dataManager) {
        this.mapView = mapView;
        this.dataManager = dataManager;

        // Registra listener per aggiornamenti automatici
        dataManager.addVehicleUpdateListener(this);

        System.out.println("🚗 MapVehicleManager initialized");
        System.out.println("   📡 Listening for vehicle updates every 30s");
    }

    /**
     * Registra una RouteVisualizationWaypoint per aggiornamenti automatici
     * I veicoli saranno aggiornati ogni 30s automaticamente
     */
    public void registerRouteVisualization(String routeId,
                                          RouteVisualizationWaypoint waypoint) {
        if (routeId == null || routeId.isEmpty() || waypoint == null) {
            return;
        }

        activeRouteVisualizations.put(routeId, waypoint);

        // Carica veicoli esistenti immediatamente (con fallback a simulati)
        List<VehiclePosition> vehicles =
            dataManager.getVehiclePositionsForRouteWithSimulation(routeId);
        waypoint.updateVehicles(vehicles);

        System.out.println("✅ Registered route " + routeId +
                         " with " + vehicles.size() + " vehicles" +
                         (vehicles.stream().anyMatch(v -> v.isSimulated()) ? " (simulated)" : ""));
    }

    /**
     * Rimuove registrazione route
     * I veicoli non saranno più aggiornati automaticamente
     */
    public void unregisterRouteVisualization(String routeId) {
        RouteVisualizationWaypoint waypoint =
            activeRouteVisualizations.remove(routeId);
        if (waypoint != null) {
            waypoint.clearVehicles();
            System.out.println("✅ Unregistered route " + routeId);
        }
    }

    /**
     * Pulisce tutte le registrazioni
     */
    public void clearAll() {
        System.out.println("🧹 Clearing all vehicle visualizations (" +
                         activeRouteVisualizations.size() + " routes)");

        for (RouteVisualizationWaypoint waypoint :
             activeRouteVisualizations.values()) {
            waypoint.clearVehicles();
        }
        activeRouteVisualizations.clear();
    }

    /**
     * Callback quando veicoli sono aggiornati (ogni 30s)
     * Implementa VehicleUpdateListener
     */
    @Override
    public void onVehiclesUpdated(List<VehiclePosition> positions) {
        if (!autoUpdateEnabled) return;

        if (activeRouteVisualizations.isEmpty()) {
            // Nessuna route registrata, skip update
            return;
        }

        // DEBUG: Stampa distribuzione veicoli per route
        if (updateCount == 0) {
            System.out.println("🔍 [DEBUG] Vehicle distribution across all routes:");
            java.util.Map<String, Long> vehiclesByRoute = positions.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    VehiclePosition::getRouteId,
                    java.util.stream.Collectors.counting()));

            vehiclesByRoute.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(20)
                .forEach(entry ->
                    System.out.println("   Route " + entry.getKey() + ": " +
                                     entry.getValue() + " vehicles"));
        }

        // Aggiorna solo le route attualmente visualizzate
        Platform.runLater(() -> {
            updateCount++;

            int totalVehiclesUpdated = 0;
            for (var entry : activeRouteVisualizations.entrySet()) {
                String routeId = entry.getKey();
                RouteVisualizationWaypoint waypoint = entry.getValue();

                // Filtra veicoli per questa route
                List<VehiclePosition> routeVehicles = positions.stream()
                    .filter(vp -> routeId.equals(vp.getRouteId()))
                    .filter(vp -> !vp.isStale())
                    .toList();

                // Aggiorna waypoint
                waypoint.updateVehicles(routeVehicles);
                totalVehiclesUpdated += routeVehicles.size();
            }

            // Trigger repaint della mappa
            mapView.markForRepaint();

            System.out.println("🔄 [Update #" + updateCount + "] Updated " +
                             totalVehiclesUpdated + " vehicles on " +
                             activeRouteVisualizations.size() + " routes");
        });
    }

    @Override
    public void onUpdateFailed(Exception error) {
        System.err.println("⚠️ Vehicle update failed: " + error.getMessage());
        // Non fa nulla, mantiene veicoli esistenti
    }

    /**
     * Abilita/disabilita aggiornamenti automatici
     */
    public void setAutoUpdateEnabled(boolean enabled) {
        this.autoUpdateEnabled = enabled;
        System.out.println("🔄 Auto-update " +
                         (enabled ? "enabled" : "disabled"));

        if (!enabled) {
            // Pulisci veicoli quando disabilitato
            for (RouteVisualizationWaypoint waypoint :
                 activeRouteVisualizations.values()) {
                waypoint.clearVehicles();
            }
            mapView.markForRepaint();
        }
    }

    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    public int getActiveRouteCount() {
        return activeRouteVisualizations.size();
    }

    public int getUpdateCount() {
        return updateCount;
    }

    /**
     * Forza aggiornamento immediato di tutti i veicoli
     */
    public void forceUpdate() {
        List<VehiclePosition> positions = dataManager.getActiveVehiclePositions();
        onVehiclesUpdated(positions);
        System.out.println("🔄 Forced vehicle update");
    }

    /**
     * Ottiene statistiche
     */
    public String getStats() {
        int totalVehicles = 0;
        for (RouteVisualizationWaypoint waypoint :
             activeRouteVisualizations.values()) {
            totalVehicles += waypoint.getVehicleCount();
        }

        return String.format("Routes: %d | Vehicles: %d | Updates: %d | Auto: %s",
            activeRouteVisualizations.size(),
            totalVehicles,
            updateCount,
            autoUpdateEnabled ? "ON" : "OFF");
    }

    /**
     * Shutdown e cleanup
     */
    public void shutdown() {
        System.out.println("🛑 Shutting down MapVehicleManager...");

        // Rimuovi listener dal dataManager
        dataManager.removeVehicleUpdateListener(this);

        // Pulisci tutte le visualizzazioni
        clearAll();

        System.out.println("✅ MapVehicleManager shutdown completed");
        System.out.println("   📊 Total updates processed: " + updateCount);
    }
}
