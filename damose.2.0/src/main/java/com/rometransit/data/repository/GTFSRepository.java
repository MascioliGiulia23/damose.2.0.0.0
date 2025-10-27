package com.rometransit.data.repository;

import com.rometransit.data.dao.*;
import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Repository pattern for GTFS data access
 * Centralizes all database operations and provides high-level API
 *
 * This replaces the old JSON-based GTFSCacheManager
 */
public class GTFSRepository {
    private static GTFSRepository instance;

    private final SQLiteDatabaseManager dbManager;
    private final AgencyDAO agencyDAO;
    private final RouteDAO routeDAO;
    private final StopDAO stopDAO;
    private final TripDAO tripDAO;
    private final StopTimeDAO stopTimeDAO;
    private final ShapeDAO shapeDAO;
    private final VehiclePositionDAO vehiclePositionDAO;
    private final TripUpdateDAO tripUpdateDAO;

    private GTFSRepository() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
        this.agencyDAO = new AgencyDAO();
        this.routeDAO = new RouteDAO();
        this.stopDAO = new StopDAO();
        this.tripDAO = new TripDAO();
        this.stopTimeDAO = new StopTimeDAO();
        this.shapeDAO = new ShapeDAO();
        this.vehiclePositionDAO = new VehiclePositionDAO();
        this.tripUpdateDAO = new TripUpdateDAO();

        System.out.println("📊 GTFS Repository initialized (SQLite-based)");
    }

    public static synchronized GTFSRepository getInstance() {
        if (instance == null) {
            instance = new GTFSRepository();
        }
        return instance;
    }

    // ===== Agency Methods =====

    public void saveAgencies(List<Agency> agencies) throws SQLException {
        agencyDAO.insertBatch(agencies);
    }

    public List<Agency> loadAgencies() throws SQLException {
        return agencyDAO.findAll();
    }

    public Optional<Agency> getAgency(String agencyId) throws SQLException {
        return agencyDAO.findById(agencyId);
    }

    // ===== Route Methods =====

    public void saveRoutes(List<Route> routes) throws SQLException {
        routeDAO.insertBatch(routes);
    }

    public List<Route> loadRoutes() throws SQLException {
        return routeDAO.findAll();
    }

    public Optional<Route> getRoute(String routeId) throws SQLException {
        return routeDAO.findById(routeId);
    }

    public List<Route> searchRoutes(String query) throws SQLException {
        return routeDAO.searchByName(query);
    }

    // ===== Stop Methods =====

    public void saveStops(List<Stop> stops) throws SQLException {
        stopDAO.insertBatch(stops);
    }

    public List<Stop> loadStops() throws SQLException {
        return stopDAO.findAll();
    }

    public Optional<Stop> getStop(String stopId) throws SQLException {
        return stopDAO.findById(stopId);
    }

    public List<Stop> searchStops(String query) throws SQLException {
        return stopDAO.searchByName(query);
    }

    public List<Stop> findNearbyStops(double minLat, double maxLat, double minLon, double maxLon) throws SQLException {
        return stopDAO.findNearby(minLat, maxLat, minLon, maxLon);
    }

    // ===== Trip Methods =====

    public void saveTrips(List<Trip> trips) throws SQLException {
        tripDAO.insertBatch(trips);
    }

    public List<Trip> loadTrips() throws SQLException {
        return tripDAO.findAll();
    }

    public Optional<Trip> getTrip(String tripId) throws SQLException {
        return tripDAO.findById(tripId);
    }

    public List<Trip> getTripsByRoute(String routeId) throws SQLException {
        return tripDAO.findByRoute(routeId);
    }

    // ===== StopTime Methods =====

    public void saveStopTimes(List<StopTime> stopTimes) throws SQLException {
        stopTimeDAO.insertBatch(stopTimes);
    }

    public List<StopTime> loadStopTimes() throws SQLException {
        return stopTimeDAO.findAll();
    }

    public List<StopTime> getStopTimesByTrip(String tripId) throws SQLException {
        return stopTimeDAO.findByTrip(tripId);
    }

    public List<StopTime> getStopTimesByStop(String stopId) throws SQLException {
        return stopTimeDAO.findByStop(stopId);
    }

    // ===== Shape Methods =====

    public void saveShapes(List<Shape> shapes) throws SQLException {
        shapeDAO.insertBatch(shapes);
    }

    public List<Shape> loadShapes() throws SQLException {
        return shapeDAO.findAll();
    }

    public List<Shape> getShapePoints(String shapeId) throws SQLException {
        return shapeDAO.findByShapeId(shapeId);
    }

    // ===== VehiclePosition Methods (Realtime) =====

    public void saveVehiclePositions(List<VehiclePosition> positions) throws SQLException {
        vehiclePositionDAO.upsertBatch(positions);

        // Clean old positions (older than 10 minutes)
        vehiclePositionDAO.deleteOlderThan(600);
    }

    public List<VehiclePosition> loadVehiclePositions() throws SQLException {
        return vehiclePositionDAO.findAll();
    }

    public List<VehiclePosition> getVehiclePositionsByRoute(String routeId) throws SQLException {
        return vehiclePositionDAO.findByRoute(routeId);
    }

    public Optional<VehiclePosition> getVehiclePosition(String vehicleId) throws SQLException {
        return vehiclePositionDAO.findById(vehicleId);
    }

    // ===== TripUpdate Methods (Realtime) =====

    public void saveTripUpdates(List<ArrivalPrediction> predictions) throws SQLException {
        tripUpdateDAO.upsertBatch(predictions);

        // Clean old updates (older than 30 minutes)
        tripUpdateDAO.deleteOlderThan(1800);
    }

    public List<ArrivalPrediction> getTripUpdatesByStop(String stopId) throws SQLException {
        return tripUpdateDAO.findByStop(stopId);
    }

    public List<ArrivalPrediction> getTripUpdatesByTrip(String tripId) throws SQLException {
        return tripUpdateDAO.findByTrip(tripId);
    }

    // ===== Bulk Operations =====

    /**
     * Save all GTFS static data in a transaction
     */
    public void saveAllGTFSData(List<Agency> agencies, List<Route> routes, List<Stop> stops,
                                List<Trip> trips, List<StopTime> stopTimes, List<Shape> shapes) throws SQLException {

        System.out.println("💾 Saving all GTFS data to SQLite database...");
        long startTime = System.currentTimeMillis();

        dbManager.executeInTransaction(conn -> {
            try {
                if (agencies != null && !agencies.isEmpty()) {
                    System.out.println("   Saving " + agencies.size() + " agencies...");
                    agencyDAO.insertBatch(agencies);
                }

                if (routes != null && !routes.isEmpty()) {
                    System.out.println("   Saving " + routes.size() + " routes...");
                    routeDAO.insertBatch(routes);
                }

                if (stops != null && !stops.isEmpty()) {
                    System.out.println("   Saving " + stops.size() + " stops...");
                    stopDAO.insertBatch(stops);
                }

                if (trips != null && !trips.isEmpty()) {
                    System.out.println("   Saving " + trips.size() + " trips...");
                    tripDAO.insertBatch(trips);
                }

                if (stopTimes != null && !stopTimes.isEmpty()) {
                    System.out.println("   Saving " + stopTimes.size() + " stop_times...");
                    stopTimeDAO.insertBatch(stopTimes);
                }

                if (shapes != null && !shapes.isEmpty()) {
                    System.out.println("   Saving " + shapes.size() + " shapes...");
                    shapeDAO.insertBatch(shapes);
                }

                return null;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save GTFS data", e);
            }
        });

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("✅ All GTFS data saved in " + elapsed + "ms");
    }

    /**
     * Clear all data from database
     */
    public void clearAllData() throws SQLException {
        System.out.println("🗑️  Clearing all data from SQLite database...");
        dbManager.clearAllData();
        System.out.println("✅ All data cleared");
    }

    /**
     * Get database statistics
     */
    public SQLiteDatabaseManager.DatabaseStats getStats() throws SQLException {
        return dbManager.getStats();
    }

    /**
     * Optimize database
     */
    public void optimize() throws SQLException {
        dbManager.optimize();
    }

    /**
     * Shutdown repository
     */
    public void shutdown() {
        dbManager.shutdown();
    }

    /**
     * Check if database is initialized and has data
     */
    public boolean hasData() {
        try {
            return stopDAO.count() > 0 || routeDAO.count() > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Get counts for all entities
     */
    public DataCounts getCounts() {
        try {
            DataCounts counts = new DataCounts();
            counts.agencies = agencyDAO.count();
            counts.routes = routeDAO.count();
            counts.stops = stopDAO.count();
            counts.trips = tripDAO.count();
            counts.stopTimes = stopTimeDAO.count();
            counts.shapes = shapeDAO.countDistinctShapes();
            return counts;
        } catch (SQLException e) {
            return new DataCounts();
        }
    }

    public static class DataCounts {
        public int agencies;
        public int routes;
        public int stops;
        public int trips;
        public int stopTimes;
        public int shapes;

        @Override
        public String toString() {
            return String.format("Agencies: %d, Routes: %d, Stops: %d, Trips: %d, StopTimes: %d, Shapes: %d",
                agencies, routes, stops, trips, stopTimes, shapes);
        }
    }
}
