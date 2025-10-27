package com.rometransit.util.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

public class SecurityUtil {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String SALT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DEFAULT_SALT_LENGTH = 16;
    
    // Password validation patterns
    private static final Pattern STRONG_PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$"
    );
    
    public static String generateSalt() {
        return generateSalt(DEFAULT_SALT_LENGTH);
    }
    
    public static String generateSalt(int length) {
        StringBuilder salt = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int randomIndex = secureRandom.nextInt(SALT_CHARS.length());
            salt.append(SALT_CHARS.charAt(randomIndex));
        }
        return salt.toString();
    }
    
    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String saltedPassword = password + salt;
            byte[] hash = digest.digest(saltedPassword.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    public static boolean verifyPassword(String password, String salt, String hashedPassword) {
        String computedHash = hashPassword(password, salt);
        return computedHash.equals(hashedPassword);
    }
    
    public static String generateSessionId() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    

    public static boolean isStrongPassword(String password) {
        return password != null && STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }
    
    public static PasswordStrength checkPasswordStrength(String password) {
        if (password == null || password.isEmpty()) {
            return PasswordStrength.VERY_WEAK;
        }
        
        int score = 0;
        
        // Length check
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        
        // Character variety checks
        if (password.matches(".*[a-z].*")) score++; // lowercase
        if (password.matches(".*[A-Z].*")) score++; // uppercase
        if (password.matches(".*\\d.*")) score++;   // digits
        if (password.matches(".*[@$!%*?&].*")) score++; // special chars
        
        // Additional complexity
        if (password.length() >= 16) score++;
        
        switch (score) {
            case 0:
            case 1:
                return PasswordStrength.VERY_WEAK;
            case 2:
            case 3:
                return PasswordStrength.WEAK;
            case 4:
            case 5:
                return PasswordStrength.MEDIUM;
            case 6:
                return PasswordStrength.STRONG;
            case 7:
            default:
                return PasswordStrength.VERY_STRONG;
        }
    }
    
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // Remove potentially dangerous characters
        return input.replaceAll("[<>\"'&]", "")
                   .trim();
    }
    
    public static String sanitizeFilename(String filename) {
        if (filename == null) {
            return null;
        }
        
        // Remove path traversal attempts and dangerous characters
        return filename.replaceAll("[/\\\\:*?\"<>|]", "_")
                      .replaceAll("^\\.+", "")
                      .trim();
    }
    
    public static boolean isValidUserId(String userId) {
        return userId != null && 
               userId.length() >= 3 && 
               userId.length() <= 50 && 
               userId.matches("^[a-zA-Z0-9._-]+$");
    }
    
    public static String generateApiKey() {
        byte[] randomBytes = new byte[24];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
    
    public static String maskSensitiveData(String data) {
        if (data == null || data.length() <= 4) {
            return "****";
        }
        
        int visibleChars = Math.min(2, data.length() / 4);
        String prefix = data.substring(0, visibleChars);
        String suffix = data.substring(data.length() - visibleChars);
        String middle = "*".repeat(Math.max(4, data.length() - 2 * visibleChars));
        
        return prefix + middle + suffix;
    }
    
    public static boolean isSecureConnection(String url) {
        return url != null && url.toLowerCase().startsWith("https://");
    }
    
    public static String hashText(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
    
    public enum PasswordStrength {
        VERY_WEAK("Very Weak", "#ff0000"),
        WEAK("Weak", "#ff8800"),
        MEDIUM("Medium", "#ffff00"),
        STRONG("Strong", "#88ff00"),
        VERY_STRONG("Very Strong", "#00ff00");
        
        private final String description;
        private final String color;
        
        PasswordStrength(String description, String color) {
            this.description = description;
            this.color = color;
        }
        
        public String getDescription() {
            return description;
        }
        
        public String getColor() {
            return color;
        }
    }
}