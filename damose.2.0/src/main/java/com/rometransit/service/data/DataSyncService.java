package com.rometransit.service.data;

import com.rometransit.data.repository.StopRepository;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;

import java.util.List;

/**
 * Service to synchronize data from GTFSDataManager to the repository system
 */
public class DataSyncService {
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;

    public DataSyncService() {
        this.stopRepository = new StopRepository();
        this.routeRepository = new RouteRepository();
    }

    public void syncFromGTFSDataManager(GTFSDataManager gtfsDataManager) {
        if (!gtfsDataManager.isStaticDataLoaded()) {
            System.out.println("⚠️ GTFS data not loaded, skipping sync");
            return;
        }

        System.out.println("🔄 Starting data synchronization from GTFS cache to repository...");

        try {
            // Sync stops
            List<Stop> stops = gtfsDataManager.getAllStops();
            System.out.println("📍 Retrieved " + stops.size() + " stops from GTFSDataManager");
            if (!stops.isEmpty()) {
                System.out.println("   📍 Sample stop: " + stops.get(0).getStopId() + " - " + stops.get(0).getStopName());
                stopRepository.saveAll(stops);
                System.out.println("✅ Synced " + stops.size() + " stops to repository");
            } else {
                System.out.println("⚠️ No stops found in GTFSDataManager!");
            }

            // Sync routes
            List<Route> routes = gtfsDataManager.getAllRoutes();
            System.out.println("🚌 Retrieved " + routes.size() + " routes from GTFSDataManager");
            if (!routes.isEmpty()) {
                System.out.println("   🚌 Sample route: " + routes.get(0).getRouteId() + " - " + routes.get(0).getRouteShortName());
                routeRepository.saveAll(routes);
                System.out.println("✅ Synced " + routes.size() + " routes to repository");
            } else {
                System.out.println("⚠️ No routes found in GTFSDataManager!");
            }

            System.out.println("✅ Data synchronization completed successfully");
            System.out.println("   📊 Total synced: " + stops.size() + " stops and " + routes.size() + " routes");

        } catch (Exception e) {
            System.err.println("❌ Error during data synchronization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void clearRepositoryData() {
        System.out.println("🗑️ Clearing existing repository data...");
        try {
            stopRepository.deleteAll();
            routeRepository.deleteAll();
            System.out.println("✅ Repository data cleared");
        } catch (Exception e) {
            System.err.println("❌ Error clearing repository data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}