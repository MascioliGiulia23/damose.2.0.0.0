package com.rometransit.service.dashboard;

import com.rometransit.model.entity.*;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.model.enums.VehicleStatus;
import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.data.repository.IncidentRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing transport quality and generating dashboard metrics
 * Uses real data from database repositories
 */
public class TransportQualityService {

    private final GTFSDataManager gtfsDataManager;
    private final VehicleRepository vehicleRepository;
    private final IncidentRepository incidentRepository;
    private TransportMetrics currentMetrics;

    public TransportQualityService(GTFSDataManager gtfsDataManager) {
        this.gtfsDataManager = gtfsDataManager;
        this.vehicleRepository = new VehicleRepository();
        this.incidentRepository = new IncidentRepository();
    }

    public TransportQualityService(GTFSDataManager gtfsDataManager, VehicleRepository vehicleRepository, IncidentRepository incidentRepository) {
        this.gtfsDataManager = gtfsDataManager;
        this.vehicleRepository = vehicleRepository;
        this.incidentRepository = incidentRepository;
    }

    /**
     * Get comprehensive transport quality metrics
     */
    public TransportMetrics getTransportMetrics() {
        updateMetrics();
        return currentMetrics;
    }

    /**
     * Get list of active vehicles with their status
     * Returns vehicles from database that have been updated in the last 10 minutes
     */
    public List<Vehicle> getActiveVehicles() {
        return vehicleRepository.findActiveVehicles();
    }

    /**
     * Get current incidents affecting transport
     * Returns active incidents from database
     */
    public List<TransportIncident> getCurrentIncidents() {
        return incidentRepository.findActiveIncidents();
    }

    /**
     * Get crowding data for all active vehicles
     * Returns list of VehicleCrowdingData sorted by occupancy percentage (highest first)
     * Uses real data from vehicle repository
     */
    public List<com.rometransit.model.dto.dashboard.VehicleCrowdingData> getCrowdingData() {
        List<com.rometransit.model.dto.dashboard.VehicleCrowdingData> crowdingList = new ArrayList<>();

        try {
            List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();

            for (Vehicle vehicle : activeVehicles) {
                // Skip vehicles without occupancy data
                if (vehicle.getCapacity() == 0) continue;

                // Get route information
                Route route = gtfsDataManager.getRouteById(vehicle.getRouteId());
                String routeName = "Unknown Route";

                if (route != null) {
                    routeName = route.getRouteLongName() != null && !route.getRouteLongName().isEmpty()
                            ? route.getRouteLongName()
                            : (route.getRouteShortName() != null ? route.getRouteShortName() : route.getRouteId());
                }

                // Create crowding data object
                com.rometransit.model.dto.dashboard.VehicleCrowdingData data =
                    new com.rometransit.model.dto.dashboard.VehicleCrowdingData(
                        vehicle.getRouteId(),
                        routeName,
                        vehicle.getOccupancyStatus(),
                        vehicle.getCapacity()
                    );

                crowdingList.add(data);
            }

            // Sort by percentage descending (most crowded first)
            crowdingList.sort((a, b) -> Double.compare(b.getPercentage(), a.getPercentage()));

        } catch (Exception e) {
            System.err.println("Error generating crowding data: " + e.getMessage());
        }

        return crowdingList;
    }

    /**
     * Get route performance analysis
     */
    public Map<String, RoutePerformance> getRoutePerformance() {
        Map<String, RoutePerformance> performance = new HashMap<>();

        try {
            List<Route> routes = gtfsDataManager.getAllRoutes();
            for (Route route : routes) {
                RoutePerformance perf = analyzeRoutePerformance(route);
                performance.put(route.getRouteId(), perf);
            }
        } catch (Exception e) {
            System.err.println("Error analyzing route performance: " + e.getMessage());
        }

        return performance;
    }

    /**
     * Get network-wide quality score (0-100)
     */
    public double getNetworkQualityScore() {
        double vehicleScore = calculateVehicleQualityScore();
        double incidentScore = calculateIncidentImpactScore();
        double punctualityScore = calculatePunctualityScore();
        double coverageScore = calculateCoverageScore();

        return (vehicleScore * 0.3 + incidentScore * 0.25 + punctualityScore * 0.25 + coverageScore * 0.2);
    }

    /**
     * Get alerts for transport quality issues
     * Uses real data from database repositories
     */
    public List<QualityAlert> getQualityAlerts() {
        List<QualityAlert> alerts = new ArrayList<>();

        List<TransportIncident> currentIncidents = incidentRepository.findActiveIncidents();
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();

        // Check for high incident areas
        if (currentIncidents.size() > 5) {
            alerts.add(new QualityAlert(
                QualityAlert.Severity.HIGH,
                "Alto numero di incidenti",
                currentIncidents.size() + " incidenti attivi nel network",
                LocalDateTime.now()
            ));
        }

        // Check for low vehicle availability
        long inactiveVehicles = activeVehicles.stream()
            .filter(v -> v.getCurrentStatus() == VehicleStatus.UNKNOWN)
            .count();

        if (!activeVehicles.isEmpty() && inactiveVehicles > activeVehicles.size() * 0.3) {
            alerts.add(new QualityAlert(
                QualityAlert.Severity.MEDIUM,
                "Disponibilità veicoli ridotta",
                String.format("%.0f%% dei veicoli non è attivo",
                    (inactiveVehicles * 100.0 / activeVehicles.size())),
                LocalDateTime.now()
            ));
        }

        // Check for high severity incidents
        long highSeverityCount = currentIncidents.stream()
            .filter(i -> i.getSeverity() == TransportIncident.Severity.HIGH)
            .count();

        if (highSeverityCount > 0) {
            alerts.add(new QualityAlert(
                QualityAlert.Severity.HIGH,
                "Incidenti ad alta gravità",
                highSeverityCount + " incident" + (highSeverityCount > 1 ? "i" : "e") + " ad alta gravità",
                LocalDateTime.now()
            ));
        }

        return alerts;
    }

    private void updateMetrics() {
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
        List<TransportIncident> currentIncidents = incidentRepository.findActiveIncidents();

        long activeCount = activeVehicles.stream()
            .filter(v -> v.getCurrentStatus() == VehicleStatus.IN_TRANSIT_TO || v.getCurrentStatus() == VehicleStatus.STOPPED_AT)
            .count();

        long inactiveCount = activeVehicles.stream()
            .filter(v -> v.getCurrentStatus() == VehicleStatus.UNKNOWN)
            .count();

        long maintenanceCount = activeVehicles.stream()
            .filter(v -> v.getCurrentStatus() == VehicleStatus.INCOMING_AT)
            .count();

        int highSeverityIncidents = (int) currentIncidents.stream()
            .filter(i -> i.getSeverity() == TransportIncident.Severity.HIGH)
            .count();

        currentMetrics = new TransportMetrics(
            activeCount,
            inactiveCount,
            maintenanceCount,
            currentIncidents.size(),
            highSeverityIncidents,
            0.0, // avgDelay - can be calculated from real-time data if available
            getNetworkQualityScore(),
            LocalDateTime.now()
        );
    }

    private RoutePerformance analyzeRoutePerformance(Route route) {
        List<Vehicle> routeVehicles = vehicleRepository.findByRouteId(route.getRouteId());
        List<TransportIncident> routeIncidents = incidentRepository.findByRoute(route.getRouteId());

        long vehicleCount = routeVehicles.size();
        int delay = 0; // Can be calculated from real-time data if available
        boolean hasIncidents = !routeIncidents.isEmpty();

        double reliability = calculateRouteReliability(delay, hasIncidents);

        return new RoutePerformance(
            route.getRouteShortName() != null ? route.getRouteShortName() : route.getRouteId(),
            (int) vehicleCount,
            delay,
            reliability,
            hasIncidents
        );
    }

    private double calculateRouteReliability(int delay, boolean hasIncidents) {
        double score = 100.0;

        // Penalty for delays
        score -= Math.min(delay * 2, 50); // Max 50 points penalty

        // Penalty for incidents
        if (hasIncidents) {
            score -= 20;
        }

        return Math.max(score, 0);
    }

    private double calculateVehicleQualityScore() {
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
        if (activeVehicles.isEmpty()) return 0;

        long activeCount = activeVehicles.stream()
            .filter(v -> v.getCurrentStatus() == VehicleStatus.IN_TRANSIT_TO || v.getCurrentStatus() == VehicleStatus.STOPPED_AT)
            .count();

        return (activeCount * 100.0) / activeVehicles.size();
    }

    private double calculateIncidentImpactScore() {
        List<TransportIncident> currentIncidents = incidentRepository.findActiveIncidents();
        if (currentIncidents.isEmpty()) return 100.0;

        double impact = currentIncidents.stream()
            .mapToDouble(i -> i.getSeverity() == TransportIncident.Severity.HIGH ? 15 :
                            i.getSeverity() == TransportIncident.Severity.MEDIUM ? 8 : 3)
            .sum();

        return Math.max(100 - impact, 0);
    }

    private double calculatePunctualityScore() {
        // Can be calculated from real-time delay data if available
        return 100.0;
    }

    private double calculateCoverageScore() {
        try {
            List<Vehicle> activeVehicles = vehicleRepository.findActiveVehicles();
            int totalRoutes = gtfsDataManager.getAllRoutes().size();
            int activeRoutes = (int) activeVehicles.stream()
                .map(Vehicle::getRouteId)
                .distinct()
                .count();

            return totalRoutes > 0 ? (activeRoutes * 100.0) / totalRoutes : 100.0;
        } catch (Exception e) {
            return 75.0; // Default coverage score
        }
    }

    // Inner classes for data structures
    public static class TransportMetrics {
        private final long activeVehicles;
        private final long inactiveVehicles;
        private final long maintenanceVehicles;
        private final int totalIncidents;
        private final int highSeverityIncidents;
        private final double averageDelay;
        private final double qualityScore;
        private final LocalDateTime timestamp;

        public TransportMetrics(long activeVehicles, long inactiveVehicles, long maintenanceVehicles,
                               int totalIncidents, int highSeverityIncidents, double averageDelay,
                               double qualityScore, LocalDateTime timestamp) {
            this.activeVehicles = activeVehicles;
            this.inactiveVehicles = inactiveVehicles;
            this.maintenanceVehicles = maintenanceVehicles;
            this.totalIncidents = totalIncidents;
            this.highSeverityIncidents = highSeverityIncidents;
            this.averageDelay = averageDelay;
            this.qualityScore = qualityScore;
            this.timestamp = timestamp;
        }

        // Getters
        public long getActiveVehicles() { return activeVehicles; }
        public long getInactiveVehicles() { return inactiveVehicles; }
        public long getMaintenanceVehicles() { return maintenanceVehicles; }
        public long getTotalVehicles() { return activeVehicles + inactiveVehicles + maintenanceVehicles; }
        public int getTotalIncidents() { return totalIncidents; }
        public int getHighSeverityIncidents() { return highSeverityIncidents; }
        public double getAverageDelay() { return averageDelay; }
        public double getQualityScore() { return qualityScore; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    public static class RoutePerformance {
        private final String routeName;
        private final int vehicleCount;
        private final int avgDelay;
        private final double reliability;
        private final boolean hasIncidents;

        public RoutePerformance(String routeName, int vehicleCount, int avgDelay, double reliability, boolean hasIncidents) {
            this.routeName = routeName;
            this.vehicleCount = vehicleCount;
            this.avgDelay = avgDelay;
            this.reliability = reliability;
            this.hasIncidents = hasIncidents;
        }

        public String getRouteName() { return routeName; }
        public int getVehicleCount() { return vehicleCount; }
        public int getAvgDelay() { return avgDelay; }
        public double getReliability() { return reliability; }
        public boolean hasIncidents() { return hasIncidents; }
    }

    public static class QualityAlert {
        public enum Severity { LOW, MEDIUM, HIGH }

        private final Severity severity;
        private final String title;
        private final String description;
        private final LocalDateTime timestamp;

        public QualityAlert(Severity severity, String title, String description, LocalDateTime timestamp) {
            this.severity = severity;
            this.title = title;
            this.description = description;
            this.timestamp = timestamp;
        }

        public Severity getSeverity() { return severity; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
}