package com.rometransit.service.transit;

import com.rometransit.data.repository.StopRepository;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.data.repository.TripRepository;
import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Trip;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.entity.StopTime;
import com.rometransit.service.gtfs.GTFSDataManager;
import com.rometransit.util.math.GeoUtils;
import com.rometransit.util.math.TimeUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class ArrivalPredictionService {
    private static ArrivalPredictionService instance;

    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;
    private final GTFSDataManager gtfsDataManager;

    private ArrivalPredictionService() {
        this.stopRepository = new StopRepository();
        this.routeRepository = new RouteRepository();
        this.tripRepository = new TripRepository();
        this.vehicleRepository = new VehicleRepository();
        this.gtfsDataManager = GTFSDataManager.getInstance();
    }

    public static synchronized ArrivalPredictionService getInstance() {
        if (instance == null) {
            instance = new ArrivalPredictionService();
        }
        return instance;
    }

    public List<ArrivalPrediction> getPredictionsForStop(String stopId) {
        Optional<Stop> stopOpt = stopRepository.findById(stopId);
        if (!stopOpt.isPresent()) {
            return new ArrayList<>();
        }

        Stop stop = stopOpt.get();
        List<ArrivalPrediction> predictions = new ArrayList<>();

        // Get all routes serving this stop
        List<Route> routes = getRoutesForStop(stopId);

        for (Route route : routes) {
            List<ArrivalPrediction> routePredictions = getPredictionsForStopAndRoute(stop, route);
            predictions.addAll(routePredictions);
        }

        // Sort by predicted arrival time
        return predictions.stream()
                .sorted(Comparator.comparing(ArrivalPrediction::getPredictedArrival))
                .collect(Collectors.toList());
    }

    public List<ArrivalPrediction> getPredictionsForStopAndRoute(String stopId, String routeId) {
        Optional<Stop> stopOpt = stopRepository.findById(stopId);
        Optional<Route> routeOpt = routeRepository.findById(routeId);

        if (!stopOpt.isPresent() || !routeOpt.isPresent()) {
            return new ArrayList<>();
        }

        return getPredictionsForStopAndRoute(stopOpt.get(), routeOpt.get());
    }

    private List<ArrivalPrediction> getPredictionsForStopAndRoute(Stop stop, Route route) {
        List<ArrivalPrediction> predictions = new ArrayList<>();

        // Get active vehicles for this route
        List<Vehicle> activeVehicles = vehicleRepository.findActiveVehiclesForRoute(route.getRouteId());

        if (!activeVehicles.isEmpty()) {
            // Real-time predictions based on vehicle positions
            for (Vehicle vehicle : activeVehicles) {
                ArrivalPrediction prediction = createRealtimePrediction(stop, route, vehicle);
                if (prediction != null) {
                    predictions.add(prediction);
                }
            }
        } else {
            // Static schedule-based predictions
            predictions.addAll(createStaticPredictions(stop, route));
        }

        return predictions;
    }

    private ArrivalPrediction createRealtimePrediction(Stop stop, Route route, Vehicle vehicle) {
        if (vehicle.getLatitude() == 0 && vehicle.getLongitude() == 0) {
            return null;
        }

        // Calculate distance between vehicle and stop
        double distanceKm = GeoUtils.calculateDistance(
                vehicle.getLatitude(), vehicle.getLongitude(),
                stop.getStopLat(), stop.getStopLon()
        );

        // Estimate arrival time based on distance and average speed
        double averageSpeedKmh = vehicle.getSpeed() > 0 ? vehicle.getSpeed() * 3.6 : 15.0; // Default 15 km/h
        double estimatedMinutes = (distanceKm / averageSpeedKmh) * 60;

        LocalDateTime predictedArrival = LocalDateTime.now().plusMinutes((long) estimatedMinutes);
        
        ArrivalPrediction prediction = new ArrivalPrediction();
        prediction.setStop(stop);
        prediction.setRoute(route);
        prediction.setTripId(vehicle.getTripId());
        prediction.setVehicleId(vehicle.getVehicleId());
        prediction.setPredictedArrival(predictedArrival);
        prediction.setScheduledArrival(predictedArrival); // Simplified - would use actual schedule
        prediction.setRealtime(true);
        prediction.setConfidence(0.8);

        return prediction;
    }

    private List<ArrivalPrediction> createStaticPredictions(Stop stop, Route route) {
        List<ArrivalPrediction> predictions = new ArrayList<>();

        try {
            // Get real stop_times data from GTFS
            List<StopTime> stopTimes = gtfsDataManager.getStopTimesForStop(stop.getStopId());

            if (stopTimes.isEmpty()) {
                return predictions; // No scheduled times for this stop
            }

            LocalDateTime now = LocalDateTime.now();
            String currentTimeStr = String.format("%02d:%02d:%02d",
                now.getHour(), now.getMinute(), now.getSecond());

            // Filter stop_times for this specific route and upcoming times
            List<StopTime> upcomingTimes = stopTimes.stream()
                .filter(st -> {
                    // Check if this stop_time belongs to the route we're interested in
                    Trip trip = gtfsDataManager.getTripById(st.getTripId());
                    return trip != null && trip.getRouteId().equals(route.getRouteId());
                })
                .filter(st -> st.getArrivalTime() != null)
                .filter(st -> st.getArrivalTime().compareTo(currentTimeStr) > 0)
                .sorted((a, b) -> a.getArrivalTime().compareTo(b.getArrivalTime()))
                .limit(10) // Next 10 arrivals
                .collect(Collectors.toList());

            // Convert stop_times to ArrivalPredictions
            for (StopTime stopTime : upcomingTimes) {
                Trip trip = gtfsDataManager.getTripById(stopTime.getTripId());
                if (trip == null) continue;

                // Parse the arrival time (format: HH:MM:SS)
                String[] timeParts = stopTime.getArrivalTime().split(":");
                int hours = Integer.parseInt(timeParts[0]);
                int minutes = Integer.parseInt(timeParts[1]);
                int seconds = Integer.parseInt(timeParts[2]);

                // Handle times >= 24:00:00 (next day service)
                LocalDateTime scheduledArrival;
                if (hours >= 24) {
                    scheduledArrival = now.toLocalDate().plusDays(1)
                        .atTime(hours - 24, minutes, seconds);
                } else {
                    scheduledArrival = now.toLocalDate().atTime(hours, minutes, seconds);
                }

                // If the time has already passed today, assume it's for tomorrow
                if (scheduledArrival.isBefore(now)) {
                    scheduledArrival = scheduledArrival.plusDays(1);
                }

                ArrivalPrediction prediction = new ArrivalPrediction();
                prediction.setStop(stop);
                prediction.setRoute(route);
                prediction.setTripId(trip.getTripId());
                prediction.setHeadsign(trip.getTripHeadsign());
                prediction.setScheduledArrival(scheduledArrival);
                prediction.setPredictedArrival(scheduledArrival);
                prediction.setRealtime(false);
                prediction.setConfidence(0.7); // Static schedule confidence

                predictions.add(prediction);
            }

        } catch (Exception e) {
            System.err.println("Error creating static predictions: " + e.getMessage());
            e.printStackTrace();
        }

        return predictions;
    }

    private List<Route> getRoutesForStop(String stopId) {
        List<Route> routes = new ArrayList<>();
        Set<String> routeIds = new HashSet<>();

        try {
            // Get all stop times for this stop
            List<StopTime> stopTimes = gtfsDataManager.getStopTimesForStop(stopId);

            // Extract unique route IDs from trips
            for (StopTime stopTime : stopTimes) {
                Trip trip = gtfsDataManager.getTripById(stopTime.getTripId());
                if (trip != null) {
                    routeIds.add(trip.getRouteId());
                }
            }

            // Get route objects
            for (String routeId : routeIds) {
                Route route = gtfsDataManager.getRouteById(routeId);
                if (route != null) {
                    routes.add(route);
                }
            }

            // Sort routes by short name
            routes.sort((a, b) -> {
                try {
                    // Try to parse as numbers for proper sorting
                    int numA = Integer.parseInt(a.getRouteShortName().replaceAll("[^0-9]", ""));
                    int numB = Integer.parseInt(b.getRouteShortName().replaceAll("[^0-9]", ""));
                    return Integer.compare(numA, numB);
                } catch (Exception e) {
                    return a.getRouteShortName().compareTo(b.getRouteShortName());
                }
            });

        } catch (Exception e) {
            System.err.println("Error getting routes for stop: " + e.getMessage());
            e.printStackTrace();
        }

        return routes;
    }

    public List<ArrivalPrediction> getUpcomingArrivals(String stopId, int maxPredictions) {
        return getPredictionsForStop(stopId).stream()
                .filter(p -> p.getPredictedArrival().isAfter(LocalDateTime.now()))
                .limit(maxPredictions)
                .collect(Collectors.toList());
    }

    public List<ArrivalPrediction> getArrivalsInTimeWindow(String stopId, int windowMinutes) {
        LocalDateTime endTime = LocalDateTime.now().plusMinutes(windowMinutes);
        
        return getPredictionsForStop(stopId).stream()
                .filter(p -> p.getPredictedArrival().isBefore(endTime))
                .filter(p -> p.getPredictedArrival().isAfter(LocalDateTime.now()))
                .collect(Collectors.toList());
    }

    public Optional<ArrivalPrediction> getNextArrival(String stopId, String routeId) {
        List<ArrivalPrediction> predictions = getPredictionsForStopAndRoute(stopId, routeId);
        
        return predictions.stream()
                .filter(p -> p.getPredictedArrival().isAfter(LocalDateTime.now()))
                .findFirst();
    }

    public double calculateAverageWaitTime(String stopId, String routeId) {
        List<ArrivalPrediction> predictions = getPredictionsForStopAndRoute(stopId, routeId);
        
        if (predictions.size() < 2) {
            return 0.0;
        }

        double totalInterval = 0.0;
        for (int i = 1; i < predictions.size(); i++) {
            long minutes = TimeUtils.getMinutesBetween(
                    predictions.get(i - 1).getPredictedArrival(),
                    predictions.get(i).getPredictedArrival()
            );
            totalInterval += minutes;
        }

        return totalInterval / (predictions.size() - 1);
    }

    public List<ArrivalPrediction> getDelayedArrivals(String stopId) {
        return getPredictionsForStop(stopId).stream()
                .filter(p -> p.getDelayMinutes() > 5)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getDelayStatistics(String routeId) {
        Map<String, Integer> stats = new HashMap<>();
        
        List<Vehicle> vehicles = vehicleRepository.findByRouteId(routeId);
        
        int onTime = 0, delayed = 0, early = 0;
        
        for (Vehicle vehicle : vehicles) {
            // Simplified delay calculation
            // In reality, this would compare with scheduled times
            if (vehicle.getSpeed() < 5) { // Slow/stopped = potentially delayed
                delayed++;
            } else {
                onTime++;
            }
        }
        
        stats.put("onTime", onTime);
        stats.put("delayed", delayed);
        stats.put("early", early);
        
        return stats;
    }

    // Alias method for backward compatibility
    public List<ArrivalPrediction> getArrivalsForStop(String stopId) {
        return getPredictionsForStop(stopId);
    }
}