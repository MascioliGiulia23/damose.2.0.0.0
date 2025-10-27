package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.enums.VehicleStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for VehiclePosition (realtime data)
 */
public class VehiclePositionDAO {
    private final SQLiteDatabaseManager dbManager;

    public VehiclePositionDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert or update vehicle position
     */
    public void upsert(VehiclePosition position) throws SQLException {
        String sql = "INSERT OR REPLACE INTO vehicle_positions " +
                    "(vehicle_id, route_id, trip_id, latitude, longitude, bearing, speed, " +
                    "status, timestamp, congestion_level, occupancy_status, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long timestamp = position.getTimestamp();
        if (timestamp == 0 && position.getLastUpdate() != null) {
            timestamp = position.getLastUpdate().atZone(ZoneId.systemDefault()).toEpochSecond();
        }

        dbManager.executeUpdate(sql,
            position.getVehicleId(),
            position.getRouteId(),
            position.getTripId(),
            position.getLatitude(),
            position.getLongitude(),
            position.getBearing(),
            position.getSpeed(),
            position.getStatus() != null ? position.getStatus().name() : null,
            timestamp,
            null, // congestion_level
            position.getOccupancyLevel(),
            System.currentTimeMillis() / 1000
        );
    }

    /**
     * Batch upsert vehicle positions
     * Validates foreign keys and sets invalid ones to NULL instead of discarding
     */
    public void upsertBatch(List<VehiclePosition> positions) throws SQLException {
        if (positions == null || positions.isEmpty()) return;

        // Process positions: set invalid foreign keys to NULL instead of discarding
        List<VehiclePosition> processedPositions = validateForeignKeys(positions);

        String sql = "INSERT OR REPLACE INTO vehicle_positions " +
                    "(vehicle_id, route_id, trip_id, latitude, longitude, bearing, speed, " +
                    "status, timestamp, congestion_level, occupancy_status, last_updated) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                VehiclePosition pos = processedPositions.get(index);
                long timestamp = pos.getTimestamp();
                if (timestamp == 0 && pos.getLastUpdate() != null) {
                    timestamp = pos.getLastUpdate().atZone(ZoneId.systemDefault()).toEpochSecond();
                }

                stmt.setString(1, pos.getVehicleId());
                stmt.setString(2, pos.getRouteId());
                stmt.setString(3, pos.getTripId());
                stmt.setDouble(4, pos.getLatitude());
                stmt.setDouble(5, pos.getLongitude());
                stmt.setDouble(6, pos.getBearing());
                stmt.setDouble(7, pos.getSpeed());
                stmt.setString(8, pos.getStatus() != null ? pos.getStatus().name() : null);
                stmt.setLong(9, timestamp);
                stmt.setString(10, null);
                stmt.setInt(11, pos.getOccupancyLevel());
                stmt.setLong(12, System.currentTimeMillis() / 1000);
            }

            @Override
            public int getBatchSize() {
                return processedPositions.size();
            }
        });
    }

    // Static counters for batch logging (thread-safe)
    private static final java.util.concurrent.atomic.AtomicInteger totalInvalidRoutes = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalInvalidTrips = new java.util.concurrent.atomic.AtomicInteger(0);
    private static final java.util.concurrent.atomic.AtomicInteger totalProcessed = new java.util.concurrent.atomic.AtomicInteger(0);
    private static volatile long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 30000; // Log summary every 30 seconds

    /**
     * Validate that route_id and trip_id exist in the database before inserting
     * Instead of discarding invalid positions, we set invalid foreign keys to NULL
     * This allows real-time data to be displayed even if static GTFS data is outdated
     */
    private List<VehiclePosition> validateForeignKeys(List<VehiclePosition> positions) throws SQLException {
        List<VehiclePosition> processed = new ArrayList<>();
        int invalidRoutes = 0;
        int invalidTrips = 0;

        for (VehiclePosition pos : positions) {
            // Create a copy to avoid modifying the original
            VehiclePosition processedPos = new VehiclePosition();
            processedPos.setVehicleId(pos.getVehicleId());
            processedPos.setLatitude(pos.getLatitude());
            processedPos.setLongitude(pos.getLongitude());
            processedPos.setBearing(pos.getBearing());
            processedPos.setSpeed(pos.getSpeed());
            processedPos.setStatus(pos.getStatus());
            processedPos.setTimestamp(pos.getTimestamp());
            processedPos.setLastUpdate(pos.getLastUpdate());
            processedPos.setOccupancyLevel(pos.getOccupancyLevel());
            processedPos.setCurrentStopId(pos.getCurrentStopId());

            // Check route_id - set to NULL if invalid
            if (pos.getRouteId() != null && !pos.getRouteId().isEmpty()) {
                if (routeExists(pos.getRouteId())) {
                    processedPos.setRouteId(pos.getRouteId());
                } else {
                    processedPos.setRouteId(null); // Set to NULL instead of discarding
                    invalidRoutes++;
                }
            }

            // Check trip_id - set to NULL if invalid
            if (pos.getTripId() != null && !pos.getTripId().isEmpty()) {
                if (tripExists(pos.getTripId())) {
                    processedPos.setTripId(pos.getTripId());
                } else {
                    processedPos.setTripId(null); // Set to NULL instead of discarding
                    invalidTrips++;
                }
            }

            // Always add the vehicle, even with NULL foreign keys
            processed.add(processedPos);
        }

        // Update global counters
        totalInvalidRoutes.addAndGet(invalidRoutes);
        totalInvalidTrips.addAndGet(invalidTrips);
        totalProcessed.addAndGet(positions.size());

        // Log summary periodically (every 30 seconds) instead of for every batch
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > LOG_INTERVAL_MS && (invalidRoutes > 0 || invalidTrips > 0)) {
            synchronized (VehiclePositionDAO.class) {
                if (currentTime - lastLogTime > LOG_INTERVAL_MS) {
                    int totalInvRoutes = totalInvalidRoutes.getAndSet(0);
                    int totalInvTrips = totalInvalidTrips.getAndSet(0);
                    int totalProc = totalProcessed.getAndSet(0);

                    if (totalInvRoutes > 0 || totalInvTrips > 0) {
                        System.out.println("ℹ️  [Vehicle Positions] Last 30s: Processed " + totalProc + " vehicles, " +
                            totalInvRoutes + " with invalid route_id, " +
                            totalInvTrips + " with invalid trip_id (set to NULL)");
                    }
                    lastLogTime = currentTime;
                }
            }
        }

        return processed;
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
     * Find all vehicle positions
     */
    public List<VehiclePosition> findAll() throws SQLException {
        String sql = "SELECT * FROM vehicle_positions";
        return dbManager.executeQuery(sql, this::mapResultSetToList);
    }

    /**
     * Find vehicle positions by route
     */
    public List<VehiclePosition> findByRoute(String routeId) throws SQLException {
        String sql = "SELECT * FROM vehicle_positions WHERE route_id = ?";
        return dbManager.executeQuery(sql, this::mapResultSetToList, routeId);
    }

    /**
     * Find vehicle position by ID
     */
    public Optional<VehiclePosition> findById(String vehicleId) throws SQLException {
        String sql = "SELECT * FROM vehicle_positions WHERE vehicle_id = ?";
        return Optional.ofNullable(dbManager.executeQuery(sql, rs -> {
            if (rs.next()) return mapRow(rs);
            return null;
        }, vehicleId));
    }

    /**
     * Delete old vehicle positions (older than N seconds)
     */
    public void deleteOlderThan(int seconds) throws SQLException {
        long cutoff = (System.currentTimeMillis() / 1000) - seconds;
        dbManager.executeUpdate("DELETE FROM vehicle_positions WHERE last_updated < ?", cutoff);
    }

    /**
     * Delete all vehicle positions
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM vehicle_positions");
    }

    private List<VehiclePosition> mapResultSetToList(ResultSet rs) throws SQLException {
        List<VehiclePosition> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    private VehiclePosition mapRow(ResultSet rs) throws SQLException {
        VehiclePosition pos = new VehiclePosition();
        pos.setVehicleId(rs.getString("vehicle_id"));
        pos.setRouteId(rs.getString("route_id"));
        pos.setTripId(rs.getString("trip_id"));
        pos.setLatitude(rs.getDouble("latitude"));
        pos.setLongitude(rs.getDouble("longitude"));
        pos.setBearing(rs.getDouble("bearing"));
        pos.setSpeed(rs.getDouble("speed"));

        String status = rs.getString("status");
        if (status != null) {
            try {
                pos.setStatus(VehicleStatus.valueOf(status));
            } catch (Exception e) {
                pos.setStatus(VehicleStatus.IN_TRANSIT_TO);
            }
        }

        long timestamp = rs.getLong("timestamp");
        if (timestamp > 0) {
            pos.setTimestamp(timestamp);
            pos.setLastUpdate(LocalDateTime.ofEpochSecond(timestamp, 0, ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now())));
        }

        pos.setOccupancyLevel(rs.getInt("occupancy_status"));
        return pos;
    }
}
