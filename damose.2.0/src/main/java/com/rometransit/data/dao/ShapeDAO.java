package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.Shape;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for Shape entity
 */
public class ShapeDAO {
    private final SQLiteDatabaseManager dbManager;

    public ShapeDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single shape point
     */
    public void insert(Shape shape) throws SQLException {
        String sql = "INSERT OR REPLACE INTO shapes " +
                    "(shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled) " +
                    "VALUES (?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            shape.getShapeId(),
            shape.getShapePtLat(),
            shape.getShapePtLon(),
            shape.getShapePtSequence(),
            shape.getShapeDistTraveled()
        );
    }

    /**
     * Insert multiple shape points in batch
     */
    public void insertBatch(List<Shape> shapes) throws SQLException {
        if (shapes == null || shapes.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO shapes " +
                    "(shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence, shape_dist_traveled) " +
                    "VALUES (?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                Shape shape = shapes.get(index);
                stmt.setString(1, shape.getShapeId());
                stmt.setDouble(2, shape.getShapePtLat());
                stmt.setDouble(3, shape.getShapePtLon());
                stmt.setInt(4, shape.getShapePtSequence());
                stmt.setDouble(5, shape.getShapeDistTraveled());
            }

            @Override
            public int getBatchSize() {
                return shapes.size();
            }
        });
    }

    /**
     * Find shape points by shape ID
     */
    public List<Shape> findByShapeId(String shapeId) throws SQLException {
        String sql = "SELECT * FROM shapes WHERE shape_id = ? ORDER BY shape_pt_sequence";

        return dbManager.executeQuery(sql, rs -> {
            List<Shape> shapes = new ArrayList<>();
            while (rs.next()) {
                shapes.add(mapResultSetToShape(rs));
            }
            return shapes;
        }, shapeId);
    }

    /**
     * Find all shapes (careful: can be many records!)
     */
    public List<Shape> findAll() throws SQLException {
        String sql = "SELECT * FROM shapes ORDER BY shape_id, shape_pt_sequence";

        return dbManager.executeQuery(sql, rs -> {
            List<Shape> shapes = new ArrayList<>();
            while (rs.next()) {
                shapes.add(mapResultSetToShape(rs));
            }
            return shapes;
        });
    }

    /**
     * Delete shapes by shape ID
     */
    public void deleteByShapeId(String shapeId) throws SQLException {
        String sql = "DELETE FROM shapes WHERE shape_id = ?";
        dbManager.executeUpdate(sql, shapeId);
    }

    /**
     * Count total shape points
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM shapes";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Count distinct shapes
     */
    public int countDistinctShapes() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT shape_id) FROM shapes";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all shapes
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM shapes");
    }

    /**
     * Map ResultSet to Shape object
     */
    private Shape mapResultSetToShape(ResultSet rs) throws SQLException {
        Shape shape = new Shape();
        shape.setShapeId(rs.getString("shape_id"));
        shape.setShapePtLat(rs.getDouble("shape_pt_lat"));
        shape.setShapePtLon(rs.getDouble("shape_pt_lon"));
        shape.setShapePtSequence(rs.getInt("shape_pt_sequence"));
        shape.setShapeDistTraveled(rs.getDouble("shape_dist_traveled"));
        return shape;
    }
}
