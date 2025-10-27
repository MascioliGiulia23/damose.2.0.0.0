package com.rometransit.data.repository;

import com.rometransit.data.database.DatabaseManager;
import com.rometransit.model.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Legacy UserRepository stub for backward compatibility
 *
 * @deprecated User data not yet migrated to SQLite - uses legacy DatabaseManager
 */
@Deprecated
public class UserRepository {

    private final DatabaseManager db;

    public UserRepository() {
        this.db = DatabaseManager.getInstance();
        System.out.println("⚠️  UserRepository stub - uses legacy DatabaseManager");
    }

    public Optional<User> findById(String userId) {
        return db.find("users", userId, User.class);
    }

    public List<User> findAll() {
        return db.findAll("users", User.class);
    }

    public Optional<User> findByUsername(String username) {
        // Simple search in all users
        return findAll().stream()
            .filter(u -> username.equals(u.getUsername()))
            .findFirst();
    }

    public User save(User user) {
        db.save("users", user.getUserId(), user);
        return user;
    }

    public void delete(String userId) {
        db.delete("users", userId);
    }

    public long count() {
        return db.count("users");
    }

    public boolean exists(String userId) {
        return db.exists("users", userId);
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username).isPresent();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }

    public User createUser(String username, String email, String password) {
        User user = new User();
        user.setUserId(java.util.UUID.randomUUID().toString());
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setActive(true);
        save(user);
        return user;
    }

    public boolean validateUser(String username, String password) {
        Optional<User> userOpt = findByUsername(username);
        return userOpt.isPresent() && password.equals(userOpt.get().getPassword());
    }

    public void updateLastLogin(String userId) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setLastLogin(java.time.LocalDateTime.now());
            save(user);
        }
    }

    public void updateProfile(String userId, String username, String email, String displayName) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (username != null) user.setUsername(username);
            if (email != null) user.setEmail(email);
            if (displayName != null) user.setDisplayName(displayName);
            save(user);
        }
    }

    public void updatePassword(String userId, String newPassword) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(newPassword);
            save(user);
        }
    }

    public void deactivateUser(String userId) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(false);
            save(user);
        }
    }

    public void activateUser(String userId) {
        Optional<User> userOpt = findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setActive(true);
            save(user);
        }
    }

    public Optional<User> findByEmail(String email) {
        return findAll().stream()
            .filter(u -> email.equals(u.getEmail()))
            .findFirst();
    }

    public List<User> findActiveUsers() {
        return findAll().stream()
            .filter(User::isActive)
            .toList();
    }

    public List<User> findRecentUsers(int days) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        return findAll().stream()
            .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(cutoff))
            .toList();
    }

    public long countActiveUsers() {
        return findAll().stream()
            .filter(User::isActive)
            .count();
    }

    public long countRecentUsers(int days) {
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusDays(days);
        return findAll().stream()
            .filter(u -> u.getCreatedAt() != null && u.getCreatedAt().isAfter(cutoff))
            .count();
    }
}
