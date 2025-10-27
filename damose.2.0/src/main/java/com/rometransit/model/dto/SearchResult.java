package com.rometransit.model.dto;

import com.rometransit.model.entity.Stop;
import com.rometransit.model.entity.Route;
import java.util.Objects;

public class SearchResult {
    public enum ResultType {
        STOP("Stop", "🚏"),
        ROUTE("Route", "🚌"),
        ADDRESS("Address", "📍"),
        STOP_AND_ROUTE("Stop & Route", "🚏");

        private final String displayName;
        private final String icon;

        ResultType(String displayName, String icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIcon() {
            return icon;
        }
    }

    private ResultType type;
    private Stop stop;
    private Route route;
    private double distance;
    private double relevanceScore;
    private String matchedText;
    private String highlightedText;

    public SearchResult() {}

    public SearchResult(Stop stop, double distance, double relevanceScore, String matchedText) {
        this.type = ResultType.STOP;
        this.stop = stop;
        this.distance = distance;
        this.relevanceScore = relevanceScore;
        this.matchedText = matchedText;
    }

    public SearchResult(Route route, double relevanceScore, String matchedText) {
        this.type = ResultType.ROUTE;
        this.route = route;
        this.relevanceScore = relevanceScore;
        this.matchedText = matchedText;
        this.distance = 0;
    }

    public SearchResult(Stop stop, Route route, double distance, double relevanceScore, String matchedText) {
        this.type = ResultType.STOP_AND_ROUTE;
        this.stop = stop;
        this.route = route;
        this.distance = distance;
        this.relevanceScore = relevanceScore;
        this.matchedText = matchedText;
    }

    public ResultType getType() {
        return type;
    }

    public void setType(ResultType type) {
        this.type = type;
    }

    public Stop getStop() {
        return stop;
    }

    public void setStop(Stop stop) {
        this.stop = stop;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }

    public String getMatchedText() {
        return matchedText;
    }

    public void setMatchedText(String matchedText) {
        this.matchedText = matchedText;
    }

    public String getHighlightedText() {
        return highlightedText;
    }

    public void setHighlightedText(String highlightedText) {
        this.highlightedText = highlightedText;
    }

    public String getName() {
        switch (type) {
            case STOP:
                return stop != null ? stop.getStopName() : "";
            case ROUTE:
                return route != null ? route.getRouteShortName() + " - " + route.getRouteLongName() : "";
            case STOP_AND_ROUTE:
                return (stop != null ? stop.getStopName() : "") + 
                       (route != null ? " - " + route.getRouteShortName() : "");
            default:
                return "";
        }
    }

    public String getDescription() {
        switch (type) {
            case STOP:
                return stop != null ? "Stop ID: " + stop.getStopId() : "";
            case ROUTE:
                return route != null ? route.getRouteType().getDisplayName() : "";
            case STOP_AND_ROUTE:
                return (stop != null ? "Stop ID: " + stop.getStopId() : "") +
                       (route != null ? " • " + route.getRouteType().getDisplayName() : "");
            default:
                return "";
        }
    }

    public String getDisplayTitle() {
        return getName();
    }

    public String getDisplaySubtitle() {
        switch (type) {
            case STOP:
                return distance > 0 ? String.format("%.0fm away", distance) : "";
            case ROUTE:
                return route.getRouteType().getDisplayName();
            case STOP_AND_ROUTE:
                String distanceText = distance > 0 ? String.format("%.0fm", distance) : "";
                String typeText = route.getRouteType().getDisplayName();
                return distanceText.isEmpty() ? typeText : distanceText + " • " + typeText;
            default:
                return "";
        }
    }

    public double getCombinedScore() {
        return relevanceScore * (distance > 0 ? Math.max(0.1, 1.0 / (1.0 + distance / 1000.0)) : 1.0);
    }

    // Method to get the primary item (Stop or Route)
    public Object getItem() {
        switch (type) {
            case STOP:
            case STOP_AND_ROUTE:
                return stop;
            case ROUTE:
                return route;
            default:
                return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchResult that = (SearchResult) o;
        return type == that.type &&
                Objects.equals(stop, that.stop) &&
                Objects.equals(route, that.route);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, stop, route);
    }

    @Override
    public String toString() {
        return "SearchResult{" +
                "type=" + type +
                ", title='" + getDisplayTitle() + '\'' +
                ", subtitle='" + getDisplaySubtitle() + '\'' +
                ", relevanceScore=" + String.format("%.2f", relevanceScore) +
                '}';
    }
}