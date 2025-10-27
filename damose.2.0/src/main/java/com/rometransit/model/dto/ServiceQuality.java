package com.rometransit.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public class ServiceQuality {
    private String routeId;
    private String routeShortName;
    private LocalDate date;
    private int totalScheduledTrips;
    private int completedTrips;
    private int cancelledTrips;
    private int delayedTrips;
    private int onTimeTrips;
    private double averageDelayMinutes;
    private double punctualityPercentage;
    private double reliabilityScore;
    private Map<String, Integer> delayDistribution;
    private LocalDateTime lastUpdated;

    public ServiceQuality() {
        this.delayDistribution = new HashMap<>();
        this.lastUpdated = LocalDateTime.now();
    }

    public ServiceQuality(String routeId, String routeShortName, LocalDate date) {
        this();
        this.routeId = routeId;
        this.routeShortName = routeShortName;
        this.date = date;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getTotalScheduledTrips() {
        return totalScheduledTrips;
    }

    public void setTotalScheduledTrips(int totalScheduledTrips) {
        this.totalScheduledTrips = totalScheduledTrips;
    }

    public int getCompletedTrips() {
        return completedTrips;
    }

    public void setCompletedTrips(int completedTrips) {
        this.completedTrips = completedTrips;
    }

    public int getCancelledTrips() {
        return cancelledTrips;
    }

    public void setCancelledTrips(int cancelledTrips) {
        this.cancelledTrips = cancelledTrips;
    }

    public int getDelayedTrips() {
        return delayedTrips;
    }

    public void setDelayedTrips(int delayedTrips) {
        this.delayedTrips = delayedTrips;
    }

    public int getOnTimeTrips() {
        return onTimeTrips;
    }

    public void setOnTimeTrips(int onTimeTrips) {
        this.onTimeTrips = onTimeTrips;
    }

    public double getAverageDelayMinutes() {
        return averageDelayMinutes;
    }

    public void setAverageDelayMinutes(double averageDelayMinutes) {
        this.averageDelayMinutes = averageDelayMinutes;
    }

    public double getPunctualityPercentage() {
        return punctualityPercentage;
    }

    public void setPunctualityPercentage(double punctualityPercentage) {
        this.punctualityPercentage = punctualityPercentage;
    }

    public double getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(double reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public Map<String, Integer> getDelayDistribution() {
        return delayDistribution;
    }

    public void setDelayDistribution(Map<String, Integer> delayDistribution) {
        this.delayDistribution = delayDistribution;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void calculateMetrics() {
        if (totalScheduledTrips > 0) {
            this.punctualityPercentage = (double) onTimeTrips / totalScheduledTrips * 100;
            this.reliabilityScore = (double) completedTrips / totalScheduledTrips * 100;
        }
        this.lastUpdated = LocalDateTime.now();
    }

    public double getCancellationRate() {
        return totalScheduledTrips > 0 ? (double) cancelledTrips / totalScheduledTrips * 100 : 0;
    }

    public double getDelayRate() {
        return totalScheduledTrips > 0 ? (double) delayedTrips / totalScheduledTrips * 100 : 0;
    }

    public String getQualityGrade() {
        if (reliabilityScore >= 95 && punctualityPercentage >= 90) return "A";
        if (reliabilityScore >= 90 && punctualityPercentage >= 80) return "B";
        if (reliabilityScore >= 80 && punctualityPercentage >= 70) return "C";
        if (reliabilityScore >= 70 && punctualityPercentage >= 60) return "D";
        return "F";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceQuality that = (ServiceQuality) o;
        return Objects.equals(routeId, that.routeId) && Objects.equals(date, that.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, date);
    }

    @Override
    public String toString() {
        return "ServiceQuality{" +
                "routeShortName='" + routeShortName + '\'' +
                ", date=" + date +
                ", punctualityPercentage=" + String.format("%.1f", punctualityPercentage) + "%" +
                ", reliabilityScore=" + String.format("%.1f", reliabilityScore) + "%" +
                ", grade=" + getQualityGrade() +
                '}';
    }
}