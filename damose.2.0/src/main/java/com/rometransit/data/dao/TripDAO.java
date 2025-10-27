package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.Trip;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Trip entity
 */
public class TripDAO {
    private final SQLiteDatabaseManager dbManager;

    public TripDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single trip
     */
    public void insert(Trip trip) throws SQLException {
        String sql = "INSERT OR REPLACE INTO trips " +
                    "(trip_id, route_id, service_id, trip_headsign, trip_short_name, " +
                    "direction_id, block_id, shape_id, wheelchair_accessible, bikes_allowed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            trip.getTripId(),
            trip.getRouteId(),
            trip.getServiceId(),
            trip.getTripHeadsign(),
            trip.getTripShortName(),
            trip.getDirectionId(),
            trip.getBlockId(),
            trip.getShapeId(),
            trip.getWheelchairAccessible(),
            trip.getBikesAllowed()
        );
    }

    /**
     * Insert multiple trips in batch
     */
    public void insertBatch(List<Trip> trips) throws SQLException {
        if (trips == null || trips.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO trips " +
                    "(trip_id, route_id, service_id, trip_headsign, trip_short_name, " +
                    "direction_id, block_id, shape_id, wheelchair_accessible, bikes_allowed) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                Trip trip = trips.get(index);
                stmt.setString(1, trip.getTripId());
                stmt.setString(2, trip.getRouteId());
                stmt.setString(3, trip.getServiceId());
                stmt.setString(4, trip.getTripHeadsign());
                stmt.setString(5, trip.getTripShortName());
                stmt.setInt(6, trip.getDirectionId());
                stmt.setString(7, trip.getBlockId());
                stmt.setString(8, trip.getShapeId());
                stmt.setInt(9, trip.getWheelchairAccessible());
                stmt.setInt(10, trip.getBikesAllowed());
            }

            @Override
            public int getBatchSize() {
                return trips.size();
            }
        });
    }

    /**
     * Find trip by ID
     */
    public Optional<Trip> findById(String tripId) throws SQLException {
        String sql = "SELECT * FROM trips WHERE trip_id = ?";

        return Optional.ofNullable(dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return mapResultSetToTrip(rs);
            }
            return null;
        }, tripId));
    }

    /**
     * Find all trips
     */
    public List<Trip> findAll() throws SQLException {
        String sql = "SELECT * FROM trips";

        return dbManager.executeQuery(sql, rs -> {
            List<Trip> trips = new ArrayList<>();
            while (rs.next()) {
                trips.add(mapResultSetToTrip(rs));
            }
            return trips;
        });
    }

    /**
     * Find trips by route
     */
    public List<Trip> findByRoute(String routeId) throws SQLException {
        String sql = "SELECT * FROM trips WHERE route_id = ? ORDER BY direction_id, trip_headsign";

        return dbManager.executeQuery(sql, rs -> {
            List<Trip> trips = new ArrayList<>();
            while (rs.next()) {
                trips.add(mapResultSetToTrip(rs));
            }
            return trips;
        }, routeId);
    }

    /**
     * Search trips by headsign
     */
    public List<Trip> searchByHeadsign(String query) throws SQLException {
        String sql = "SELECT * FROM trips " +
                    "WHERE trip_headsign LIKE ? " +
                    "LIMIT 50";

        String searchPattern = "%" + query + "%";

        return dbManager.executeQuery(sql, rs -> {
            List<Trip> trips = new ArrayList<>();
            while (rs.next()) {
                trips.add(mapResultSetToTrip(rs));
            }
            return trips;
        }, searchPattern);
    }

    /**
     * Delete trip by ID
     */
    public void delete(String tripId) throws SQLException {
        String sql = "DELETE FROM trips WHERE trip_id = ?";
        dbManager.executeUpdate(sql, tripId);
    }

    /**
     * Count total trips
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM trips";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all trips
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM trips");
    }

    /**
     * Map ResultSet to Trip object
     */
    private Trip mapResultSetToTrip(ResultSet rs) throws SQLException {
        Trip trip = new Trip();
        trip.setTripId(rs.getString("trip_id"));
        trip.setRouteId(rs.getString("route_id"));
        trip.setServiceId(rs.getString("service_id"));
        trip.setTripHeadsign(rs.getString("trip_headsign"));
        trip.setTripShortName(rs.getString("trip_short_name"));
        trip.setDirectionId(rs.getInt("direction_id"));
        trip.setBlockId(rs.getString("block_id"));
        trip.setShapeId(rs.getString("shape_id"));
        trip.setWheelchairAccessible(rs.getInt("wheelchair_accessible"));
        trip.setBikesAllowed(rs.getInt("bikes_allowed"));
        return trip;
    }
}
