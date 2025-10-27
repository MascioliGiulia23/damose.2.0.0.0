package com.rometransit.data.repository;

import com.rometransit.model.entity.Trip;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legacy TripRepository stub for backward compatibility
 *
 * @deprecated Use {@link GTFSRepository} instead for GTFS data access
 */
@Deprecated
public class TripRepository {

    private final GTFSRepository gtfsRepository;

    public TripRepository() {
        this.gtfsRepository = GTFSRepository.getInstance();
        System.out.println("⚠️  TripRepository stub - use GTFSRepository instead");
    }

    public Optional<Trip> findById(String tripId) {
        try {
            return gtfsRepository.getTrip(tripId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Trip> findAll() {
        try {
            return gtfsRepository.loadTrips();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Trip> findByRoute(String routeId) {
        try {
            return gtfsRepository.getTripsByRoute(routeId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void save(Trip trip) {
        try {
            gtfsRepository.saveTrips(List.of(trip));
        } catch (Exception e) {
            System.err.println("Failed to save trip: " + e.getMessage());
        }
    }

    public void delete(String tripId) {
        // No-op stub
    }

    public long count() {
        try {
            return gtfsRepository.loadTrips().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Trip> findByRouteId(String routeId) {
        return findByRoute(routeId);
    }

    public List<Trip> findByDirection(String routeId, int directionId) {
        try {
            return gtfsRepository.getTripsByRoute(routeId).stream()
                    .filter(trip -> trip.getDirectionId() == directionId)
                    .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
