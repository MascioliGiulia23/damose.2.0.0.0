package com.rometransit.model.converter;

import com.rometransit.model.dto.VehiclePosition;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.entity.Route;
import com.rometransit.model.enums.VehicleStatus;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.util.logging.Logger;

import java.time.LocalDateTime;

/**
 * Converter for transforming VehiclePosition DTOs to Vehicle entities
 *
 * Responsibilities:
 * - Convert DTO → Entity
 * - Enrich with GTFS static data (route info)
 * - Estimate capacity when not provided
 * - Calculate occupancy levels
 * - Validate and sanitize data
 */
public class VehiclePositionConverter {

    private static final Logger logger = Logger.getLogger(VehiclePositionConverter.class);

    // Capacità veicoli realistiche per Roma
    // Fonte: dati ATAC e specifiche tecniche mezzi pubblici Roma
    private static final int DEFAULT_BUS_CAPACITY = 90;           // Autobus urbano standard (es. Irisbus Citelis)
    private static final int DEFAULT_BUS_ARTICULATED_CAPACITY = 150; // Autobus snodato
    private static final int DEFAULT_METRO_CAPACITY = 1200;       // Metro A/B/C (6 carrozze)
    private static final int DEFAULT_TRAM_CAPACITY = 180;         // Tram (es. CAF Urbos 100)
    private static final int DEFAULT_TRAIN_CAPACITY = 600;        // Treno regionale FL
    private static final int DEFAULT_GENERIC_CAPACITY = 90;

    // Occupancy thresholds
    private static final double LOW_OCCUPANCY_THRESHOLD = 0.5;   // < 50%
    private static final double MEDIUM_OCCUPANCY_THRESHOLD = 0.8; // 50-80%
    // > 80% = HIGH occupancy

    private final GTFSDataManager gtfsDataManager;

    public VehiclePositionConverter() {
        this.gtfsDataManager = GTFSDataManager.getInstance();
    }

    /**
     * Convert VehiclePosition DTO to Vehicle entity
     * @param position VehiclePosition DTO from real-time feed
     * @return Vehicle entity ready to save to database
     */
    public Vehicle convertToEntity(VehiclePosition position) {
        if (position == null) {
            throw new IllegalArgumentException("VehiclePosition cannot be null");
        }

        Vehicle vehicle = new Vehicle();

        // Basic identification
        vehicle.setVehicleId(position.getVehicleId());
        vehicle.setTripId(position.getTripId());
        vehicle.setRouteId(position.getRouteId());

        // Position data
        vehicle.setLatitude(position.getLatitude());
        vehicle.setLongitude(position.getLongitude());
        vehicle.setBearing((float) position.getBearing());
        vehicle.setSpeed(position.getSpeed());

        // Status
        VehicleStatus status = position.getStatus() != null ?
                position.getStatus() : VehicleStatus.IN_TRANSIT_TO;
        vehicle.setCurrentStatus(status);

        // Timestamp
        LocalDateTime timestamp = position.getLastUpdate() != null ?
                position.getLastUpdate() : LocalDateTime.now();
        vehicle.setTimestamp(timestamp);

        // Current stop
        if (position.getCurrentStopId() != null) {
            vehicle.setStopId(position.getCurrentStopId());
        }

        // Enrichment: Get capacity and occupancy
        enrichWithCapacityAndOccupancy(vehicle, position);

        // Validation
        validateVehicle(vehicle);

        return vehicle;
    }

    /**
     * Enrich vehicle with capacity and occupancy information
     */
    private void enrichWithCapacityAndOccupancy(Vehicle vehicle, VehiclePosition position) {
        // Estimate capacity from route type
        int capacity = estimateCapacity(vehicle.getRouteId());
        vehicle.setCapacity(capacity);

        // Get occupancy level from position (percentage 0-100)
        int occupancyLevel = position.getOccupancyLevel();

        // If occupancy level is provided from feed, use it
        if (occupancyLevel > 0) {
            // Calculate actual passenger count from percentage
            int passengerCount = (int) (capacity * (occupancyLevel / 100.0));
            vehicle.setOccupancyStatus(passengerCount);

            logger.debug("Vehicle " + vehicle.getVehicleId() + " occupancy: " +
                        passengerCount + "/" + capacity + " (" + occupancyLevel + "%)");
        } else {
            // No occupancy data from feed
            // Check if simulation mode is enabled for testing
            if (isSimulationModeEnabled()) {
                // SIMULATION MODE: Generate realistic test data
                int simulatedPassengers = simulateOccupancy(capacity, vehicle.getVehicleId());
                vehicle.setOccupancyStatus(simulatedPassengers);

                logger.info("⚠️ SIMULATION MODE: Vehicle " + vehicle.getVehicleId() +
                           " simulated occupancy: " + simulatedPassengers + "/" + capacity +
                           " (" + (int)((simulatedPassengers * 100.0) / capacity) + "%)");
            } else {
                // Production mode: No occupancy data available
                vehicle.setOccupancyStatus(0);

                logger.debug("No occupancy data for vehicle " + vehicle.getVehicleId() +
                            ", defaulting to 0 (unknown)");
            }
        }
    }

    /**
     * Check if simulation mode is enabled.
     * Enable with JVM flag: -Dsimulate.occupancy=true
     *
     * @return true if simulation mode is enabled
     */
    private boolean isSimulationModeEnabled() {
        return Boolean.getBoolean("simulate.occupancy");
    }

    /**
     * Simulate realistic occupancy data for testing purposes.
     * Uses vehicle ID as seed for consistent but varied results.
     *
     * @param capacity Vehicle capacity
     * @param vehicleId Vehicle ID (used as seed)
     * @return Simulated passenger count
     */
    private int simulateOccupancy(int capacity, String vehicleId) {
        // Use vehicle ID hash as seed for consistent results
        int seed = Math.abs(vehicleId != null ? vehicleId.hashCode() : 0);

        // Generate pseudo-random occupancy level (0-100%)
        // Weighted towards realistic patterns:
        // - 30% chance of LOW occupancy (0-40%)
        // - 50% chance of MEDIUM occupancy (40-80%)
        // - 20% chance of HIGH occupancy (80-100%)

        int random = (seed % 100);

        int occupancyPercentage;
        if (random < 30) {
            // LOW occupancy (0-40%)
            occupancyPercentage = (seed % 40);
        } else if (random < 80) {
            // MEDIUM occupancy (40-80%)
            occupancyPercentage = 40 + (seed % 40);
        } else {
            // HIGH occupancy (80-100%)
            occupancyPercentage = 80 + (seed % 20);
        }

        // Add some variation based on time of day (simulate rush hour)
        int hour = java.time.LocalTime.now().getHour();
        if (hour >= 7 && hour <= 9) {
            // Morning rush hour: increase occupancy
            occupancyPercentage = Math.min(100, occupancyPercentage + 15);
        } else if (hour >= 17 && hour <= 19) {
            // Evening rush hour: increase occupancy
            occupancyPercentage = Math.min(100, occupancyPercentage + 20);
        } else if (hour >= 1 && hour <= 5) {
            // Late night: decrease occupancy
            occupancyPercentage = Math.max(0, occupancyPercentage - 30);
        }

        int passengerCount = (int) (capacity * (occupancyPercentage / 100.0));
        return Math.max(0, Math.min(capacity, passengerCount));
    }

    /**
     * Estimate vehicle capacity based on route type
     * @param routeId Route ID
     * @return Estimated capacity
     */
    private int estimateCapacity(String routeId) {
        if (routeId == null || routeId.isEmpty()) {
            logger.debug("No route ID provided, using default generic capacity");
            return DEFAULT_GENERIC_CAPACITY;
        }

        try {
            Route route = gtfsDataManager.getRouteById(routeId);

            if (route == null) {
                logger.debug("Route not found: " + routeId + ", using default capacity");
                return DEFAULT_GENERIC_CAPACITY;
            }

            // Determina capacità in base al tipo di mezzo
            // GTFS route_type: BUS=0/3, TRAM=0/1, METRO=1, TRAIN=2, FERRY=4
            int routeType = route.getRouteType() != null ? route.getRouteType().getGtfsType() : -1;

            if (routeType == 0 || routeType == 3) { // Bus (route_type 3 = autobus GTFS)
                // Distingui tra autobus normale e snodato (se disponibile nel routeId)
                String routeIdLower = routeId.toLowerCase();
                if (routeIdLower.contains("snodato") || routeIdLower.contains("articulated")) {
                    return DEFAULT_BUS_ARTICULATED_CAPACITY; // 150 posti
                }
                return DEFAULT_BUS_CAPACITY; // 90 posti
            } else if (routeType == 1) { // Tram
                return DEFAULT_TRAM_CAPACITY; // 180 posti
            } else if (routeType == 2) { // Metro
                return DEFAULT_METRO_CAPACITY; // 1200 posti
            } else if (routeType == 100 || routeType == 101 || routeType == 102) { // Treni regionali FL
                return DEFAULT_TRAIN_CAPACITY; // 600 posti
            } else if (routeType == 4) { // Ferry (non presente a Roma)
                return 300;
            } else {
                logger.debug("Tipo mezzo sconosciuto: " + routeType +
                           " per linea " + routeId + ", uso capacità predefinita");
                return DEFAULT_GENERIC_CAPACITY;
            }

        } catch (Exception e) {
            logger.warn("Error getting route info for capacity estimation: " + e.getMessage());
            return DEFAULT_GENERIC_CAPACITY;
        }
    }

    /**
     * Calculate occupancy level (LOW, MEDIUM, HIGH)
     * @param occupancy Current passengers
     * @param capacity Maximum capacity
     * @return Occupancy level string
     */
    public String calculateOccupancyLevel(int occupancy, int capacity) {
        if (capacity <= 0 || occupancy <= 0) {
            return "UNKNOWN";
        }

        double ratio = (double) occupancy / capacity;

        if (ratio < LOW_OCCUPANCY_THRESHOLD) {
            return "LOW";
        } else if (ratio < MEDIUM_OCCUPANCY_THRESHOLD) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    /**
     * Calculate occupancy percentage
     * @param occupancy Current passengers
     * @param capacity Maximum capacity
     * @return Percentage (0-100)
     */
    public double calculateOccupancyPercentage(int occupancy, int capacity) {
        if (capacity <= 0) {
            return 0.0;
        }

        return Math.min(100.0, (double) occupancy / capacity * 100.0);
    }

    /**
     * Validate vehicle data
     * @param vehicle Vehicle entity to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateVehicle(Vehicle vehicle) {
        // Vehicle ID is required
        if (vehicle.getVehicleId() == null || vehicle.getVehicleId().isEmpty()) {
            throw new IllegalArgumentException("Vehicle ID is required");
        }

        // Coordinates must be valid
        if (!isValidCoordinate(vehicle.getLatitude(), vehicle.getLongitude())) {
            throw new IllegalArgumentException("Invalid coordinates for vehicle " +
                    vehicle.getVehicleId() + ": [" + vehicle.getLatitude() + ", " +
                    vehicle.getLongitude() + "]");
        }

        // Bearing must be 0-360
        if (vehicle.getBearing() < 0 || vehicle.getBearing() > 360) {
            logger.warn("Invalid bearing " + vehicle.getBearing() + " for vehicle " +
                       vehicle.getVehicleId() + ", normalizing to 0-360 range");
            vehicle.setBearing((vehicle.getBearing() % 360 + 360) % 360);
        }

        // Speed must be non-negative
        if (vehicle.getSpeed() < 0) {
            logger.warn("Negative speed " + vehicle.getSpeed() + " for vehicle " +
                       vehicle.getVehicleId() + ", setting to 0");
            vehicle.setSpeed(0.0);
        }

        // Capacity must be positive
        if (vehicle.getCapacity() <= 0) {
            logger.warn("Invalid capacity " + vehicle.getCapacity() + " for vehicle " +
                       vehicle.getVehicleId() + ", setting to default");
            vehicle.setCapacity(DEFAULT_GENERIC_CAPACITY);
        }

        // Occupancy must be non-negative
        if (vehicle.getOccupancyStatus() < 0) {
            logger.warn("Negative occupancy " + vehicle.getOccupancyStatus() +
                       " for vehicle " + vehicle.getVehicleId() + ", setting to 0");
            vehicle.setOccupancyStatus(0);
        }
    }

    /**
     * Check if coordinates are valid
     * @param latitude Latitude
     * @param longitude Longitude
     * @return true if valid
     */
    private boolean isValidCoordinate(double latitude, double longitude) {
        // Basic validation
        if (latitude == 0.0 && longitude == 0.0) {
            return false; // Null island
        }

        // Latitude must be -90 to 90
        if (latitude < -90 || latitude > 90) {
            return false;
        }

        // Longitude must be -180 to 180
        if (longitude < -180 || longitude > 180) {
            return false;
        }

        // Optional: Check if coordinates are in expected region (Rome area)
        // Rome is approximately at 41.9°N, 12.5°E
        // We can add a sanity check for Italian coordinates
        if (latitude < 35 || latitude > 48) {
            logger.warn("Coordinates outside Italy bounds: [" + latitude + ", " + longitude + "]");
            // Still valid, just suspicious
        }

        if (longitude < 6 || longitude > 19) {
            logger.warn("Coordinates outside Italy bounds: [" + latitude + ", " + longitude + "]");
            // Still valid, just suspicious
        }

        return true;
    }

    /**
     * Convert multiple positions to entities
     * @param positions List of VehiclePosition DTOs
     * @return List of Vehicle entities
     */
    public java.util.List<Vehicle> convertToEntities(java.util.List<VehiclePosition> positions) {
        java.util.List<Vehicle> vehicles = new java.util.ArrayList<>();

        for (VehiclePosition position : positions) {
            try {
                Vehicle vehicle = convertToEntity(position);
                vehicles.add(vehicle);
            } catch (Exception e) {
                logger.error("Error converting vehicle " + position.getVehicleId() +
                           ": " + e.getMessage());
                // Continue with other vehicles
            }
        }

        return vehicles;
    }
}
