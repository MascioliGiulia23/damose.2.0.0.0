package com.rometransit.data.dao;

import com.rometransit.data.database.SQLiteDatabaseManager;
import com.rometransit.model.entity.Agency;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for Agency entity
 */
public class AgencyDAO {
    private final SQLiteDatabaseManager dbManager;

    public AgencyDAO() {
        this.dbManager = SQLiteDatabaseManager.getInstance();
    }

    /**
     * Insert a single agency
     */
    public void insert(Agency agency) throws SQLException {
        String sql = "INSERT OR REPLACE INTO agencies " +
                    "(agency_id, agency_name, agency_url, agency_timezone, agency_lang, " +
                    "agency_phone, agency_fare_url, agency_email) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeUpdate(sql,
            agency.getAgencyId(),
            agency.getAgencyName(),
            agency.getAgencyUrl(),
            agency.getAgencyTimezone(),
            agency.getAgencyLang(),
            agency.getAgencyPhone(),
            agency.getAgencyFareUrl(),
            agency.getAgencyEmail()
        );
    }

    /**
     * Insert multiple agencies in batch
     */
    public void insertBatch(List<Agency> agencies) throws SQLException {
        if (agencies == null || agencies.isEmpty()) {
            return;
        }

        String sql = "INSERT OR REPLACE INTO agencies " +
                    "(agency_id, agency_name, agency_url, agency_timezone, agency_lang, " +
                    "agency_phone, agency_fare_url, agency_email) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        dbManager.executeBatch(sql, new SQLiteDatabaseManager.BatchParameterSetter() {
            @Override
            public void setValues(PreparedStatement stmt, int index) throws SQLException {
                Agency agency = agencies.get(index);
                stmt.setString(1, agency.getAgencyId());
                stmt.setString(2, agency.getAgencyName());
                stmt.setString(3, agency.getAgencyUrl());
                stmt.setString(4, agency.getAgencyTimezone());
                stmt.setString(5, agency.getAgencyLang());
                stmt.setString(6, agency.getAgencyPhone());
                stmt.setString(7, agency.getAgencyFareUrl());
                stmt.setString(8, agency.getAgencyEmail());
            }

            @Override
            public int getBatchSize() {
                return agencies.size();
            }
        });
    }

    /**
     * Find agency by ID
     */
    public Optional<Agency> findById(String agencyId) throws SQLException {
        String sql = "SELECT * FROM agencies WHERE agency_id = ?";

        return Optional.ofNullable(dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return mapResultSetToAgency(rs);
            }
            return null;
        }, agencyId));
    }

    /**
     * Find all agencies
     */
    public List<Agency> findAll() throws SQLException {
        String sql = "SELECT * FROM agencies ORDER BY agency_name";

        return dbManager.executeQuery(sql, rs -> {
            List<Agency> agencies = new ArrayList<>();
            while (rs.next()) {
                agencies.add(mapResultSetToAgency(rs));
            }
            return agencies;
        });
    }

    /**
     * Delete agency by ID
     */
    public void delete(String agencyId) throws SQLException {
        String sql = "DELETE FROM agencies WHERE agency_id = ?";
        dbManager.executeUpdate(sql, agencyId);
    }

    /**
     * Count total agencies
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM agencies";
        return dbManager.executeQuery(sql, rs -> {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        });
    }

    /**
     * Delete all agencies
     */
    public void deleteAll() throws SQLException {
        dbManager.executeUpdate("DELETE FROM agencies");
    }

    /**
     * Map ResultSet to Agency object
     */
    private Agency mapResultSetToAgency(ResultSet rs) throws SQLException {
        Agency agency = new Agency();
        agency.setAgencyId(rs.getString("agency_id"));
        agency.setAgencyName(rs.getString("agency_name"));
        agency.setAgencyUrl(rs.getString("agency_url"));
        agency.setAgencyTimezone(rs.getString("agency_timezone"));
        agency.setAgencyLang(rs.getString("agency_lang"));
        agency.setAgencyPhone(rs.getString("agency_phone"));
        agency.setAgencyFareUrl(rs.getString("agency_fare_url"));
        agency.setAgencyEmail(rs.getString("agency_email"));
        return agency;
    }
}
