package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.dto.ArrivalPrediction;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Trip Updates / Arrival Predictions (realtime data)
 */
public class TripUpdateDAO {
    private final SQLiteDatabaseManager dbManager;

    public TripUpdateDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert or update trip update
     */
    public void upsert(ArrivalPrediction prediction) throws SQLException {
        String sql = "INSERT INTO trip_updates " +
                    "(trip_id, route_id, stop_id, stop_sequence, predicted_arrival, " +
                    "predicted_departure, delay_seconds, schedule_relationship, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long arrivalTime = prediction.getArrivalTime();
        if (arrivalTime == 0 && prediction.getPredictedArrival() != null) {
            arrivalTime = prediction.getPredictedArrival().atZone(ZoneId.systemDefault()).toEpochSecond();
        }

        long departureTime = prediction.getDepartureTime();
        if (departureTime == 0 && prediction.getExpectedDepartureTime() != null) {
            departureTime = prediction.getExpectedDepartureTime().atZone(ZoneId.systemDefault()).toEpochSecond();
        }

        dbManager.executeUpdate(sql,
            prediction.getTripId(),
            prediction.getRouteId(),
            prediction.getStopId(),
            prediction.getStopSequence(),
            arrivalTime,
            departureTime,
            prediction.getDelaySeconds(),
            prediction.getScheduleRelationship(),
            System.currentTimeMillis() / 1000
        );
    }

    /**
     * Batch insert trip updates
     * Validates foreign keys and sets invalid ones to NULL instead of discarding
     */
    public void upsertBatch(List<ArrivalPrediction> predictions) throws SQLException {
        if (predictions == null || predictions.isEmpty()) return;

        // Process predictions: set invalid foreign keys to NULL instead of discarding
        List<ArrivalPrediction> processedPredictions = validateForeignKeys(predictions);

        String sql = "INSERT INTO trip_updates " +
                    "(trip_id, route_id, stop_id, stop_sequence, predicted_arrival, " +
                    "predicted_departure, delay_seconds, schedule_relationship, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                ArrivalPrediction pred = processedPredictions.get(index);

                long arrivalTime = pred.getArrivalTime();
                if (arrivalTime == 0 && pred.getPredictedArrival() != null) {
                    arrivalTime = pred.getPredictedArrival().atZone(ZoneId.systemDefault()).toEpochSecond();
                }

                long departureTime = pred.getDepartureTime();
                if (departureTime == 0 && pred.getExpectedDepartureTime() != null) {
                    departureTime = pred.getExpectedDepartureTime().atZone(ZoneId.systemDefault()).toEpochSecond();
                }

                stmt.setString(1, pred.getTripId());
                stmt.setString(2, pred.getRouteId());
                stmt.setString(3, pred.getStopId());
                stmt.setInt(4, pred.getStopSequence());
                stmt.setLong(5, arrivalTime);
                stmt.setLong(6, departureTime);
                stmt.setInt(7, pred.getDelaySeconds());
                stmt.setString(8, pred.getScheduleRelationship());
                stmt.setLong(9, System.currentTimeMillis() / 1000);
            }

            @Override
            public int getBatchSize() {
                return processedPredictions.size();
            }
        });
    }

    // Static counters for batch logging (thread-safe)
    private static final java.util.concurrent.atomic.AtomicInteger totalInvalidTrips = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalInvalidRoutes = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalInvalidStops = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
    private static volatile long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 30000; // Log summary every 30 seconds

    /**
     * Validate that trip_id, route_id, and stop_id exist in the database before inserting
     * Instead of discarding invalid predictions, we set invalid foreign keys to NULL
     * This allows real-time data to be saved even if static GTFS data is outdated
     */
    private List<ArrivalPrediction> validateForeignKeys(List<ArrivalPrediction> predictions) throws SQLException {
        List<ArrivalPrediction> processed = new ArrayList<>();
        int invalidTrips = 0;
        int invalidRoutes = 0;
        int invalidStops = 0;

        for (ArrivalPrediction pred : predictions) {
            // Create a copy to avoid modifying the original
            ArrivalPrediction processedPred = new ArrivalPrediction();
            processedPred.setStopSequence(pred.getStopSequence());
            processedPred.setPredictedArrival(pred.getPredictedArrival());
            processedPred.setExpectedDepartureTime(pred.getExpectedDepartureTime());
            processedPred.setArrivalTime(pred.getArrivalTime());
            processedPred.setDepartureTime(pred.getDepartureTime());
            processedPred.setDelaySeconds(pred.getDelaySeconds());
            processedPred.setScheduleRelationship(pred.getScheduleRelationship());
            processedPred.setRealtime(pred.isRealtime());

            // Check trip_id - set to NULL if invalid
            if (pred.getTripId() != null && !pred.getTripId().isEmpty()) {
                if (tripExists(pred.getTripId())) {
                    processedPred.setTripId(pred.getTripId());
                } else {
                    processedPred.setTripId(null);
                    invalidTrips++;
                }
            }

            // Check route_id - set to NULL if invalid
            if (pred.getRouteId() != null && !pred.getRouteId().isEmpty()) {
                if (routeExists(pred.getRouteId())) {
                    processedPred.setRouteId(pred.getRouteId());
                } else {
                    processedPred.setRouteId(null);
                    invalidRoutes++;
                }
            }

            // Check stop_id - set to NULL if invalid
            if (pred.getStopId() != null && !pred.getStopId().isEmpty()) {
                if (stopExists(pred.getStopId())) {
                    processedPred.setStopId(pred.getStopId());
                } else {
                    processedPred.setStopId(null);
                    invalidStops++;
                }
            }

            // Always add the prediction, even with NULL foreign keys
            processed.add(processedPred);
        }

        // Update global counters
        totalInvalidTrips.addAndGet(invalidTrips);
        totalInvalidRoutes.addAndGet(invalidRoutes);
        totalInvalidStops.addAndGet(invalidStops);
        totalProcessed.addAndGet(predictions.size());

        // Log summary periodically (every 30 seconds) instead of for every batch
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > LOG_INTERVAL_MS && (invalidTrips > 0 || invalidRoutes > 0 || invalidStops > 0)) {
            synchronized (TripUpdateDAO.class) {
                if (currentTime - lastLogTime > LOG_INTERVAL_MS) {
                    int totalInvTrips = totalInvalidTrips.getAndSet(0);
                    int totalInvRoutes = totalInvalidRoutes.getAndSet(0);
                    int totalInvStops = totalInvalidStops.getAndSet(0);
                    int totalProc = totalProcessed.getAndSet(0);

                    if (totalInvTrips > 0 || totalInvRoutes > 0 || totalInvStops > 0) {
                        System.out.println("ℹ️  [Trip Updates] Last 30s: Processed " + totalProc + " updates, " +
                            totalInvTrips + " with invalid trip_id, " +
                            totalInvRoutes + " with invalid route_id, " +
                            totalInvStops + " with invalid stop_id (set to NULL)");
                    }
                    lastLogTime = currentTime;
                }
            }
        }

        return processed;
    }

    /**
     * Check if a trip exists in the database
     */
    private boolean tripExists(String tripId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM trips WHERE trip_id = ?";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }, tripId);
    }

    /**
     * Check if a route exists in the database
     */
    private boolean routeExists(String routeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM routes WHERE route_id = ?";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }, routeId);
    }

    /**
     * Check if a stop exists in the database
     */
    private boolean stopExists(String stopId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM stops WHERE stop_id = ?";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }, stopId);
    }

    /**
     * Find trip updates by stop ID
     */
    public List<ArrivalPrediction> findByStop(String stopId) throws SQLException {
        String sql = "SELECT * FROM trip_updates WHERE stop_id = ? ORDER BY predicted_arrival LIMIT 50";
        return dbManager.executeQuery(sql, this::mapResultSetToList, stopId);
    }

    /**
     * Find trip updates by trip ID
     */
    public List<ArrivalPrediction> findByTrip(String tripId) throws SQLException {
        String sql = "SELECT * FROM trip_updates WHERE trip_id = ? ORDER BY stop_sequence";
        return dbManager.executeQuery(sql, this::mapResultSetToList, tripId);
    }

    /**
     * Delete old trip updates (older than N seconds)
     */
    public void deleteOlderThan(int seconds) throws SQLException {
        long cutoff = (System.currentTimeMillis() / 1000) - seconds;
        dbManager.executeUpdate("DELETE FROM trip_updates WHERE last_updated < ?", cutoff);
    }

    /**
     * Delete all trip updates
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM trip_updates");
    }

    private List<ArrivalPrediction> mapResultSetToList(ResultSet rs) throws SQLException {
        List<ArrivalPrediction> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private ArrivalPrediction mapRow(ResultSet rs) throws SQLException {
        ArrivalPrediction pred = new ArrivalPrediction();
        pred.setTripId(rs.getString("trip_id"));
        pred.setRouteId(rs.getString("route_id"));
        pred.setStopId(rs.getString("stop_id"));
        pred.setStopSequence(rs.getInt("stop_sequence"));

        long arrivalTime = rs.getLong("predicted_arrival");
        if (arrivalTime > 0) {
            pred.setArrivalTime(arrivalTime);
            pred.setPredictedArrival(LocalDateTime.ofEpochSecond(arrivalTime, 0, ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now())));
        }

        long departureTime = rs.getLong("predicted_departure");
        if (departureTime > 0) {
            pred.setDepartureTime(departureTime);
            pred.setExpectedDepartureTime(LocalDateTime.ofEpochSecond(departureTime, 0, ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now())));
        }

        pred.setDelaySeconds(rs.getInt("delay_seconds"));
        pred.setScheduleRelationship(rs.getString("schedule_relationship"));
        pred.setRealtime(true);

        return pred;
    }
}
