package com.rometransit.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Favorite {
    private String favoriteId;
    private String userId;
    private String stopId;
    private String routeId;
    private FavoriteType type;
    private String customName;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private int usageCount;
    private boolean isActive;

    public Favorite() {
        this.createdAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
        this.usageCount = 0;
        this.isActive = true;
    }

    public Favorite(String userId, String stopId, String routeId, FavoriteType type) {
        this();
        this.favoriteId = generateFavoriteId();
        this.userId = userId;
        this.stopId = stopId;
        this.routeId = routeId;
        this.type = type;
    }

    private String generateFavoriteId() {
        return "fav_" + System.currentTimeMillis() + "_" + userId;
    }

    public void markAsUsed() {
        this.lastUsed = LocalDateTime.now();
        this.usageCount++;
    }

    // Getters and Setters
    public String getFavoriteId() {
        return favoriteId;
    }

    public void setFavoriteId(String favoriteId) {
        this.favoriteId = favoriteId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStopId() {
        return stopId;
    }

    public void setStopId(String stopId) {
        this.stopId = stopId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public FavoriteType getType() {
        return type;
    }

    public void setType(FavoriteType type) {
        this.type = type;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(LocalDateTime lastUsed) {
        this.lastUsed = lastUsed;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(int usageCount) {
        this.usageCount = usageCount;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getDisplayName() {
        if (customName != null && !customName.trim().isEmpty()) {
            return customName;
        }
        
        switch (type) {
            case STOP:
                return "Stop " + stopId;
            case ROUTE:
                return "Route " + routeId;
            case STOP_ROUTE:
                return "Route " + routeId + " at " + stopId;
            default:
                return "Favorite";
        }
    }

    @Override
    public String toString() {
        return "Favorite{" +
                "favoriteId='" + favoriteId + '\'' +
                ", userId='" + userId + '\'' +
                ", stopId='" + stopId + '\'' +
                ", routeId='" + routeId + '\'' +
                ", type=" + type +
                ", customName='" + customName + '\'' +
                ", usageCount=" + usageCount +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Favorite favorite = (Favorite) o;

        return favoriteId != null ? favoriteId.equals(favorite.favoriteId) : favorite.favoriteId == null;
    }

    @Override
    public int hashCode() {
        return favoriteId != null ? favoriteId.hashCode() : 0;
    }

    public enum FavoriteType {
        STOP,
        ROUTE,
        STOP_ROUTE
    }
}