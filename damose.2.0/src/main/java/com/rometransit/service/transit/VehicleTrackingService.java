package com.rometransit.service.transit;

import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.entity.Route;
import com.rometransit.model.enums.VehicleStatus;
import com.rometransit.util.math.GeoUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class VehicleTrackingService {
    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;

    public VehicleTrackingService() {
        this.vehicleRepository = new VehicleRepository();
        this.routeRepository = new RouteRepository();
    }

    public List<VehiclePosition> getAllActiveVehicles() {
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
        return activeVehicles.stream()
                .map(this::convertToVehiclePosition)
                .collect(Collectors.toList());
    }

    public List<VehiclePosition> getVehiclesForRoute(String routeId) {
        List<Vehicle> vehicles = vehicleRepository.findActiveVehiclesForRoute(routeId);
        return vehicles.stream()
                .map(this::convertToVehiclePosition)
                .collect(Collectors.toList());
    }

    public List<VehiclePosition> getVehiclesNearLocation(double latitude, double longitude, double radiusKm) {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            return new ArrayList<>();
        }

        List<Vehicle> nearbyVehicles = vehicleRepository.findNearby(latitude, longitude, radiusKm);
        return nearbyVehicles.stream()
                .map(this::convertToVehiclePosition)
                .sorted((v1, v2) -> {
                    double dist1 = GeoUtils.calculateDistance(latitude, longitude, v1.getLatitude(), v1.getLongitude());
                    double dist2 = GeoUtils.calculateDistance(latitude, longitude, v2.getLatitude(), v2.getLongitude());
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
    }

    public Optional<VehiclePosition> getVehicleById(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(this::convertToVehiclePosition);
    }

    public List<VehiclePosition> getVehiclesByStatus(VehicleStatus status) {
        List<Vehicle> vehicles = vehicleRepository.findByStatus(status);
        return vehicles.stream()
                .map(this::convertToVehiclePosition)
                .collect(Collectors.toList());
    }

    public void updateVehiclePosition(String vehicleId, double latitude, double longitude,
                                    double bearing, double speed, VehicleStatus status) {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("Invalid coordinates: " + latitude + ", " + longitude);
        }

        Optional<Vehicle> existingVehicle = vehicleRepository.findById(vehicleId);
        Vehicle vehicle;

        if (existingVehicle.isPresent()) {
            vehicle = existingVehicle.get();
        } else {
            vehicle = new Vehicle();
            vehicle.setVehicleId(vehicleId);
        }

        vehicle.setLatitude(latitude);
        vehicle.setLongitude(longitude);
        vehicle.setBearing((float) bearing); // Vehicle entity uses float
        vehicle.setSpeed(speed);
        vehicle.setCurrentStatus(status);
        vehicle.setTimestamp(LocalDateTime.now());

        vehicleRepository.save(vehicle);
    }

    public void updateVehicleInfo(String vehicleId, String tripId, String routeId, String headsign) {
        Optional<Vehicle> vehicleOpt = vehicleRepository.findById(vehicleId);
        if (vehicleOpt.isPresent()) {
            Vehicle vehicle = vehicleOpt.get();
            vehicle.setTripId(tripId);
            vehicle.setRouteId(routeId);
            // Note: headsign would be stored in Trip entity normally
            vehicle.setTimestamp(LocalDateTime.now());
            vehicleRepository.save(vehicle);
        }
    }

    public List<VehiclePosition> getStaleVehicles(int minutesThreshold) {
        List<Vehicle> staleVehicles = vehicleRepository.findStaleVehicles(minutesThreshold);
        return staleVehicles.stream()
                .map(this::convertToVehiclePosition)
                .collect(Collectors.toList());
    }

    public void removeStaleVehicles(int minutesThreshold) {
        vehicleRepository.cleanupStaleVehicles(minutesThreshold);
    }

    public Map<String, Long> getVehicleCountByRoute() {
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
        return activeVehicles.stream()
                .filter(v -> v.getRouteId() != null)
                .collect(Collectors.groupingBy(Vehicle::getRouteId, Collectors.counting()));
    }

    public Map<VehicleStatus, Long> getVehicleCountByStatus() {
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
        return activeVehicles.stream()
                .filter(v -> v.getCurrentStatus() != null)
                .collect(Collectors.groupingBy(Vehicle::getCurrentStatus, Collectors.counting()));
    }

    public double getAverageSpeedForRoute(String routeId) {
        List<Vehicle> vehicles = vehicleRepository.findActiveVehiclesForRoute(routeId);
        return vehicles.stream()
                .mapToDouble(Vehicle::getSpeed)
                .filter(speed -> speed > 0)
                .average()
                .orElse(0.0);
    }

    public List<VehiclePosition> getVehiclesInBoundingBox(double minLat, double minLon, 
                                                         double maxLat, double maxLon) {
        List<Vehicle> allVehicles = vehicleRepository.findActiveVehicles();
        return allVehicles.stream()
                .filter(v -> v.getLatitude() >= minLat && v.getLatitude() <= maxLat &&
                            v.getLongitude() >= minLon && v.getLongitude() <= maxLon)
                .map(this::convertToVehiclePosition)
                .collect(Collectors.toList());
    }

    public VehiclePosition findNearestVehicle(double latitude, double longitude, String routeId) {
        List<Vehicle> vehicles = routeId != null ? 
                vehicleRepository.findActiveVehiclesForRoute(routeId) :
                vehicleRepository.findActiveVehicles();

        return vehicles.stream()
                .filter(v -> GeoUtils.isValidCoordinate(v.getLatitude(), v.getLongitude()))
                .min((v1, v2) -> {
                    double dist1 = GeoUtils.calculateDistance(latitude, longitude, v1.getLatitude(), v1.getLongitude());
                    double dist2 = GeoUtils.calculateDistance(latitude, longitude, v2.getLatitude(), v2.getLongitude());
                    return Double.compare(dist1, dist2);
                })
                .map(this::convertToVehiclePosition)
                .orElse(null);
    }

    public List<String> getActiveRouteIds() {
        return vehicleRepository.getActiveRoutes();
    }

    public boolean isVehicleActive(String vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .map(vehicle -> {
                    if (vehicle.getTimestamp() == null) return false;
                    return vehicle.getTimestamp().isAfter(LocalDateTime.now().minusMinutes(10));
                })
                .orElse(false);
    }

    private VehiclePosition convertToVehiclePosition(Vehicle vehicle) {
        Optional<Route> routeOpt = vehicle.getRouteId() != null ?
                routeRepository.findById(vehicle.getRouteId()) : Optional.empty();

        VehiclePosition position = new VehiclePosition(vehicle, routeOpt.orElse(null));

        // Calculate additional fields
        if (vehicle.getTimestamp() != null) {
            long minutesSinceUpdate = java.time.Duration.between(vehicle.getTimestamp(), LocalDateTime.now()).toMinutes();
            position.setTracked(minutesSinceUpdate <= 5);
        }

        return position;
    }

    public long getTotalActiveVehicles() {
        return vehicleRepository.countActiveVehicles();
    }

    public Map<String, Object> getTrackingStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalActive = getTotalActiveVehicles();
        Map<String, Long> byRoute = getVehicleCountByRoute();
        Map<VehicleStatus, Long> byStatus = getVehicleCountByStatus();
        
        stats.put("totalActive", totalActive);
        stats.put("byRoute", byRoute);
        stats.put("byStatus", byStatus);
        stats.put("lastUpdate", LocalDateTime.now());
        
        return stats;
    }
}