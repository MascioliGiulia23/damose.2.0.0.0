package com.rometransit.model.entity;

import java.time.LocalDateTime;
import java.util.Objects;

public class User {
    private String userId;
    private String username;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    private String firstName;
    private String lastName;
    private LocalDateTime registrationDate;
    private LocalDateTime lastLoginDate;
    private boolean isActive;
    private String preferredLanguage;

    public User() {}

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.registrationDate = LocalDateTime.now();
        this.isActive = true;
        this.preferredLanguage = "it";
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDateTime registrationDate) {
        this.registrationDate = registrationDate;
    }

    public LocalDateTime getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(LocalDateTime lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    // Alias methods for backward compatibility with UserRepository
    public String getPassword() {
        return getPasswordHash();
    }

    public void setPassword(String password) {
        setPasswordHash(password);
    }

    public LocalDateTime getCreatedAt() {
        return getRegistrationDate();
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        setRegistrationDate(createdAt);
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        setLastLoginDate(lastLogin);
    }

    public void setDisplayName(String displayName) {
        // Display name can be firstName + lastName
        if (displayName != null && displayName.contains(" ")) {
            String[] parts = displayName.split(" ", 2);
            setFirstName(parts[0]);
            if (parts.length > 1) {
                setLastName(parts[1]);
            }
        } else {
            setFirstName(displayName);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(userId, user.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                '}';
    }
}