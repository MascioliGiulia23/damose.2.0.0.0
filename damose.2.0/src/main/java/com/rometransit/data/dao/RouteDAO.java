package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.Route;
import com.rometransit.model.enums.TransportType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Route entity
 */
public class RouteDAO {
    private final SQLiteDatabaseManager dbManager;

    public RouteDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single route
     */
    public void insert(Route route) throws SQLException {
        String sql = "INSERT OR REPLACE INTO routes " +
                    "(route_id, agency_id, route_short_name, route_long_name, route_desc, " +
                    "route_type, route_url, route_color, route_text_color, route_sort_order) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            route.getRouteId(),
            route.getAgencyId(),
            route.getRouteShortName(),
            route.getRouteLongName(),
            route.getRouteDesc(),
            route.getRouteType() != null ? route.getRouteType().getGtfsCode() : 0,
            route.getRouteUrl(),
            route.getRouteColor(),
            route.getRouteTextColor(),
            route.getRouteSortOrder()
        );
    }

    /**
     * Insert multiple routes in batch
     */
    public void insertBatch(List<Route> routes) throws SQLException {
        if (routes == null || routes.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO routes " +
                    "(route_id, agency_id, route_short_name, route_long_name, route_desc, " +
                    "route_type, route_url, route_color, route_text_color, route_sort_order) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                Route route = routes.get(index);
                stmt.setString(1, route.getRouteId());
                stmt.setString(2, route.getAgencyId());
                stmt.setString(3, route.getRouteShortName());
                stmt.setString(4, route.getRouteLongName());
                stmt.setString(5, route.getRouteDesc());
                stmt.setInt(6, route.getRouteType() != null ? route.getRouteType().getGtfsCode() : 0);
                stmt.setString(7, route.getRouteUrl());
                stmt.setString(8, route.getRouteColor());
                stmt.setString(9, route.getRouteTextColor());
                stmt.setInt(10, route.getRouteSortOrder());
            }

            @Override
            public int getBatchSize() {
                return routes.size();
            }
        });
    }

    /**
     * Find route by ID
     */
    public Optional<Route> findById(String routeId) throws SQLException {
        String sql = "SELECT * FROM routes WHERE route_id = ?";

        return Optional.ofNullable(dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return mapResultSetToRoute(rs);
            }
            return null;
        }, routeId));
    }

    /**
     * Find all routes
     */
    public List<Route> findAll() throws SQLException {
        String sql = "SELECT * FROM routes ORDER BY route_short_name";

        return dbManager.executeQuery(sql, rs -> {
            List<Route> routes = new ArrayList<>();
            while (rs.next()) {
                routes.add(mapResultSetToRoute(rs));
            }
            return routes;
        });
    }

    /**
     * Search routes by name (short or long)
     */
    public List<Route> searchByName(String query) throws SQLException {
        String sql = "SELECT * FROM routes " +
                    "WHERE route_short_name LIKE ? OR route_long_name LIKE ? " +
                    "ORDER BY route_short_name " +
                    "LIMIT 50";

        String searchPattern = "%" + query + "%";

        return dbManager.executeQuery(sql, rs -> {
            List<Route> routes = new ArrayList<>();
            while (rs.next()) {
                routes.add(mapResultSetToRoute(rs));
            }
            return routes;
        }, searchPattern, searchPattern);
    }

    /**
     * Find routes by agency
     */
    public List<Route> findByAgency(String agencyId) throws SQLException {
        String sql = "SELECT * FROM routes WHERE agency_id = ? ORDER BY route_short_name";

        return dbManager.executeQuery(sql, rs -> {
            List<Route> routes = new ArrayList<>();
            while (rs.next()) {
                routes.add(mapResultSetToRoute(rs));
            }
            return routes;
        }, agencyId);
    }

    /**
     * Delete route by ID
     */
    public void delete(String routeId) throws SQLException {
        String sql = "DELETE FROM routes WHERE route_id = ?";
        dbManager.executeUpdate(sql, routeId);
    }

    /**
     * Count total routes
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM routes";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all routes
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM routes");
    }

    /**
     * Map ResultSet to Route object
     */
    private Route mapResultSetToRoute(ResultSet rs) throws SQLException {
        Route route = new Route();
        route.setRouteId(rs.getString("route_id"));
        route.setAgencyId(rs.getString("agency_id"));
        route.setRouteShortName(rs.getString("route_short_name"));
        route.setRouteLongName(rs.getString("route_long_name"));
        route.setRouteDesc(rs.getString("route_desc"));
        route.setRouteType(TransportType.fromGtfsCode(rs.getInt("route_type")));
        route.setRouteUrl(rs.getString("route_url"));
        route.setRouteColor(rs.getString("route_color"));
        route.setRouteTextColor(rs.getString("route_text_color"));
        route.setRouteSortOrder(rs.getInt("route_sort_order"));
        return route;
    }
}
