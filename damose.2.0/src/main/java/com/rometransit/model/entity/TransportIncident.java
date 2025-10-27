package com.rometransit.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entity class representing a transport incident or disruption
 */
public class TransportIncident {

    public enum Severity { LOW, MEDIUM, HIGH }

    private String id;
    private String type;
    private String location;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Severity severity;
    private List<String> affectedRoutes;
    private boolean active;

    public TransportIncident() {
        this.affectedRoutes = new ArrayList<>();
        this.active = true;
    }

    public TransportIncident(String type, String location, Severity severity) {
        this();
        this.type = type;
        this.location = location;
        this.severity = severity;
        this.startTime = LocalDateTime.now();
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public List<String> getAffectedRoutes() {
        return affectedRoutes;
    }

    public void setAffectedRoutes(List<String> affectedRoutes) {
        this.affectedRoutes = affectedRoutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransportIncident that = (TransportIncident) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TransportIncident{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", location='" + location + '\'' +
                ", severity=" + severity +
                ", active=" + active +
                '}';
    }
}
