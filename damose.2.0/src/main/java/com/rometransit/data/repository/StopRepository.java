package com.rometransit.data.repository;

import com.rometransit.model.entity.Stop;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legacy StopRepository stub for backward compatibility
 *
 * @deprecated Use {@link GTFSRepository} instead for GTFS data access
 */
@Deprecated
public class StopRepository {

    private final GTFSRepository gtfsRepository;

    public StopRepository() {
        this.gtfsRepository = GTFSRepository.getInstance();
        System.out.println("⚠️  StopRepository stub - use GTFSRepository instead");
    }

    public Optional<Stop> findById(String stopId) {
        try {
            return gtfsRepository.getStop(stopId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Stop> findAll() {
        try {
            return gtfsRepository.loadStops();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Stop> searchByName(String query) {
        try {
            return gtfsRepository.searchStops(query);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void save(Stop stop) {
        try {
            gtfsRepository.saveStops(List.of(stop));
        } catch (Exception e) {
            System.err.println("Failed to save stop: " + e.getMessage());
        }
    }

    public void delete(String stopId) {
        // No-op stub
    }

    public long count() {
        try {
            return gtfsRepository.loadStops().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public Optional<Stop> findByStopCode(String stopCode) {
        try {
            return gtfsRepository.loadStops().stream()
                .filter(s -> stopCode.equals(s.getStopCode()))
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Stop> findNearby(double lat, double lon, double radiusKm) {
        try {
            double deltaLat = radiusKm / 111.0;
            double deltaLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
            return gtfsRepository.findNearbyStops(
                lat - deltaLat, lat + deltaLat,
                lon - deltaLon, lon + deltaLon
            );
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<Stop> stops) {
        try {
            gtfsRepository.saveStops(stops);
        } catch (Exception e) {
            System.err.println("Failed to save stops: " + e.getMessage());
        }
    }

    public void deleteAll() {
        // No-op stub
    }

    public List<Stop> findByFavorites(List<String> stopIds) {
        try {
            return gtfsRepository.loadStops().stream()
                .filter(s -> stopIds.contains(s.getStopId()))
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Stop> search(String query) {
        return searchByName(query);
    }
}
