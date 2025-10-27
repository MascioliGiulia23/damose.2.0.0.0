package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.Stop;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Stop entity
 */
public class StopDAO {
    private final SQLiteDatabaseManager dbManager;

    public StopDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single stop
     */
    public void insert(Stop stop) throws SQLException {
        String sql = "INSERT OR REPLACE INTO stops " +
                    "(stop_id, stop_code, stop_name, stop_desc, stop_lat, stop_lon, " +
                    "zone_id, stop_url, location_type, parent_station, stop_timezone, wheelchair_boarding) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            stop.getStopId(),
            stop.getStopCode(),
            stop.getStopName(),
            stop.getStopDesc(),
            stop.getStopLat(),
            stop.getStopLon(),
            stop.getZoneId(),
            stop.getStopUrl(),
            stop.getLocationType(),
            stop.getParentStation(),
            stop.getStopTimezone(),
            stop.getWheelchairBoarding()
        );
    }

    /**
     * Insert multiple stops in batch
     */
    public void insertBatch(List<Stop> stops) throws SQLException {
        if (stops == null || stops.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO stops " +
                    "(stop_id, stop_code, stop_name, stop_desc, stop_lat, stop_lon, " +
                    "zone_id, stop_url, location_type, parent_station, stop_timezone, wheelchair_boarding) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                Stop stop = stops.get(index);
                stmt.setString(1, stop.getStopId());
                stmt.setString(2, stop.getStopCode());
                stmt.setString(3, stop.getStopName());
                stmt.setString(4, stop.getStopDesc());
                stmt.setDouble(5, stop.getStopLat());
                stmt.setDouble(6, stop.getStopLon());
                stmt.setString(7, stop.getZoneId());
                stmt.setString(8, stop.getStopUrl());
                stmt.setInt(9, stop.getLocationType());
                stmt.setString(10, stop.getParentStation());
                stmt.setString(11, stop.getStopTimezone());
                stmt.setInt(12, stop.getWheelchairBoarding());
            }

            @Override
            public int getBatchSize() {
                return stops.size();
            }
        });
    }

    /**
     * Find stop by ID
     */
    public Optional<Stop> findById(String stopId) throws SQLException {
        String sql = "SELECT * FROM stops WHERE stop_id = ?";

        return Optional.ofNullable(dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return mapResultSetToStop(rs);
            }
            return null;
        }, stopId));
    }

    /**
     * Find all stops
     */
    public List<Stop> findAll() throws SQLException {
        String sql = "SELECT * FROM stops ORDER BY stop_name";

        return dbManager.executeQuery(sql, rs -> {
            List<Stop> stops = new ArrayList<>();
            while (rs.next()) {
                stops.add(mapResultSetToStop(rs));
            }
            return stops;
        });
    }

    /**
     * Search stops by name or code
     */
    public List<Stop> searchByName(String query) throws SQLException {
        String sql = "SELECT * FROM stops " +
                    "WHERE stop_name LIKE ? OR stop_code LIKE ? " +
                    "ORDER BY stop_name " +
                    "LIMIT 50";

        String searchPattern = "%" + query + "%";

        return dbManager.executeQuery(sql, rs -> {
            List<Stop> stops = new ArrayList<>();
            while (rs.next()) {
                stops.add(mapResultSetToStop(rs));
            }
            return stops;
        }, searchPattern, searchPattern);
    }

    /**
     * Find nearby stops within bounding box
     */
    public List<Stop> findNearby(double minLat, double maxLat, double minLon, double maxLon) throws SQLException {
        String sql = "SELECT * FROM stops " +
                    "WHERE stop_lat BETWEEN ? AND ? " +
                    "AND stop_lon BETWEEN ? AND ? " +
                    "LIMIT 100";

        return dbManager.executeQuery(sql, rs -> {
            List<Stop> stops = new ArrayList<>();
            while (rs.next()) {
                stops.add(mapResultSetToStop(rs));
            }
            return stops;
        }, minLat, maxLat, minLon, maxLon);
    }

    /**
     * Delete stop by ID
     */
    public void delete(String stopId) throws SQLException {
        String sql = "DELETE FROM stops WHERE stop_id = ?";
        dbManager.executeUpdate(sql, stopId);
    }

    /**
     * Count total stops
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM stops";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all stops
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM stops");
    }

    /**
     * Map ResultSet to Stop object
     */
    private Stop mapResultSetToStop(ResultSet rs) throws SQLException {
        Stop stop = new Stop();
        stop.setStopId(rs.getString("stop_id"));
        stop.setStopCode(rs.getString("stop_code"));
        stop.setStopName(rs.getString("stop_name"));
        stop.setStopDesc(rs.getString("stop_desc"));
        stop.setStopLat(rs.getDouble("stop_lat"));
        stop.setStopLon(rs.getDouble("stop_lon"));
        stop.setZoneId(rs.getString("zone_id"));
        stop.setStopUrl(rs.getString("stop_url"));
        stop.setLocationType(rs.getInt("location_type"));
        stop.setParentStation(rs.getString("parent_station"));
        stop.setStopTimezone(rs.getString("stop_timezone"));
        stop.setWheelchairBoarding(rs.getInt("wheelchair_boarding"));
        return stop;
    }
}
