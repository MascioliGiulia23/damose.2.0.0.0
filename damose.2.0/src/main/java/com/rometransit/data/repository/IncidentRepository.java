package com.rometransit.data.repository;

import com.rometransit.model.entity.TransportIncident;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legacy IncidentRepository stub for backward compatibility
 *
 * @deprecated Incidents are not yet migrated to SQLite - placeholder only
 */
@Deprecated
public class IncidentRepository {

    public IncidentRepository() {
        System.out.println("⚠️  IncidentRepository stub - incidents not yet in SQLite");
    }

    public Optional<TransportIncident> findById(String incidentId) {
        return Optional.empty();
    }

    public List<TransportIncident> findAll() {
        return new ArrayList<>();
    }

    public List<TransportIncident> findActive() {
        return new ArrayList<>();
    }

    public List<TransportIncident> findByRoute(String routeId) {
        return new ArrayList<>();
    }

    public void save(TransportIncident incident) {
        // No-op stub
    }

    public void delete(String incidentId) {
        // No-op stub
    }

    public long count() {
        return 0;
    }

    public void cleanupOldIncidents(int minutesThreshold) {
        // No-op stub
    }

    public int migrateOldDelaysToRitardi() {
        // No-op stub - returns 0 as no records were migrated
        return 0;
    }

    public List<TransportIncident> findActiveIncidents() {
        return findActive();
    }
}
