package com.rometransit.model.entity;

import com.rometransit.model.enums.VehicleStatus;
import java.time.LocalDateTime;
import java.util.Objects;

public class Vehicle {
    private String vehicleId;
    private String tripId;
    private String routeId;
    private String startTime;
    private String startDate;
    private int scheduleRelationship;
    private double latitude;
    private double longitude;
    private float bearing;
    private double speed;
    private VehicleStatus currentStatus;
    private String currentStopSequence;
    private String stopId;
    private LocalDateTime timestamp;
    private int congestionLevel;
    private int occupancyStatus;
    private int capacity;

    public Vehicle() {}

    public Vehicle(String vehicleId, String tripId, String routeId) {
        this.vehicleId = vehicleId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.timestamp = LocalDateTime.now();
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getTripId() {
        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public int getScheduleRelationship() {
        return scheduleRelationship;
    }

    public void setScheduleRelationship(int scheduleRelationship) {
        this.scheduleRelationship = scheduleRelationship;
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

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public VehicleStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(VehicleStatus currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getCurrentStopSequence() {
        return currentStopSequence;
    }

    public void setCurrentStopSequence(String currentStopSequence) {
        this.currentStopSequence = currentStopSequence;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getCongestionLevel() {
        return congestionLevel;
    }

    public void setCongestionLevel(int congestionLevel) {
        this.congestionLevel = congestionLevel;
    }

    public int getOccupancyStatus() {
        return occupancyStatus;
    }

    public void setOccupancyStatus(int occupancyStatus) {
        this.occupancyStatus = occupancyStatus;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vehicle vehicle = (Vehicle) o;
        return Objects.equals(vehicleId, vehicle.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vehicleId);
    }

    @Override
    public String toString() {
        return "Vehicle{" +
                "vehicleId='" + vehicleId + '\'' +
                ", tripId='" + tripId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", currentStatus=" + currentStatus +
                '}';
    }
}