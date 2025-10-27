package com.rometransit.model.dto;

import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Stop;
import java.time.LocalDateTime;
import java.util.Objects;

public class ArrivalPrediction {
    private Stop stop;
    private Route route;
    private String tripId;
    private String headsign;
    private LocalDateTime scheduledArrival;
    private LocalDateTime predictedArrival;
    private int delayMinutes;
    private boolean isRealtime;
    private double confidence;
    private String vehicleId;
    private int stopSequence;
    private String stopId;
    private String routeId;
    private long arrivalTime;
    private long departureTime;
    private LocalDateTime predictionTime;
    private int delay;
    private LocalDateTime expectedArrivalTime;
    private LocalDateTime expectedDepartureTime;
    private String scheduleRelationship;

    public ArrivalPrediction() {}

    public ArrivalPrediction(Stop stop, Route route, String tripId, LocalDateTime scheduledArrival) {
        this.stop = stop;
        this.route = route;
        this.tripId = tripId;
        this.scheduledArrival = scheduledArrival;
        this.predictedArrival = scheduledArrival;
        this.delayMinutes = 0;
        this.isRealtime = false;
        this.confidence = 0.5;
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

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getHeadsign() {
        return headsign;
    }

    public void setHeadsign(String headsign) {
        this.headsign = headsign;
    }

    public LocalDateTime getScheduledArrival() {
        return scheduledArrival;
    }

    public void setScheduledArrival(LocalDateTime scheduledArrival) {
        this.scheduledArrival = scheduledArrival;
    }

    public LocalDateTime getPredictedArrival() {
        return predictedArrival;
    }

    public void setPredictedArrival(LocalDateTime predictedArrival) {
        this.predictedArrival = predictedArrival;
        if (scheduledArrival != null) {
            this.delayMinutes = (int) java.time.Duration.between(scheduledArrival, predictedArrival).toMinutes();
        }
    }

    public int getDelayMinutes() {
        return delayMinutes;
    }

    public void setDelayMinutes(int delayMinutes) {
        this.delayMinutes = delayMinutes;
    }

    public boolean isRealtime() {
        return isRealtime;
    }

    public void setRealtime(boolean realtime) {
        isRealtime = realtime;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public int getStopSequence() {
        return stopSequence;
    }

    public void setStopSequence(int stopSequence) {
        this.stopSequence = stopSequence;
    }

    public String getRouteId() {
        return routeId != null ? routeId : (route != null ? route.getRouteId() : null);
    }
    
    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }
    
    public String getStopId() {
        return stopId != null ? stopId : (stop != null ? stop.getStopId() : null);
    }
    
    public void setStopId(String stopId) {
        this.stopId = stopId;
    }
    
    public long getArrivalTime() {
        return arrivalTime;
    }
    
    public void setArrivalTime(long arrivalTime) {
        this.arrivalTime = arrivalTime;
    }
    
    public long getDepartureTime() {
        return departureTime;
    }
    
    public void setDepartureTime(long departureTime) {
        this.departureTime = departureTime;
    }
    
    public LocalDateTime getPredictionTime() {
        return predictionTime;
    }
    
    public void setPredictionTime(LocalDateTime predictionTime) {
        this.predictionTime = predictionTime;
    }
    
    public int getDelay() {
        return delay;
    }
    
    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getTripHeadsign() {
        return headsign;
    }

    public LocalDateTime getPredictedArrivalTime() {
        return predictedArrival;
    }

    public int getMinutesUntilArrival() {
        return (int) java.time.Duration.between(LocalDateTime.now(), predictedArrival).toMinutes();
    }

    public String getDelayStatus() {
        if (delayMinutes > 5) return "Delayed";
        if (delayMinutes < -2) return "Early";
        return "On Time";
    }

    // Additional required methods
    public int getMinutesToArrival() {
        return getMinutesUntilArrival();
    }

    public int getDelaySeconds() {
        return delayMinutes * 60;
    }

    public LocalDateTime getExpectedArrivalTime() {
        return expectedArrivalTime != null ? expectedArrivalTime : predictedArrival;
    }

    public void setExpectedArrivalTime(LocalDateTime expectedArrivalTime) {
        this.expectedArrivalTime = expectedArrivalTime;
        this.predictedArrival = expectedArrivalTime;
    }

    public LocalDateTime getExpectedDepartureTime() {
        return expectedDepartureTime;
    }

    public void setExpectedDepartureTime(LocalDateTime expectedDepartureTime) {
        this.expectedDepartureTime = expectedDepartureTime;
    }

    public String getScheduleRelationship() {
        return scheduleRelationship;
    }

    public void setScheduleRelationship(String scheduleRelationship) {
        this.scheduleRelationship = scheduleRelationship;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delay = delaySeconds;
        this.delayMinutes = delaySeconds / 60;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrivalPrediction that = (ArrivalPrediction) o;
        return Objects.equals(tripId, that.tripId) &&
                Objects.equals(stop, that.stop) &&
                Objects.equals(scheduledArrival, that.scheduledArrival);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tripId, stop, scheduledArrival);
    }

    @Override
    public String toString() {
        return "ArrivalPrediction{" +
                "route=" + (route != null ? route.getRouteShortName() : "null") +
                ", stop=" + (stop != null ? stop.getStopName() : "null") +
                ", predictedArrival=" + predictedArrival +
                ", delayMinutes=" + delayMinutes +
                ", isRealtime=" + isRealtime +
                '}';
    }
}