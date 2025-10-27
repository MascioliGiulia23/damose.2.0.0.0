package com.rometransit.service.map;

import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Shape;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Trip;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.ui.component.NativeMapView;
import com.rometransit.ui.map.MapVehicleManager;
import com.rometransit.ui.waypoint.RouteVisualizationWaypoint;
import com.rometransit.ui.waypoint.StopMarkerWaypoint;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service per visualizzare fermate e percorsi sulla mappa
 * Gestisce la logica di creazione dei waypoint e visualizzazione
 * Integra anche i veicoli real-time tramite MapVehicleManager
 */
public class StopVisualizationService {
    private static StopVisualizationService instance;
    private final GTFSDataManager gtfsDataManager;
    private MapVehicleManager vehicleManager; // NUOVO

    private StopVisualizationService() {
        this.gtfsDataManager = GTFSDataManager.getInstance();
    }

    public static synchronized StopVisualizationService getInstance() {
        if (instance == null) {
            instance = new StopVisualizationService();
        }
        return instance;
    }

    /**
     * Visualizza una fermata sulla mappa con tutte le route che la servono
     * @param mapView La vista della mappa
     * @param stop La fermata da visualizzare
     */
    public void showStopWithRoutes(NativeMapView mapView, Stop stop) {
        if (mapView == null || stop == null) {
            System.err.println("⚠️ MapView or Stop is null");
            return;
        }

        System.out.println("🗺️ Showing stop on map: " + stop.getStopName() + " (" + stop.getStopId() + ")");

        // 0. Inizializza vehicle manager se necessario
        initializeVehicleManager(mapView);

        // 1. Pulisci waypoint precedenti e veicoli
        mapView.clearWaypoints();
        if (vehicleManager != null) {
            vehicleManager.clearAll();
        }

        // 2. Aggiungi il marker della fermata (pallino blu grande)
        StopMarkerWaypoint stopMarker = new StopMarkerWaypoint(
            stop,
            Color.web("#00ff00"),  // Verde per evidenziare la fermata selezionata
            true  // Mostra etichetta
        );
        mapView.addWaypoint(stopMarker);

        // 3. Trova tutte le route che passano per questa fermata
        List<RouteInfo> routeInfos = getRoutesForStop(stop);
        System.out.println("   Found " + routeInfos.size() + " routes serving this stop");

        // 4. Colori diversi per ogni route
        Color[] routeColors = {
            Color.web("#e74c3c"),  // Rosso
            Color.web("#3498db"),  // Blu
            Color.web("#2ecc71"),  // Verde
            Color.web("#f39c12"),  // Arancione
            Color.web("#9b59b6"),  // Viola
            Color.web("#1abc9c"),  // Turchese
            Color.web("#e67e22"),  // Arancione scuro
            Color.web("#95a5a6")   // Grigio
        };

        // 5. Visualizza ogni route con le sue fermate
        int colorIndex = 0;
        for (RouteInfo routeInfo : routeInfos) {
            Color routeColor = routeColors[colorIndex % routeColors.length];

            // Crea waypoint di visualizzazione per questa route
            RouteVisualizationWaypoint routeViz = new RouteVisualizationWaypoint(
                "route_" + routeInfo.route.getRouteId(),
                routeInfo.route,
                routeInfo.stopsOnRoute,
                routeInfo.shapePoints,
                getRouteDisplayName(routeInfo.route),
                routeColor,           // Colore percorso
                routeColor.darker()   // Colore fermate (più scuro)
            );

            mapView.addWaypoint(routeViz);

            // NUOVO: Registra route per aggiornamenti veicoli real-time
            if (vehicleManager != null) {
                vehicleManager.registerRouteVisualization(
                    routeInfo.route.getRouteId(), routeViz);
            }

            colorIndex++;
        }

        // 6. Centra la mappa sulla fermata e auto-zoom per vedere tutto
        if (routeInfos.isEmpty()) {
            // Se non ci sono route, centra solo sulla fermata
            mapView.setCenter(stop.getStopLat(), stop.getStopLon());
            mapView.setZoom(16);
        } else {
            // Altrimenti, auto-zoom per vedere tutte le route
            mapView.fitWaypointsInView();
        }

        System.out.println("✅ Stop visualization complete");
    }

    /**
     * Trova tutte le route che servono una specifica fermata
     */
    private List<RouteInfo> getRoutesForStop(Stop stop) {
        List<RouteInfo> routeInfos = new ArrayList<>();

        try {
            // Ottieni tutti i trips
            Map<String, Trip> allTrips = gtfsDataManager.getAllTrips();
            if (allTrips == null || allTrips.isEmpty()) {
                System.out.println("⚠️ No trips data available");
                return routeInfos;
            }

            // Set di route IDs che servono questa fermata
            Set<String> routeIds = new HashSet<>();

            // Trova tutti i trips che includono questa fermata
            System.out.println("   Scanning " + allTrips.size() + " trips...");
            int processedTrips = 0;
            for (Trip trip : allTrips.values()) {
                List<Stop> stopsForTrip = gtfsDataManager.getStopsByTrip(trip.getTripId());
                if (stopsForTrip == null || stopsForTrip.isEmpty()) {
                    continue;
                }

                boolean tripServesStop = stopsForTrip.stream()
                    .anyMatch(s -> s != null && s.getStopId() != null && s.getStopId().equals(stop.getStopId()));

                if (tripServesStop) {
                    routeIds.add(trip.getRouteId());
                }

                processedTrips++;
                if (processedTrips % 1000 == 0) {
                    System.out.println("   Processed " + processedTrips + " trips, found " + routeIds.size() + " routes so far...");
                }
            }

            System.out.println("   Found " + routeIds.size() + " unique routes for this stop");

            // Per ogni route, ottieni i dati completi
            for (String routeId : routeIds) {
                Route route = gtfsDataManager.getRouteById(routeId);
                if (route == null) {
                    System.out.println("   ⚠️ Route not found: " + routeId);
                    continue;
                }

                // Ottieni un trip rappresentativo per questa route
                List<Trip> tripsForRoute = gtfsDataManager.getTripsByRoute(routeId);
                if (tripsForRoute == null || tripsForRoute.isEmpty()) {
                    System.out.println("   ⚠️ No trips for route: " + routeId);
                    continue;
                }

                // Prendi il trip con più fermate
                Trip representativeTrip = tripsForRoute.stream()
                    .filter(t -> t != null)
                    .max(Comparator.comparingInt(t -> {
                        List<Stop> stops = gtfsDataManager.getStopsByTrip(t.getTripId());
                        return stops != null ? stops.size() : 0;
                    }))
                    .orElse(tripsForRoute.get(0));

                if (representativeTrip == null) {
                    System.out.println("   ⚠️ No representative trip for route: " + routeId);
                    continue;
                }

                // Ottieni le fermate di questo trip
                List<Stop> stopsOnRoute = gtfsDataManager.getStopsByTrip(representativeTrip.getTripId());
                if (stopsOnRoute == null) {
                    stopsOnRoute = new ArrayList<>();
                }

                // Ottieni i shape points
                List<Shape> shapePoints = new ArrayList<>();
                if (representativeTrip.getShapeId() != null && !representativeTrip.getShapeId().isEmpty()) {
                    shapePoints = gtfsDataManager.getShapePoints(representativeTrip.getShapeId());
                    if (shapePoints == null) {
                        shapePoints = new ArrayList<>();
                    }
                }

                // Aggiungi anche se non ci sono shape points (useremo solo le fermate)
                System.out.println("   Route " + route.getRouteShortName() + ": " +
                                 stopsOnRoute.size() + " stops, " +
                                 shapePoints.size() + " shape points");
                routeInfos.add(new RouteInfo(route, stopsOnRoute, shapePoints));
            }

        } catch (Exception e) {
            System.err.println("❌ Error finding routes for stop: " + e.getMessage());
            e.printStackTrace();
        }

        // Limita a massimo 8 route per non sovraccaricare la mappa
        if (routeInfos.size() > 8) {
            System.out.println("   Limiting to 8 routes for better visualization");
            return routeInfos.stream().limit(8).collect(Collectors.toList());
        }

        System.out.println("   Returning " + routeInfos.size() + " routes");
        return routeInfos;
    }

    /**
     * Ottiene il nome visualizzabile di una route
     */
    private String getRouteDisplayName(Route route) {
        if (route.getRouteShortName() != null && !route.getRouteShortName().isEmpty()) {
            return route.getRouteShortName();
        }
        if (route.getRouteLongName() != null && !route.getRouteLongName().isEmpty()) {
            return route.getRouteLongName();
        }
        return route.getRouteId();
    }

    /**
     * Classe helper per raggruppare informazioni di una route
     */
    private static class RouteInfo {
        final Route route;
        final List<Stop> stopsOnRoute;
        final List<Shape> shapePoints;

        RouteInfo(Route route, List<Stop> stopsOnRoute, List<Shape> shapePoints) {
            this.route = route;
            this.stopsOnRoute = stopsOnRoute;
            this.shapePoints = shapePoints;
        }
    }

    /**
     * Visualizza una fermata con solo le route selezionate
     * @param mapView La vista della mappa
     * @param stop La fermata da visualizzare
     * @param selectedRoutes Le route selezionate da visualizzare
     */
    public void showStopWithSelectedRoutes(NativeMapView mapView, Stop stop, List<Route> selectedRoutes) {
        if (mapView == null || stop == null || selectedRoutes == null || selectedRoutes.isEmpty()) {
            System.err.println("⚠️ Invalid parameters for showStopWithSelectedRoutes");
            return;
        }

        System.out.println("🗺️ Showing stop with " + selectedRoutes.size() + " selected route(s): " + stop.getStopName());

        // 0. Inizializza vehicle manager se necessario
        initializeVehicleManager(mapView);

        // 1. Pulisci waypoint precedenti e veicoli
        mapView.clearWaypoints();
        if (vehicleManager != null) {
            vehicleManager.clearAll();
        }

        // 2. Aggiungi il marker della fermata (pallino verde grande)
        StopMarkerWaypoint stopMarker = new StopMarkerWaypoint(
            stop,
            javafx.scene.paint.Color.web("#00ff00"),  // Verde per evidenziare la fermata selezionata
            true  // Mostra etichetta
        );
        mapView.addWaypoint(stopMarker);

        // 3. Colori diversi per ogni route
        javafx.scene.paint.Color[] routeColors = {
            javafx.scene.paint.Color.web("#e74c3c"),  // Rosso
            javafx.scene.paint.Color.web("#3498db"),  // Blu
            javafx.scene.paint.Color.web("#2ecc71"),  // Verde
            javafx.scene.paint.Color.web("#f39c12"),  // Arancione
            javafx.scene.paint.Color.web("#9b59b6"),  // Viola
            javafx.scene.paint.Color.web("#1abc9c"),  // Turchese
            javafx.scene.paint.Color.web("#e67e22"),  // Arancione scuro
            javafx.scene.paint.Color.web("#95a5a6")   // Grigio
        };

        // 4. Visualizza ogni route selezionata
        int colorIndex = 0;
        System.out.println("📋 Starting to visualize " + selectedRoutes.size() + " selected routes...");

        for (Route route : selectedRoutes) {
            System.out.println("\n   🔄 Processing route: " + route.getRouteShortName() + " (ID: " + route.getRouteId() + ")");
            javafx.scene.paint.Color routeColor = routeColors[colorIndex % routeColors.length];

            // Ottieni un trip rappresentativo per questa route
            System.out.println("      Fetching trips for route " + route.getRouteId() + "...");
            List<Trip> tripsForRoute = gtfsDataManager.getTripsByRoute(route.getRouteId());
            System.out.println("      Found " + (tripsForRoute != null ? tripsForRoute.size() : 0) + " trips");

            if (tripsForRoute == null || tripsForRoute.isEmpty()) {
                System.out.println("   ⚠️  SKIPPED route " + route.getRouteId() + " - No trips found");
                continue;
            }

            // Prendi il trip con più fermate
            Trip representativeTrip = tripsForRoute.stream()
                .filter(t -> t != null)
                .max(Comparator.comparingInt(t -> {
                    List<Stop> stops = gtfsDataManager.getStopsByTrip(t.getTripId());
                    return stops != null ? stops.size() : 0;
                }))
                .orElse(tripsForRoute.get(0));

            if (representativeTrip == null) {
                System.out.println("   ⚠️  SKIPPED route " + route.getRouteId() + " - No representative trip found");
                continue;
            }

            System.out.println("      Representative trip: " + representativeTrip.getTripId());

            // Ottieni le fermate e shape points
            System.out.println("      Fetching stops for trip " + representativeTrip.getTripId() + "...");
            List<Stop> stopsOnRoute = gtfsDataManager.getStopsByTrip(representativeTrip.getTripId());
            if (stopsOnRoute == null) {
                stopsOnRoute = new ArrayList<>();
            }
            System.out.println("      Found " + stopsOnRoute.size() + " stops");

            List<Shape> shapePoints = new ArrayList<>();
            if (representativeTrip.getShapeId() != null && !representativeTrip.getShapeId().isEmpty()) {
                System.out.println("      Fetching shape points for shape " + representativeTrip.getShapeId() + "...");
                shapePoints = gtfsDataManager.getShapePoints(representativeTrip.getShapeId());
                if (shapePoints == null) {
                    shapePoints = new ArrayList<>();
                }
                System.out.println("      Found " + shapePoints.size() + " shape points");
            } else {
                System.out.println("      ⚠️  No shape ID for this trip");
            }

            System.out.println("   ✅ Route " + route.getRouteShortName() + " data: " +
                             stopsOnRoute.size() + " stops, " +
                             shapePoints.size() + " shape points");

            // Crea waypoint di visualizzazione per questa route
            System.out.println("      Creating RouteVisualizationWaypoint...");
            RouteVisualizationWaypoint routeViz = new RouteVisualizationWaypoint(
                "route_" + route.getRouteId(),
                route,
                stopsOnRoute,
                shapePoints,
                getRouteDisplayName(route),
                routeColor,           // Colore percorso
                routeColor.darker()   // Colore fermate (più scuro)
            );

            System.out.println("      Adding waypoint to map...");
            mapView.addWaypoint(routeViz);
            System.out.println("   ✅ Added RouteVisualizationWaypoint for route " + route.getRouteShortName());

            // Registra route per aggiornamenti veicoli real-time
            if (vehicleManager != null) {
                vehicleManager.registerRouteVisualization(
                    route.getRouteId(), routeViz);
            }

            colorIndex++;
        }

        // 5. Auto-zoom per vedere tutto
        mapView.fitWaypointsInView();

        System.out.println("✅ Stop visualization complete with selected routes");
    }

    /**
     * Visualizza solo una route specifica
     */
    public void showRoute(NativeMapView mapView, Route route) {
        if (mapView == null || route == null) return;

        // Inizializza vehicle manager se necessario
        initializeVehicleManager(mapView);

        mapView.clearWaypoints();
        if (vehicleManager != null) {
            vehicleManager.clearAll();
        }

        // Ottieni trips per questa route
        List<Trip> trips = gtfsDataManager.getTripsByRoute(route.getRouteId());
        if (trips.isEmpty()) {
            System.out.println("⚠️ No trips found for route: " + route.getRouteId());
            return;
        }

        // Prendi il trip con più fermate
        Trip trip = trips.stream()
            .max(Comparator.comparingInt(t -> gtfsDataManager.getStopsByTrip(t.getTripId()).size()))
            .orElse(trips.get(0));

        List<Stop> stops = gtfsDataManager.getStopsByTrip(trip.getTripId());
        List<Shape> shapePoints = gtfsDataManager.getShapePoints(trip.getShapeId());

        System.out.println("🗺️ [StopVisualizationService.showRoute()] Creating RouteVisualizationWaypoint:");
        System.out.println("   Route ID: " + route.getRouteId());
        System.out.println("   Route name: " + getRouteDisplayName(route));
        System.out.println("   Trip ID: " + trip.getTripId());
        System.out.println("   Stops: " + (stops != null ? stops.size() : "null"));
        System.out.println("   Shape points: " + (shapePoints != null ? shapePoints.size() : "null"));

        RouteVisualizationWaypoint routeViz = new RouteVisualizationWaypoint(
            "route_" + route.getRouteId(),
            route,
            stops,
            shapePoints,
            getRouteDisplayName(route)
        );

        System.out.println("✅ RouteVisualizationWaypoint created, adding to map...");
        mapView.addWaypoint(routeViz);
        System.out.println("✅ Waypoint added to map");

        // NUOVO: Registra route per aggiornamenti veicoli real-time
        if (vehicleManager != null) {
            vehicleManager.registerRouteVisualization(route.getRouteId(), routeViz);
            System.out.println("✅ Route registered for vehicle tracking: " + route.getRouteId());
        }

        mapView.fitWaypointsInView();
    }

    /**
     * Pulisce la mappa
     */
    public void clearMap(NativeMapView mapView) {
        if (mapView != null) {
            mapView.clearWaypoints();
            if (vehicleManager != null) {
                vehicleManager.clearAll();
            }
        }
    }

    /**
     * Inizializza il vehicle manager se non è già stato fatto
     */
    private void initializeVehicleManager(NativeMapView mapView) {
        if (vehicleManager == null && mapView != null) {
            vehicleManager = new MapVehicleManager(mapView, gtfsDataManager);
            System.out.println("✅ MapVehicleManager initialized in StopVisualizationService");
        }
    }

    /**
     * Ottiene il vehicle manager (per debug/testing)
     */
    public MapVehicleManager getVehicleManager() {
        return vehicleManager;
    }

    /**
     * Ottiene il GTFSDataManager
     */
    public GTFSDataManager getGTFSDataManager() {
        return gtfsDataManager;
    }
}
