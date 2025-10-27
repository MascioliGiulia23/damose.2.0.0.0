package com.rometransit.util.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DatabaseConfig {
    private static DatabaseConfig instance;
    private final AppConfig appConfig;
    
    private String dataDirectory;
    private boolean backupEnabled;
    private int backupIntervalSeconds;
    private int maxBackupFiles;
    private boolean encryptionEnabled;

    private DatabaseConfig() {
        this.appConfig = AppConfig.getInstance();
        loadConfiguration();
        ensureDirectoriesExist();
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    private void loadConfiguration() {
        this.dataDirectory = appConfig.getDatabaseDirectory();
        this.backupEnabled = appConfig.isDatabaseBackupEnabled();
        this.backupIntervalSeconds = appConfig.getDatabaseBackupInterval();
        this.maxBackupFiles = appConfig.getIntProperty("database.max_backup_files", 10);
        this.encryptionEnabled = appConfig.getBooleanProperty("database.encryption_enabled", false);
    }

    private void ensureDirectoriesExist() {
        try {
            // Create main data directory
            Path dataPath = Paths.get(dataDirectory);
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
                System.out.println("Created data directory: " + dataPath);
            }

            // Create backup directory if backup is enabled
            if (backupEnabled) {
                Path backupPath = Paths.get(dataDirectory, "backups");
                if (!Files.exists(backupPath)) {
                    Files.createDirectories(backupPath);
                    System.out.println("Created backup directory: " + backupPath);
                }
            }

            // Create logs directory
            Path logsPath = Paths.get(dataDirectory, "..", "logs");
            if (!Files.exists(logsPath)) {
                Files.createDirectories(logsPath);
                System.out.println("Created logs directory: " + logsPath);
            }

            // Create cache directory
            Path cachePath = Paths.get(dataDirectory, "cache");
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
                System.out.println("Created cache directory: " + cachePath);
            }

        } catch (IOException e) {
            System.err.println("Failed to create database directories: " + e.getMessage());
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public String getBackupDirectory() {
        return Paths.get(dataDirectory, "backups").toString();
    }

    public String getLogsDirectory() {
        return Paths.get(dataDirectory, "..", "logs").toString();
    }

    public String getCacheDirectory() {
        return Paths.get(dataDirectory, "cache").toString();
    }

    public boolean isBackupEnabled() {
        return backupEnabled;
    }

    public void setBackupEnabled(boolean backupEnabled) {
        this.backupEnabled = backupEnabled;
    }

    public int getBackupIntervalSeconds() {
        return backupIntervalSeconds;
    }

    public void setBackupIntervalSeconds(int backupIntervalSeconds) {
        this.backupIntervalSeconds = backupIntervalSeconds;
    }

    public int getMaxBackupFiles() {
        return maxBackupFiles;
    }

    public void setMaxBackupFiles(int maxBackupFiles) {
        this.maxBackupFiles = maxBackupFiles;
    }

    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }

    public void setEncryptionEnabled(boolean encryptionEnabled) {
        this.encryptionEnabled = encryptionEnabled;
    }

    public String getDatabaseFilePath(String collectionName) {
        return Paths.get(dataDirectory, collectionName + ".json").toString();
    }

    public String getBackupFilePath(String collectionName, String timestamp) {
        return Paths.get(getBackupDirectory(), collectionName + "_" + timestamp + ".json").toString();
    }

    public long getDirectorySize() {
        try {
            return Files.walk(Paths.get(dataDirectory))
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public long getBackupDirectorySize() {
        try {
            Path backupPath = Paths.get(getBackupDirectory());
            if (!Files.exists(backupPath)) {
                return 0;
            }
            return Files.walk(backupPath)
                    .filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public void cleanupOldBackups() {
        try {
            Path backupDir = Paths.get(getBackupDirectory());
            if (!Files.exists(backupDir)) {
                return;
            }

            java.util.List<Path> backupFiles = Files.list(backupDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .sorted((p1, p2) -> {
                        try {
                            return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            // Keep only the most recent maxBackupFiles
            if (backupFiles.size() > maxBackupFiles) {
                for (int i = maxBackupFiles; i < backupFiles.size(); i++) {
                    Files.delete(backupFiles.get(i));
                    System.out.println("Deleted old backup: " + backupFiles.get(i).getFileName());
                }
            }

        } catch (IOException e) {
            System.err.println("Error cleaning up old backups: " + e.getMessage());
        }
    }

    public boolean isDatabaseHealthy() {
        try {
            Path dataPath = Paths.get(dataDirectory);
            
            // Check if directory exists and is writable
            if (!Files.exists(dataPath) || !Files.isWritable(dataPath)) {
                return false;
            }

            // Check available disk space (require at least 100MB)
            long usableSpace = Files.getFileStore(dataPath).getUsableSpace();
            if (usableSpace < 100 * 1024 * 1024) {
                return false;
            }

            // Check if we can create a test file
            Path testFile = Paths.get(dataDirectory, ".health_check");
            Files.write(testFile, "test".getBytes());
            Files.delete(testFile);

            return true;

        } catch (IOException e) {
            System.err.println("Database health check failed: " + e.getMessage());
            return false;
        }
    }

    public String getHealthStatus() {
        StringBuilder status = new StringBuilder();
        status.append("Database Configuration Status:\n");
        status.append("- Data Directory: ").append(dataDirectory).append("\n");
        status.append("- Directory exists: ").append(Files.exists(Paths.get(dataDirectory))).append("\n");
        status.append("- Directory writable: ").append(Files.isWritable(Paths.get(dataDirectory))).append("\n");
        status.append("- Backup enabled: ").append(backupEnabled).append("\n");
        status.append("- Encryption enabled: ").append(encryptionEnabled).append("\n");
        status.append("- Data size: ").append(formatBytes(getDirectorySize())).append("\n");
        
        if (backupEnabled) {
            status.append("- Backup size: ").append(formatBytes(getBackupDirectorySize())).append("\n");
            status.append("- Max backup files: ").append(maxBackupFiles).append("\n");
        }
        
        try {
            Path dataPath = Paths.get(dataDirectory);
            long usableSpace = Files.getFileStore(dataPath).getUsableSpace();
            status.append("- Available space: ").append(formatBytes(usableSpace)).append("\n");
        } catch (IOException e) {
            status.append("- Available space: Unknown\n");
        }
        
        status.append("- Health status: ").append(isDatabaseHealthy() ? "Healthy" : "Unhealthy");

        return status.toString();
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public void printConfiguration() {
        System.out.println("=== Database Configuration ===");
        System.out.println("Data Directory: " + dataDirectory);
        System.out.println("Backup Enabled: " + backupEnabled);
        System.out.println("Backup Interval: " + backupIntervalSeconds + " seconds");
        System.out.println("Max Backup Files: " + maxBackupFiles);
        System.out.println("Encryption Enabled: " + encryptionEnabled);
        System.out.println("Health Status: " + (isDatabaseHealthy() ? "Healthy" : "Unhealthy"));
        System.out.println("===============================");
    }
}