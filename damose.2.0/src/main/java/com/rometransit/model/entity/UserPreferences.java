package com.rometransit.model.entity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class UserPreferences {
    private String userId;
    private Set<String> favoriteStopIds;
    private Set<String> favoriteRouteIds;
    private boolean darkThemeEnabled;
    private boolean realtimeNotificationsEnabled;
    private boolean allowUsageStatistics;
    private int refreshIntervalSeconds;
    private double homeLatitude;
    private double homeLongitude;
    private String homeStopId;
    private boolean mapCenteredOnHome;
    private int maxWalkingDistance;
    private String preferredLanguage;

    public UserPreferences() {
        this.favoriteStopIds = new HashSet<>();
        this.favoriteRouteIds = new HashSet<>();
        this.darkThemeEnabled = false;
        this.realtimeNotificationsEnabled = true;
        this.allowUsageStatistics = false; // Default: disabled for privacy
        this.refreshIntervalSeconds = 30;
        this.maxWalkingDistance = 500;
        this.mapCenteredOnHome = true;
        this.preferredLanguage = "it"; // Default to Italian
    }

    public UserPreferences(String userId) {
        this();
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Set<String> getFavoriteStopIds() {
        return favoriteStopIds;
    }

    public void setFavoriteStopIds(Set<String> favoriteStopIds) {
        this.favoriteStopIds = favoriteStopIds;
    }

    public void addFavoriteStop(String stopId) {
        this.favoriteStopIds.add(stopId);
    }

    public void removeFavoriteStop(String stopId) {
        this.favoriteStopIds.remove(stopId);
    }

    public Set<String> getFavoriteRouteIds() {
        return favoriteRouteIds;
    }

    public void setFavoriteRouteIds(Set<String> favoriteRouteIds) {
        this.favoriteRouteIds = favoriteRouteIds;
    }

    public void addFavoriteRoute(String routeId) {
        this.favoriteRouteIds.add(routeId);
    }

    public void removeFavoriteRoute(String routeId) {
        this.favoriteRouteIds.remove(routeId);
    }

    public boolean isDarkThemeEnabled() {
        return darkThemeEnabled;
    }

    public void setDarkThemeEnabled(boolean darkThemeEnabled) {
        this.darkThemeEnabled = darkThemeEnabled;
    }

    public boolean isRealtimeNotificationsEnabled() {
        return realtimeNotificationsEnabled;
    }

    public void setRealtimeNotificationsEnabled(boolean realtimeNotificationsEnabled) {
        this.realtimeNotificationsEnabled = realtimeNotificationsEnabled;
    }

    public boolean isAllowUsageStatistics() {
        return allowUsageStatistics;
    }

    public void setAllowUsageStatistics(boolean allowUsageStatistics) {
        this.allowUsageStatistics = allowUsageStatistics;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }

    public double getHomeLatitude() {
        return homeLatitude;
    }

    public void setHomeLatitude(double homeLatitude) {
        this.homeLatitude = homeLatitude;
    }

    public double getHomeLongitude() {
        return homeLongitude;
    }

    public void setHomeLongitude(double homeLongitude) {
        this.homeLongitude = homeLongitude;
    }

    public String getHomeStopId() {
        return homeStopId;
    }

    public void setHomeStopId(String homeStopId) {
        this.homeStopId = homeStopId;
    }

    public boolean isMapCenteredOnHome() {
        return mapCenteredOnHome;
    }

    public void setMapCenteredOnHome(boolean mapCenteredOnHome) {
        this.mapCenteredOnHome = mapCenteredOnHome;
    }

    public int getMaxWalkingDistance() {
        return maxWalkingDistance;
    }

    public void setMaxWalkingDistance(int maxWalkingDistance) {
        this.maxWalkingDistance = maxWalkingDistance;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPreferences that = (UserPreferences) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "UserPreferences{" +
                "userId='" + userId + '\'' +
                ", favoriteStops=" + favoriteStopIds.size() +
                ", favoriteRoutes=" + favoriteRouteIds.size() +
                ", darkTheme=" + darkThemeEnabled +
                '}';
    }
}