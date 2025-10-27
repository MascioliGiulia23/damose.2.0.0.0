package com.rometransit.service.transit;

import com.rometransit.data.repository.StopRepository;
import com.rometransit.data.repository.RouteRepository;
import com.rometransit.data.repository.TripRepository;
import com.rometransit.data.repository.VehicleRepository;
import com.rometransit.model.dto.SearchResult;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Trip;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.enums.TransportType;
import com.rometransit.util.math.GeoUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class TransitService {
    private static TransitService instance;
    
    private final StopRepository stopRepository;
    private final RouteRepository routeRepository;
    private final TripRepository tripRepository;
    private final VehicleRepository vehicleRepository;

    private TransitService() {
        this.stopRepository = new StopRepository();
        this.routeRepository = new RouteRepository();
        this.tripRepository = new TripRepository();
        this.vehicleRepository = new VehicleRepository();
    }

    public static synchronized TransitService getInstance() {
        if (instance == null) {
            instance = new TransitService();
        }
        return instance;
    }

    public List<SearchResult> search(String query, double userLat, double userLon) {
        List<SearchResult> results = new ArrayList<>();
        
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String searchTerm = query.toLowerCase().trim();
        
        // Search stops
        List<Stop> matchingStops = stopRepository.search(searchTerm);
        for (Stop stop : matchingStops) {
            double distance = GeoUtils.isValidCoordinate(userLat, userLon) ?
                GeoUtils.calculateDistance(userLat, userLon, stop.getStopLat(), stop.getStopLon()) * 1000 : 0;
            
            double relevance = calculateStopRelevance(stop, searchTerm);
            results.add(new SearchResult(stop, distance, relevance, searchTerm));
        }

        // Search routes
        List<Route> matchingRoutes = routeRepository.search(searchTerm);
        for (Route route : matchingRoutes) {
            double relevance = calculateRouteRelevance(route, searchTerm);
            results.add(new SearchResult(route, relevance, searchTerm));
        }

        // Sort by combined score (relevance + distance)
        return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getCombinedScore).reversed())
                .collect(Collectors.toList());
    }

    public List<Stop> getStopsNearby(double latitude, double longitude, double radiusKm) {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            return new ArrayList<>();
        }
        
        return stopRepository.findNearby(latitude, longitude, radiusKm)
                .stream()
                .sorted((s1, s2) -> {
                    double dist1 = GeoUtils.calculateDistance(latitude, longitude, s1.getStopLat(), s1.getStopLon());
                    double dist2 = GeoUtils.calculateDistance(latitude, longitude, s2.getStopLat(), s2.getStopLon());
                    return Double.compare(dist1, dist2);
                })
                .collect(Collectors.toList());
    }

    public List<Route> getRoutesForStop(String stopId) {
        List<Trip> trips = tripRepository.findAll();
        return trips.stream()
                .filter(trip -> hasStopInTrip(trip.getTripId(), stopId))
                .map(trip -> routeRepository.findById(trip.getRouteId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<Stop> getStopsForRoute(String routeId) {
        List<Trip> trips = tripRepository.findByRouteId(routeId);
        List<Stop> stops = new ArrayList<>();
        
        // This is a simplified version - in a real implementation,
        // we would use stop_times.txt to get the actual stops for each trip
        for (Trip trip : trips) {
            // Add stops based on trip data (simplified)
            List<Stop> tripStops = getStopsForTrip(trip.getTripId());
            for (Stop stop : tripStops) {
                if (!stops.contains(stop)) {
                    stops.add(stop);
                }
            }
        }
        
        return stops;
    }

    public List<Trip> getTripsForRoute(String routeId) {
        return tripRepository.findByRouteId(routeId);
    }

    public List<Vehicle> getActiveVehiclesForRoute(String routeId) {
        return vehicleRepository.findActiveVehiclesForRoute(routeId);
    }

    public List<Vehicle> getVehiclesNearby(double latitude, double longitude, double radiusKm) {
        if (!GeoUtils.isValidCoordinate(latitude, longitude)) {
            return new ArrayList<>();
        }
        
        return vehicleRepository.findNearby(latitude, longitude, radiusKm);
    }

    public Optional<Stop> getStopById(String stopId) {
        return stopRepository.findById(stopId);
    }

    public Optional<Stop> getStopByCode(String stopCode) {
        return stopRepository.findByStopCode(stopCode);
    }

    public Optional<Route> getRouteById(String routeId) {
        return routeRepository.findById(routeId);
    }

    public Optional<Route> getRouteByShortName(String shortName) {
        return routeRepository.findByShortName(shortName);
    }

    public List<Route> getRoutesByType(TransportType type) {
        return routeRepository.findByType(type);
    }

    public List<TransportType> getAvailableTransportTypes() {
        return routeRepository.getAvailableTypes();
    }

    private double calculateStopRelevance(Stop stop, String searchTerm) {
        double relevance = 0.0;
        
        if (stop.getStopName() != null) {
            String name = stop.getStopName().toLowerCase();
            if (name.equals(searchTerm)) {
                relevance += 1.0;
            } else if (name.startsWith(searchTerm)) {
                relevance += 0.8;
            } else if (name.contains(searchTerm)) {
                relevance += 0.6;
            }
        }
        
        if (stop.getStopCode() != null) {
            String code = stop.getStopCode().toLowerCase();
            if (code.equals(searchTerm)) {
                relevance += 0.9;
            } else if (code.startsWith(searchTerm)) {
                relevance += 0.7;
            }
        }
        
        return Math.min(relevance, 1.0);
    }

    private double calculateRouteRelevance(Route route, String searchTerm) {
        double relevance = 0.0;
        
        if (route.getRouteShortName() != null) {
            String shortName = route.getRouteShortName().toLowerCase();
            if (shortName.equals(searchTerm)) {
                relevance += 1.0;
            } else if (shortName.startsWith(searchTerm)) {
                relevance += 0.9;
            } else if (shortName.contains(searchTerm)) {
                relevance += 0.7;
            }
        }
        
        if (route.getRouteLongName() != null) {
            String longName = route.getRouteLongName().toLowerCase();
            if (longName.contains(searchTerm)) {
                relevance += 0.5;
            }
        }
        
        return Math.min(relevance, 1.0);
    }

    private boolean hasStopInTrip(String tripId, String stopId) {
        // This would normally check stop_times.txt
        // For now, return a simplified check
        return true;
    }

    private List<Stop> getStopsForTrip(String tripId) {
        // This would normally use stop_times.txt to get stops for a specific trip
        // For now, return empty list - this would be implemented with stop_times data
        return new ArrayList<>();
    }

    public long getStopCount() {
        return stopRepository.count();
    }

    public long getRouteCount() {
        return routeRepository.count();
    }

    public long getTripCount() {
        return tripRepository.count();
    }

    public long getActiveVehicleCount() {
        return vehicleRepository.countActiveVehicles();
    }

    // Additional required methods
    public List<Stop> getAllStops() {
        return stopRepository.findAll();
    }

    public List<Route> getAllRoutes() {
        return routeRepository.findAll();
    }

    public List<SearchResult> search(String query) {
        // Search with default user location (Rome center)
        return search(query, 41.9028, 12.4964);
    }
}