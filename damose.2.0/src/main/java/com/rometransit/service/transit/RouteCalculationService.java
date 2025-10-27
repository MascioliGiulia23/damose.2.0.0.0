package com.rometransit.service.transit;

import com.rometransit.data.repository.StopRepository;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.data.repository.TripRepository;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Trip;
import com.rometransit.util.math.GeoUtils;

import java.util.*;
import java.util.stream.Collectors;

public class RouteCalculationService {
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;

    public RouteCalculationService() {
        this.stopRepository = new StopRepository();
        this.routeRepository = new RouteRepository();
        this.tripRepository = new TripRepository();
    }

    public List<Stop> calculateRouteStops(String routeId, int directionId) {
        List<Trip> trips = tripRepository.findByDirection(routeId, directionId);
        if (trips.isEmpty()) {
            return new ArrayList<>();
        }

        // Use the first trip as reference for stop sequence
        Trip referenceTrip = trips.get(0);
        return getStopsForTrip(referenceTrip.getTripId());
    }

    public double calculateRouteDistance(String routeId, int directionId) {
        List<Stop> stops = calculateRouteStops(routeId, directionId);
        if (stops.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;
        for (int i = 1; i < stops.size(); i++) {
            Stop prevStop = stops.get(i - 1);
            Stop currentStop = stops.get(i);
            
            totalDistance += GeoUtils.calculateDistance(
                    prevStop.getStopLat(), prevStop.getStopLon(),
                    currentStop.getStopLat(), currentStop.getStopLon()
            );
        }

        return totalDistance;
    }

    public int calculateEstimatedTravelTime(String routeId, int directionId) {
        double distanceKm = calculateRouteDistance(routeId, directionId);
        double averageSpeedKmh = 15.0; // Average speed for urban transit
        
        return (int) Math.ceil((distanceKm / averageSpeedKmh) * 60); // Convert to minutes
    }

    public List<Stop> findStopsAlongRoute(String routeId, String startStopId, String endStopId) {
        List<Stop> allStops = calculateRouteStops(routeId, 0); // Default direction
        
        int startIndex = -1, endIndex = -1;
        
        for (int i = 0; i < allStops.size(); i++) {
            Stop stop = allStops.get(i);
            if (stop.getStopId().equals(startStopId)) {
                startIndex = i;
            }
            if (stop.getStopId().equals(endStopId)) {
                endIndex = i;
            }
        }

        if (startIndex == -1 || endIndex == -1 || startIndex >= endIndex) {
            return new ArrayList<>();
        }

        return allStops.subList(startIndex, endIndex + 1);
    }

    public double calculateDistanceBetweenStops(String startStopId, String endStopId) {
        Optional<Stop> startStop = stopRepository.findById(startStopId);
        Optional<Stop> endStop = stopRepository.findById(endStopId);

        if (!startStop.isPresent() || !endStop.isPresent()) {
            return 0.0;
        }

        return GeoUtils.calculateDistance(
                startStop.get().getStopLat(), startStop.get().getStopLon(),
                endStop.get().getStopLat(), endStop.get().getStopLon()
        );
    }

    public List<Route> findRoutesBetweenStops(String startStopId, String endStopId) {
        List<Route> allRoutes = routeRepository.findAll();
        List<Route> connectingRoutes = new ArrayList<>();

        for (Route route : allRoutes) {
            if (routeConnectsStops(route.getRouteId(), startStopId, endStopId)) {
                connectingRoutes.add(route);
            }
        }

        return connectingRoutes;
    }

    public List<Route> findAlternativeRoutes(String routeId, String stopId) {
        Optional<Stop> stop = stopRepository.findById(stopId);
        if (!stop.isPresent()) {
            return new ArrayList<>();
        }

        Stop targetStop = stop.get();
        List<Route> allRoutes = routeRepository.findAll();
        List<Route> alternatives = new ArrayList<>();

        for (Route route : allRoutes) {
            if (!route.getRouteId().equals(routeId) && routeServesStop(route.getRouteId(), stopId)) {
                alternatives.add(route);
            }
        }

        return alternatives;
    }

    public List<Stop> findNearbyStopsOnRoute(String routeId, double latitude, double longitude, double radiusKm) {
        List<Stop> routeStops = calculateRouteStops(routeId, 0);
        
        return routeStops.stream()
                .filter(stop -> GeoUtils.calculateDistance(
                        latitude, longitude, stop.getStopLat(), stop.getStopLon()) <= radiusKm)
                .sorted((s1, s2) -> {
                    double dist1 = GeoUtils.calculateDistance(latitude, longitude, s1.getStopLat(), s1.getStopLon());
                    double dist2 = GeoUtils.calculateDistance(latitude, longitude, s2.getStopLat(), s2.getStopLon());
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
    }

    public Map<Integer, List<Stop>> getRouteStopsByDirection(String routeId) {
        Map<Integer, List<Stop>> directionStops = new HashMap<>();
        
        List<Trip> trips = tripRepository.findByRouteId(routeId);
        Set<Integer> directions = trips.stream()
                .map(Trip::getDirectionId)
                .collect(Collectors.toSet());

        for (Integer direction : directions) {
            directionStops.put(direction, calculateRouteStops(routeId, direction));
        }

        return directionStops;
    }

    public String[] getRouteDirectionNames(String routeId) {
        List<Trip> trips = tripRepository.findByRouteId(routeId);

        // Get all headsigns for each direction
        Map<Integer, List<String>> headsignsByDirection = new HashMap<>();
        for (Trip trip : trips) {
            if (trip.getTripHeadsign() != null && !trip.getTripHeadsign().trim().isEmpty()) {
                headsignsByDirection
                    .computeIfAbsent(trip.getDirectionId(), k -> new ArrayList<>())
                    .add(trip.getTripHeadsign());
            }
        }

        String[] names = new String[2];

        // For direction 0: get the most common headsign
        if (headsignsByDirection.containsKey(0)) {
            names[0] = getMostCommonHeadsign(headsignsByDirection.get(0));
        } else {
            names[0] = null;
        }

        // For direction 1: get the most common headsign
        if (headsignsByDirection.containsKey(1)) {
            names[1] = getMostCommonHeadsign(headsignsByDirection.get(1));
        } else {
            names[1] = null;
        }

        return names;
    }

    /**
     * Get the most common headsign from a list (to handle variations)
     */
    private String getMostCommonHeadsign(List<String> headsigns) {
        if (headsigns.isEmpty()) return null;

        // Count occurrences of each headsign
        Map<String, Long> frequencyMap = headsigns.stream()
            .collect(Collectors.groupingBy(h -> h, Collectors.counting()));

        // Return the most common one
        return frequencyMap.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(headsigns.get(0));
    }

    public boolean routeHasTwoDirections(String routeId) {
        List<Trip> trips = tripRepository.findByRouteId(routeId);
        Set<Integer> directions = trips.stream()
                .map(Trip::getDirectionId)
                .collect(Collectors.toSet());
        
        return directions.size() >= 2;
    }

    public List<Stop> getTerminalStops(String routeId) {
        Map<Integer, List<Stop>> directionStops = getRouteStopsByDirection(routeId);
        List<Stop> terminals = new ArrayList<>();

        for (List<Stop> stops : directionStops.values()) {
            if (!stops.isEmpty()) {
                terminals.add(stops.get(0)); // First stop
                if (stops.size() > 1) {
                    terminals.add(stops.get(stops.size() - 1)); // Last stop
                }
            }
        }

        // Remove duplicates
        return terminals.stream().distinct().collect(Collectors.toList());
    }

    public int getStopPosition(String routeId, String stopId, int directionId) {
        List<Stop> stops = calculateRouteStops(routeId, directionId);
        
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getStopId().equals(stopId)) {
                return i + 1; // 1-based position
            }
        }
        
        return -1; // Stop not found on route
    }

    private List<Stop> getStopsForTrip(String tripId) {
        // This would normally use stop_times.txt to get the actual stops for a trip
        // For now, return a simplified implementation
        List<Stop> allStops = stopRepository.findAll();
        
        // Return a subset of stops as a placeholder
        return allStops.stream()
                .limit(10)
                .collect(Collectors.toList());
    }

    private boolean routeConnectsStops(String routeId, String startStopId, String endStopId) {
        List<Stop> routeStops = calculateRouteStops(routeId, 0);
        
        boolean hasStart = routeStops.stream().anyMatch(s -> s.getStopId().equals(startStopId));
        boolean hasEnd = routeStops.stream().anyMatch(s -> s.getStopId().equals(endStopId));
        
        return hasStart && hasEnd;
    }

    private boolean routeServesStop(String routeId, String stopId) {
        List<Stop> routeStops = calculateRouteStops(routeId, 0);
        return routeStops.stream().anyMatch(s -> s.getStopId().equals(stopId));
    }
}