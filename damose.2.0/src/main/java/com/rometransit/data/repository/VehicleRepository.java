package com.rometransit.data.repository;

import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.Vehicle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legacy VehicleRepository stub for backward compatibility
 *
 * @deprecated Use {@link GTFSRepository} instead for vehicle position data access
 */
@Deprecated
public class VehicleRepository {

    private final GTFSRepository gtfsRepository;

    public VehicleRepository() {
        this.gtfsRepository = GTFSRepository.getInstance();
        System.out.println("⚠️  VehicleRepository stub - use GTFSRepository instead");
    }

    public Optional<Vehicle> findById(String vehicleId) {
        try {
            Optional<VehiclePosition> pos = gtfsRepository.getVehiclePosition(vehicleId);
            return pos.map(this::convertToVehicle);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<VehiclePosition> findPositionById(String vehicleId) {
        try {
            return gtfsRepository.getVehiclePosition(vehicleId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Vehicle> findAll() {
        try {
            return gtfsRepository.loadVehiclePositions().stream()
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<VehiclePosition> findAllPositions() {
        try {
            return gtfsRepository.loadVehiclePositions();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Vehicle> findByRoute(String routeId) {
        try {
            return gtfsRepository.getVehiclePositionsByRoute(routeId).stream()
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<VehiclePosition> findPositionsByRoute(String routeId) {
        try {
            return gtfsRepository.getVehiclePositionsByRoute(routeId);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void save(Vehicle vehicle) {
        // Convert Vehicle to VehiclePosition and save
        VehiclePosition position = new VehiclePosition();
        position.setVehicleId(vehicle.getVehicleId());
        position.setRouteId(vehicle.getRouteId());
        position.setTripId(vehicle.getTripId());
        position.setLatitude(vehicle.getLatitude());
        position.setLongitude(vehicle.getLongitude());
        position.setBearing(vehicle.getBearing());
        position.setSpeed(vehicle.getSpeed());
        position.setStatus(vehicle.getCurrentStatus());
        position.setLastUpdate(vehicle.getTimestamp());
        position.setOccupancyLevel(vehicle.getOccupancyStatus());
        position.setCurrentStopId(vehicle.getStopId());

        try {
            gtfsRepository.saveVehiclePositions(List.of(position));
        } catch (Exception e) {
            System.err.println("Failed to save vehicle: " + e.getMessage());
        }
    }

    public void save(VehiclePosition position) {
        try {
            gtfsRepository.saveVehiclePositions(List.of(position));
        } catch (Exception e) {
            System.err.println("Failed to save vehicle position: " + e.getMessage());
        }
    }

    public void delete(String vehicleId) {
        // No-op stub
    }

    public long count() {
        try {
            return gtfsRepository.loadVehiclePositions().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Vehicle> findActiveVehicles() {
        try {
            return gtfsRepository.loadVehiclePositions().stream()
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Vehicle> findActiveVehiclesForRoute(String routeId) {
        return findByRoute(routeId);
    }

    public List<Vehicle> findNearby(double lat, double lon, double radiusKm) {
        try {
            List<VehiclePosition> all = gtfsRepository.loadVehiclePositions();
            return all.stream()
                .filter(v -> {
                    double distance = calculateDistance(lat, lon, v.getLatitude(), v.getLongitude());
                    return distance <= radiusKm;
                })
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public long countActiveVehicles() {
        return count();
    }

    public List<Vehicle> findByStatus(com.rometransit.model.enums.VehicleStatus status) {
        try {
            return gtfsRepository.loadVehiclePositions().stream()
                .filter(v -> status.equals(v.getStatus()))
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Vehicle> findStaleVehicles(int minutesThreshold) {
        try {
            java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(minutesThreshold);
            return gtfsRepository.loadVehiclePositions().stream()
                .filter(v -> v.getLastUpdate() != null && v.getLastUpdate().isBefore(cutoff))
                .map(this::convertToVehicle)
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void cleanupStaleVehicles(int minutesThreshold) {
        // Auto-cleanup is handled by GTFSRepository when saving new positions
        // This is a no-op stub for compatibility
    }

    public List<String> getActiveRoutes() {
        try {
            return gtfsRepository.loadVehiclePositions().stream()
                .map(VehiclePosition::getRouteId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean exists(String vehicleId) {
        return findById(vehicleId).isPresent();
    }

    public List<Vehicle> findByRouteId(String routeId) {
        return findActiveVehiclesForRoute(routeId);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371; // Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    /**
     * Convert VehiclePosition to Vehicle entity
     */
    private Vehicle convertToVehicle(VehiclePosition pos) {
        Vehicle vehicle = new Vehicle();
        vehicle.setVehicleId(pos.getVehicleId());
        vehicle.setRouteId(pos.getRouteId());
        vehicle.setTripId(pos.getTripId());
        vehicle.setLatitude(pos.getLatitude());
        vehicle.setLongitude(pos.getLongitude());
        vehicle.setBearing((float) pos.getBearing());
        vehicle.setSpeed(pos.getSpeed());
        vehicle.setCurrentStatus(pos.getStatus());
        vehicle.setTimestamp(pos.getLastUpdate());
        vehicle.setOccupancyStatus(pos.getOccupancyLevel());
        vehicle.setStopId(pos.getCurrentStopId());
        return vehicle;
    }
}
