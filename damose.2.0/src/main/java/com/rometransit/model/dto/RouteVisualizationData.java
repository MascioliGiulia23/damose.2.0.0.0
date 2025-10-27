package com.rometransit.model.dto;

import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Shape;
import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Trip;

import java.util.List;

/**
 * Contains all data needed to visualize a route on the map
 * - Route path (shape points)
 * - Stops along the route
 * - Route metadata (color, type, name)
 */
public class RouteVisualizationData {
    private final Route route;
    private final Trip trip;
    private final List<Shape> shapePoints;
    private final List<Stop> stopsOnRoute;
    private final String tripHeadsign;
    private final int directionId;

    public RouteVisualizationData(Route route, Trip trip, List<Shape> shapePoints, List<Stop> stopsOnRoute) {
        this.route = route;
        this.trip = trip;
        this.shapePoints = shapePoints;
        this.stopsOnRoute = stopsOnRoute;
        this.tripHeadsign = trip != null ? trip.getTripHeadsign() : null;
        this.directionId = trip != null ? trip.getDirectionId() : 0;
    }

    public Route getRoute() {
        return route;
    }

    public Trip getTrip() {
        return trip;
    }

    public List<Shape> getShapePoints() {
        return shapePoints;
    }

    public List<Stop> getStopsOnRoute() {
        return stopsOnRoute;
    }

    public String getTripHeadsign() {
        return tripHeadsign;
    }

    public int getDirectionId() {
        return directionId;
    }

    public String getRouteDisplayName() {
        String shortName = route.getRouteShortName() != null ? route.getRouteShortName() : "";
        String longName = route.getRouteLongName() != null ? route.getRouteLongName() : "";
        return shortName + (shortName.isEmpty() || longName.isEmpty() ? "" : " - ") + longName;
    }

    public String getDirectionDisplayName() {
        if (tripHeadsign != null && !tripHeadsign.isEmpty()) {
            return "Direzione: " + tripHeadsign;
        }
        return "Direzione " + (directionId == 0 ? "Andata" : "Ritorno");
    }

    @Override
    public String toString() {
        return "RouteVisualizationData{" +
                "route=" + getRouteDisplayName() +
                ", direction=" + getDirectionDisplayName() +
                ", shapePoints=" + (shapePoints != null ? shapePoints.size() : 0) +
                ", stops=" + (stopsOnRoute != null ? stopsOnRoute.size() : 0) +
                '}';
    }
}
