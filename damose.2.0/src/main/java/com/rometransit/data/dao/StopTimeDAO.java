package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.StopTime;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for StopTime entity
 */
public class StopTimeDAO {
    private final SQLiteDatabaseManager dbManager;

    public StopTimeDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single stop time
     */
    public void insert(StopTime stopTime) throws SQLException {
        String sql = "INSERT OR REPLACE INTO stop_times " +
                    "(trip_id, arrival_time, departure_time, stop_id, stop_sequence, " +
                    "stop_headsign, pickup_type, drop_off_type, shape_dist_traveled, timepoint) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            stopTime.getTripId(),
            stopTime.getArrivalTime(),
            stopTime.getDepartureTime(),
            stopTime.getStopId(),
            stopTime.getStopSequence(),
            stopTime.getStopHeadsign(),
            stopTime.getPickupType(),
            stopTime.getDropOffType(),
            stopTime.getShapeDistTraveled(),
            stopTime.getTimepoint()
        );
    }

    /**
     * Insert multiple stop times in batch
     */
    public void insertBatch(List<StopTime> stopTimes) throws SQLException {
        if (stopTimes == null || stopTimes.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO stop_times " +
                    "(trip_id, arrival_time, departure_time, stop_id, stop_sequence, " +
                    "stop_headsign, pickup_type, drop_off_type, shape_dist_traveled, timepoint) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                StopTime stopTime = stopTimes.get(index);
                stmt.setString(1, stopTime.getTripId());
                stmt.setString(2, stopTime.getArrivalTime());
                stmt.setString(3, stopTime.getDepartureTime());
                stmt.setString(4, stopTime.getStopId());
                stmt.setInt(5, stopTime.getStopSequence());
                stmt.setString(6, stopTime.getStopHeadsign());
                stmt.setInt(7, stopTime.getPickupType());
                stmt.setInt(8, stopTime.getDropOffType());
                stmt.setDouble(9, stopTime.getShapeDistTraveled());
                stmt.setInt(10, stopTime.getTimepoint());
            }

            @Override
            public int getBatchSize() {
                return stopTimes.size();
            }
        });
    }

    /**
     * Find stop times by trip ID
     */
    public List<StopTime> findByTrip(String tripId) throws SQLException {
        String sql = "SELECT * FROM stop_times WHERE trip_id = ? ORDER BY stop_sequence";

        return dbManager.executeQuery(sql, rs -> {
            List<StopTime> stopTimes = new ArrayList<>();
            while (rs.next()) {
                stopTimes.add(mapResultSetToStopTime(rs));
            }
            return stopTimes;
        }, tripId);
    }

    /**
     * Find stop times by stop ID
     */
    public List<StopTime> findByStop(String stopId) throws SQLException {
        String sql = "SELECT * FROM stop_times WHERE stop_id = ? ORDER BY arrival_time";

        return dbManager.executeQuery(sql, rs -> {
            List<StopTime> stopTimes = new ArrayList<>();
            while (rs.next()) {
                stopTimes.add(mapResultSetToStopTime(rs));
            }
            return stopTimes;
        }, stopId);
    }

    /**
     * Find all stop times (careful: can be millions of records!)
     */
    public List<StopTime> findAll() throws SQLException {
        String sql = "SELECT * FROM stop_times ORDER BY trip_id, stop_sequence";

        return dbManager.executeQuery(sql, rs -> {
            List<StopTime> stopTimes = new ArrayList<>();
            while (rs.next()) {
                stopTimes.add(mapResultSetToStopTime(rs));
            }
            return stopTimes;
        });
    }

    /**
     * Delete stop times by trip ID
     */
    public void deleteByTrip(String tripId) throws SQLException {
        String sql = "DELETE FROM stop_times WHERE trip_id = ?";
        dbManager.executeUpdate(sql, tripId);
    }

    /**
     * Count total stop times
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM stop_times";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all stop times
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM stop_times");
    }

    /**
     * Map ResultSet to StopTime object
     */
    private StopTime mapResultSetToStopTime(ResultSet rs) throws SQLException {
        StopTime stopTime = new StopTime();
        stopTime.setTripId(rs.getString("trip_id"));
        stopTime.setArrivalTime(rs.getString("arrival_time"));
        stopTime.setDepartureTime(rs.getString("departure_time"));
        stopTime.setStopId(rs.getString("stop_id"));
        stopTime.setStopSequence(rs.getInt("stop_sequence"));
        stopTime.setStopHeadsign(rs.getString("stop_headsign"));
        stopTime.setPickupType(rs.getInt("pickup_type"));
        stopTime.setDropOffType(rs.getInt("drop_off_type"));
        stopTime.setShapeDistTraveled(rs.getDouble("shape_dist_traveled"));
        stopTime.setTimepoint(rs.getInt("timepoint"));
        return stopTime;
    }
}
