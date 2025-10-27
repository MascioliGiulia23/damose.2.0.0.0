package com.rometransit.data.repository;

import com.rometransit.model.entity.Route;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Legacy RouteRepository stub for backward compatibility
 *
 * @deprecated Use {@link GTFSRepository} instead for GTFS data access
 */
@Deprecated
public class RouteRepository {

    private final GTFSRepository gtfsRepository;

    public RouteRepository() {
        this.gtfsRepository = GTFSRepository.getInstance();
        System.out.println("⚠️  RouteRepository stub - use GTFSRepository instead");
    }

    public Optional<Route> findById(String routeId) {
        try {
            return gtfsRepository.getRoute(routeId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Route> findAll() {
        try {
            return gtfsRepository.loadRoutes();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Route> searchByName(String query) {
        try {
            return gtfsRepository.searchRoutes(query);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void save(Route route) {
        try {
            gtfsRepository.saveRoutes(List.of(route));
        } catch (Exception e) {
            System.err.println("Failed to save route: " + e.getMessage());
        }
    }

    public void delete(String routeId) {
        // No-op stub
    }

    public long count() {
        try {
            return gtfsRepository.loadRoutes().size();
        } catch (Exception e) {
            return 0;
        }
    }

    public Optional<Route> findByShortName(String shortName) {
        try {
            return gtfsRepository.loadRoutes().stream()
                .filter(r -> shortName.equals(r.getRouteShortName()))
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<Route> findByType(com.rometransit.model.enums.TransportType type) {
        try {
            return gtfsRepository.loadRoutes().stream()
                .filter(r -> type.equals(r.getRouteType()))
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<com.rometransit.model.enums.TransportType> getAvailableTypes() {
        try {
            return gtfsRepository.loadRoutes().stream()
                .map(Route::getRouteType)
                .distinct()
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<Route> routes) {
        try {
            gtfsRepository.saveRoutes(routes);
        } catch (Exception e) {
            System.err.println("Failed to save routes: " + e.getMessage());
        }
    }

    public void deleteAll() {
        // No-op stub
    }

    public List<Route> findByFavorites(List<String> routeIds) {
        try {
            return gtfsRepository.loadRoutes().stream()
                .filter(r -> routeIds.contains(r.getRouteId()))
                .toList();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<Route> search(String query) {
        return searchByName(query);
    }
}
