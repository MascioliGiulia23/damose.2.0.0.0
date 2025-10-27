package com.rometransit.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.rometransit.model.entity.Vehicle;
import com.rometransit.model.entity.Route;
import com.rometransit.model.enums.VehicleStatus;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Vehicle position DTO with real-time location and status information
 * Ignores unknown JSON fields for backward compatibility with cached data
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VehiclePosition {
    private String vehicleId;
    private String routeId;
    private String routeShortName;
    private String tripId;
    private String headsign;
    private double latitude;
    private double longitude;
    private double bearing;
    private double speed;
    private VehicleStatus status;
    private LocalDateTime lastUpdate;
    private int occupancyLevel;
    private int capacity;
    private boolean isTracked;
    private String currentStopId;
    private String nextStopId;
    private int delaySeconds;
    private long timestamp;
    private int directionId;
    private boolean isSimulated; // Indica se questo veicolo è simulato (non real-time)

    public VehiclePosition() {}

    public VehiclePosition(Vehicle vehicle, Route route) {
        this.vehicleId = vehicle.getVehicleId();
        this.routeId = vehicle.getRouteId();
        this.routeShortName = route != null ? route.getRouteShortName() : "";
        this.tripId = vehicle.getTripId();
        this.latitude = vehicle.getLatitude();
        this.longitude = vehicle.getLongitude();
        this.bearing = vehicle.getBearing();
        this.speed = vehicle.getSpeed();
        this.status = vehicle.getCurrentStatus();
        this.lastUpdate = vehicle.getTimestamp();
        this.occupancyLevel = vehicle.getOccupancyStatus();
        this.capacity = vehicle.getCapacity();
        this.currentStopId = vehicle.getStopId();
        this.isTracked = true;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getRouteShortName() {
        return routeShortName;
    }

    public void setRouteShortName(String routeShortName) {
        this.routeShortName = routeShortName;
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

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getBearing() {
        return bearing;
    }

    public void setBearing(double bearing) {
        this.bearing = bearing;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getOccupancyLevel() {
        return occupancyLevel;
    }

    public void setOccupancyLevel(int occupancyLevel) {
        this.occupancyLevel = occupancyLevel;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isTracked() {
        return isTracked;
    }

    public void setTracked(boolean tracked) {
        isTracked = tracked;
    }

    public String getCurrentStopId() {
        return currentStopId;
    }

    public void setCurrentStopId(String currentStopId) {
        this.currentStopId = currentStopId;
    }

    public String getNextStopId() {
        return nextStopId;
    }

    public void setNextStopId(String nextStopId) {
        this.nextStopId = nextStopId;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(int delaySeconds) {
        this.delaySeconds = delaySeconds;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void setTimestamp(LocalDateTime dateTime) {
        this.lastUpdate = dateTime;
        this.timestamp = dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    public void setLastUpdated(LocalDateTime lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public int getDirectionId() {
        return directionId;
    }

    public void setDirectionId(int directionId) {
        this.directionId = directionId;
    }

    public void setStopId(String stopId) {
        this.currentStopId = stopId;
    }

    public void setStatus(String statusName) {
        try {
            this.status = VehicleStatus.valueOf(statusName);
        } catch (IllegalArgumentException e) {
            this.status = VehicleStatus.IN_TRANSIT_TO;
        }
    }

    public boolean isSimulated() {
        return isSimulated;
    }

    public void setSimulated(boolean simulated) {
        isSimulated = simulated;
    }

    public boolean isStale() {
        return lastUpdate != null && 
               java.time.Duration.between(lastUpdate, LocalDateTime.now()).toMinutes() > 5;
    }

    public String getOccupancyDescription() {
        switch (occupancyLevel) {
            case 0: return "Empty";
            case 1: return "Many seats available";
            case 2: return "Few seats available";
            case 3: return "Standing room only";
            case 4: return "Crushed standing room only";
            case 5: return "Full";
            default: return "Unknown";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VehiclePosition that = (VehiclePosition) o;
        return Objects.equals(vehicleId, that.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vehicleId);
    }

    @Override
    public String toString() {
        return "VehiclePosition{" +
                "vehicleId='" + vehicleId + '\'' +
                ", routeShortName='" + routeShortName + '\'' +
                ", status=" + status +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", lastUpdate=" + lastUpdate +
                '}';
    }
}