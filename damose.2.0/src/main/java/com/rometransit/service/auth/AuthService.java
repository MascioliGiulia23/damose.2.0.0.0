package com.rometransit.service.auth;

import com.rometransit.model.entity.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for user authentication, registration, and user management
 * Singleton pattern to ensure consistent user state across the application
 */
public class AuthService {
    private static final String USERS_FILE = "users.json";
    private static final String USERS_DIR = System.getProperty("user.home") + "/.damose";
    private static final int SALT_LENGTH = 32;

    private static AuthService instance;

    private final ObjectMapper objectMapper;
    private final File usersFile;
    private final SecureRandom secureRandom;

    private User currentUser;
    private List<User> users;

    private AuthService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.secureRandom = new SecureRandom();

        // Initialize users directory and file
        File usersDir = new File(USERS_DIR);
        if (!usersDir.exists()) {
            usersDir.mkdirs();
        }

        this.usersFile = new File(usersDir, USERS_FILE);
        this.users = new ArrayList<>();

        loadUsers();
    }

    /**
     * Get singleton instance of AuthService
     */
    public static synchronized AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    /**
     * Register a new user
     */
    public boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters long");
        }

        // Check if username already exists
        if (users.stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(username))) {
            return false; // Username already exists
        }

        // Generate salt and hash password
        String salt = generateSalt();
        String hashedPassword = hashPassword(password, salt);

        // Create new user
        User newUser = new User();
        newUser.setUserId(UUID.randomUUID().toString());
        newUser.setUsername(username.trim());
        newUser.setPasswordHash(hashedPassword);
        newUser.setPasswordSalt(salt);
        newUser.setRegistrationDate(LocalDateTime.now());
        newUser.setActive(true);
        newUser.setPreferredLanguage("it");

        users.add(newUser);
        saveUsers();

        System.out.println("👤 Utente registrato: " + username);
        return true;
    }

    /**
     * Login user with username and password
     */
    public boolean loginUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        Optional<User> userOpt = users.stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username.trim()))
                .findFirst();

        if (userOpt.isEmpty()) {
            return false; // User not found
        }

        User user = userOpt.get();
        String hashedPassword = hashPassword(password, user.getPasswordSalt());

        if (hashedPassword.equals(user.getPasswordHash())) {
            // Update last login
            user.setLastLoginDate(LocalDateTime.now());
            saveUsers();

            this.currentUser = user;
            System.out.println("✅ Login successful: " + username);
            return true;
        }

        return false; // Wrong password
    }

    /**
     * Logout current user
     */
    public void logout() {
        if (currentUser != null) {
            System.out.println("👋 Logout: " + currentUser.getUsername());
            currentUser = null;
        }
    }

    /**
     * Check if a user is currently logged in
     */
    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /**
     * Get current logged in user
     */
    public User getCurrentUser() {
        return currentUser;
    }

    /**
     * Get all registered users (for admin purposes)
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(users);
    }

    /**
     * Generate a random salt
     */
    private String generateSalt() {
        byte[] saltBytes = new byte[SALT_LENGTH];
        secureRandom.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Hash password with salt using SHA-256
     */
    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hashedBytes = md.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Load users from JSON file
     */
    private void loadUsers() {
        try {
            if (usersFile.exists()) {
                users = objectMapper.readValue(usersFile, new TypeReference<List<User>>() {});
                System.out.println("👥 Loaded " + users.size() + " users from " + usersFile.getAbsolutePath());
            } else {
                users = new ArrayList<>();
                System.out.println("📝 Created new users file: " + usersFile.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("❌ Error loading users: " + e.getMessage());
            users = new ArrayList<>();
        }
    }

    /**
     * Save users to JSON file
     */
    private void saveUsers() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(usersFile, users);
            System.out.println("💾 Users saved to " + usersFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("❌ Error saving users: " + e.getMessage());
        }
    }

    /**
     * Delete a user (admin function)
     */
    public boolean deleteUser(String username) {
        boolean removed = users.removeIf(u -> u.getUsername().equalsIgnoreCase(username));
        if (removed) {
            saveUsers();
            System.out.println("🗑️ User deleted: " + username);
        }
        return removed;
    }

    /**
     * Change password for current user
     */
    public boolean changePassword(String oldPassword, String newPassword) {
        if (currentUser == null) {
            return false;
        }

        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("New password must be at least 4 characters long");
        }

        // Verify old password
        String oldHashedPassword = hashPassword(oldPassword, currentUser.getPasswordSalt());
        if (!oldHashedPassword.equals(currentUser.getPasswordHash())) {
            return false; // Wrong old password
        }

        // Generate new salt and hash new password
        String newSalt = generateSalt();
        String newHashedPassword = hashPassword(newPassword, newSalt);

        // Update user
        currentUser.setPasswordHash(newHashedPassword);
        currentUser.setPasswordSalt(newSalt);
        saveUsers();

        System.out.println("🔑 Password changed for: " + currentUser.getUsername());
        return true;
    }
}