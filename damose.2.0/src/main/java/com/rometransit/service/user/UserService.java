package com.rometransit.service.user;

import com.rometransit.data.repository.UserRepository;
import com.rometransit.model.entity.User;
import com.rometransit.util.exception.AuthenticationException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class UserService {
    private static UserService instance;
    
    private final UserRepository userRepository;
    private final SecureRandom secureRandom;

    private UserService() {
        this.userRepository = new UserRepository();
        this.secureRandom = new SecureRandom();
    }

    public static synchronized UserService getInstance() {
        if (instance == null) {
            instance = new UserService();
        }
        return instance;
    }

    // Service lifecycle methods
    public void initialize() {
        System.out.println("UserService initialized");
        // Initialize any necessary resources
    }

    public void shutdown() {
        System.out.println("UserService shutting down");
        // Clean up resources if needed
    }

    public User registerUser(String username, String email, String password, String firstName, String lastName) throws AuthenticationException {
        // Validate input
        validateRegistrationInput(username, email, password);

        // Check if user already exists
        if (userRepository.existsByUsername(username)) {
            throw new AuthenticationException("Username already exists: " + username);
        }

        if (userRepository.existsByEmail(email)) {
            throw new AuthenticationException("Email already exists: " + email);
        }

        // Hash password
        String passwordHash = hashPassword(password);

        // Create user
        User user = userRepository.createUser(username, email, passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        
        return userRepository.save(user);
    }

    public User loginUser(String username, String password) throws AuthenticationException {
        String passwordHash = hashPassword(password);
        
        if (!userRepository.validateUser(username, passwordHash)) {
            throw new AuthenticationException("Invalid username or password");
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (!userOpt.isPresent()) {
            throw new AuthenticationException("User not found");
        }

        User user = userOpt.get();
        if (!user.isActive()) {
            throw new AuthenticationException("User account is disabled");
        }

        // Update last login
        userRepository.updateLastLogin(user.getUserId());
        
        return user;
    }

    public void logoutUser(String userId) {
        // In a real application, this might invalidate session tokens
        // For now, we just update the last activity
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setLastLoginDate(LocalDateTime.now());
            userRepository.save(user);
        }
    }

    public User updateProfile(String userId, String firstName, String lastName, String email) throws AuthenticationException {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            throw new AuthenticationException("User not found");
        }

        try {
            userRepository.updateProfile(userId, firstName, lastName, email);
            return userRepository.findById(userId).get();
        } catch (IllegalArgumentException e) {
            throw new AuthenticationException(e.getMessage());
        }
    }

    public void changePassword(String userId, String currentPassword, String newPassword) throws AuthenticationException {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            throw new AuthenticationException("User not found");
        }

        User user = userOpt.get();
        String currentPasswordHash = hashPassword(currentPassword);
        
        if (!user.getPasswordHash().equals(currentPasswordHash)) {
            throw new AuthenticationException("Current password is incorrect");
        }

        validatePassword(newPassword);
        String newPasswordHash = hashPassword(newPassword);
        userRepository.updatePassword(userId, newPasswordHash);
    }

    public void deactivateUser(String userId) {
        userRepository.deactivateUser(userId);
    }

    public void reactivateUser(String userId) {
        userRepository.activateUser(userId);
    }

    public Optional<User> getUserById(String userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getActiveUsers() {
        return userRepository.findActiveUsers();
    }

    public List<User> getRecentUsers(int days) {
        return userRepository.findRecentUsers(days);
    }

    public boolean isUsernameAvailable(String username) {
        return !userRepository.existsByUsername(username);
    }

    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
    }

    public long getTotalUserCount() {
        return userRepository.count();
    }

    public long getActiveUserCount() {
        return userRepository.countActiveUsers();
    }

    public long getRecentUserCount(int days) {
        return userRepository.countRecentUsers(days);
    }

    private void validateRegistrationInput(String username, String email, String password) throws AuthenticationException {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthenticationException("Username is required");
        }

        if (username.length() < 3 || username.length() > 50) {
            throw new AuthenticationException("Username must be between 3 and 50 characters");
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new AuthenticationException("Username can only contain letters, numbers, and underscores");
        }

        if (email == null || email.trim().isEmpty()) {
            throw new AuthenticationException("Email is required");
        }

        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new AuthenticationException("Invalid email format");
        }

        validatePassword(password);
    }

    private void validatePassword(String password) throws AuthenticationException {
        if (password == null || password.isEmpty()) {
            throw new AuthenticationException("Password is required");
        }

        if (password.length() < 6) {
            throw new AuthenticationException("Password must be at least 6 characters long");
        }

        if (password.length() > 128) {
            throw new AuthenticationException("Password is too long");
        }

        // Check for at least one letter and one digit
        if (!password.matches(".*[a-zA-Z].*") || !password.matches(".*\\d.*")) {
            throw new AuthenticationException("Password must contain at least one letter and one digit");
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            
            // Add salt for better security
            String salt = "damose2024"; // In production, use random salt per user
            String saltedPassword = password + salt;
            
            byte[] hashedBytes = md.digest(saltedPassword.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedBytes) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public boolean validateSession(String userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (!userOpt.isPresent()) {
            return false;
        }

        User user = userOpt.get();
        return user.isActive() && user.getLastLoginDate() != null &&
               user.getLastLoginDate().isAfter(LocalDateTime.now().minusHours(24));
    }

    public String generatePasswordResetToken(String email) throws AuthenticationException {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (!userOpt.isPresent()) {
            throw new AuthenticationException("No user found with this email");
        }

        // Generate a simple token (in production, use proper JWT or similar)
        return "reset_" + System.currentTimeMillis() + "_" + secureRandom.nextInt(10000);
    }

    public void resetPassword(String resetToken, String newPassword) throws AuthenticationException {
        // In a real implementation, you would validate the token and find the associated user
        validatePassword(newPassword);
        
        // For now, just validate the password format
        // The actual reset would require token validation and user identification
        throw new AuthenticationException("Password reset not yet implemented");
    }
}