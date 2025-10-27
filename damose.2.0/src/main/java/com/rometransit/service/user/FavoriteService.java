package com.rometransit.service.user;

import com.rometransit.model.entity.Favorite;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing user favorites (routes and stops)
 */
public class FavoriteService {
    private static final String FAVORITES_FILE = "favorites.json";
    private static final String FAVORITES_DIR = System.getProperty("user.home") + "/.damose";

    private final ObjectMapper objectMapper;
    private final File favoritesFile;
    private List<Favorite> favorites;

    public FavoriteService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        // Initialize favorites directory and file
        File favoritesDir = new File(FAVORITES_DIR);
        if (!favoritesDir.exists()) {
            favoritesDir.mkdirs();
        }

        this.favoritesFile = new File(favoritesDir, FAVORITES_FILE);
        this.favorites = new ArrayList<>();

        loadFavorites();
    }

    /**
     * Add a favorite for a user
     */
    public boolean addFavorite(String userId, String itemId, Favorite.FavoriteType type) {
        if (userId == null || itemId == null || type == null) {
            return false;
        }

        // Check if already exists
        boolean exists = favorites.stream()
                .anyMatch(f -> f.getUserId().equals(userId) &&
                         f.getType() == type &&
                         (type == Favorite.FavoriteType.ROUTE ?
                          itemId.equals(f.getRouteId()) : itemId.equals(f.getStopId())));

        if (exists) {
            return false; // Already favorited
        }

        Favorite favorite = new Favorite();
        favorite.setFavoriteId(UUID.randomUUID().toString());
        favorite.setUserId(userId);
        favorite.setType(type);
        favorite.setCreatedAt(java.time.LocalDateTime.now());
        favorite.setLastUsed(java.time.LocalDateTime.now());
        favorite.setUsageCount(0);
        favorite.setActive(true);

        if (type == Favorite.FavoriteType.ROUTE) {
            favorite.setRouteId(itemId);
        } else if (type == Favorite.FavoriteType.STOP) {
            favorite.setStopId(itemId);
        }

        favorites.add(favorite);
        saveFavorites();

        System.out.println("⭐ Favorite added: " + type + " - " + itemId);
        return true;
    }

    /**
     * Remove a favorite
     */
    public boolean removeFavorite(String userId, String itemId, Favorite.FavoriteType type) {
        boolean removed = favorites.removeIf(f ->
            f.getUserId().equals(userId) &&
            f.getType() == type &&
            (type == Favorite.FavoriteType.ROUTE ?
             itemId.equals(f.getRouteId()) : itemId.equals(f.getStopId())));

        if (removed) {
            saveFavorites();
            System.out.println("🗑️ Favorite removed: " + type + " - " + itemId);
        }

        return removed;
    }

    /**
     * Get all favorites for a user
     */
    public List<Favorite> getUserFavorites(String userId) {
        if (userId == null) {
            return new ArrayList<>();
        }

        return favorites.stream()
                .filter(f -> f.getUserId().equals(userId) && f.isActive())
                .collect(Collectors.toList());
    }

    /**
     * Get favorite routes for a user
     */
    public List<Favorite> getUserFavoriteRoutes(String userId) {
        return getUserFavorites(userId).stream()
                .filter(f -> f.getType() == Favorite.FavoriteType.ROUTE)
                .collect(Collectors.toList());
    }

    /**
     * Get favorite stops for a user
     */
    public List<Favorite> getUserFavoriteStops(String userId) {
        return getUserFavorites(userId).stream()
                .filter(f -> f.getType() == Favorite.FavoriteType.STOP)
                .collect(Collectors.toList());
    }

    /**
     * Check if item is favorited by user
     */
    public boolean isFavorite(String userId, String itemId, Favorite.FavoriteType type) {
        return favorites.stream()
                .anyMatch(f -> f.getUserId().equals(userId) &&
                         f.getType() == type &&
                         f.isActive() &&
                         (type == Favorite.FavoriteType.ROUTE ?
                          itemId.equals(f.getRouteId()) : itemId.equals(f.getStopId())));
    }

    /**
     * Mark favorite as used (update usage count and last used time)
     */
    public void markAsUsed(String userId, String itemId, Favorite.FavoriteType type) {
        favorites.stream()
                .filter(f -> f.getUserId().equals(userId) &&
                       f.getType() == type &&
                       (type == Favorite.FavoriteType.ROUTE ?
                        itemId.equals(f.getRouteId()) : itemId.equals(f.getStopId())))
                .findFirst()
                .ifPresent(favorite -> {
                    favorite.markAsUsed();
                    saveFavorites();
                });
    }

    /**
     * Load favorites from JSON file
     */
    private void loadFavorites() {
        try {
            if (favoritesFile.exists()) {
                favorites = objectMapper.readValue(favoritesFile, new TypeReference<List<Favorite>>() {});
                System.out.println("⭐ Loaded " + favorites.size() + " favorites from " + favoritesFile.getAbsolutePath());
            } else {
                favorites = new ArrayList<>();
                System.out.println("📝 Created new favorites file: " + favoritesFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading favorites: " + e.getMessage());
            favorites = new ArrayList<>();
        }
    }

    /**
     * Save favorites to JSON file
     */
    private void saveFavorites() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(favoritesFile, favorites);
            System.out.println("💾 Favorites saved to " + favoritesFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Error saving favorites: " + e.getMessage());
        }
    }

    /**
     * Clear all favorites for a user
     */
    public void clearUserFavorites(String userId) {
        favorites.removeIf(f -> f.getUserId().equals(userId));
        saveFavorites();
        System.out.println("🗑️ Cleared all favorites for user: " + userId);
    }
}
