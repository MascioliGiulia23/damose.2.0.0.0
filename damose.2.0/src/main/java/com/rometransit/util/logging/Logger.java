package com.rometransit.util.logging;

import com.rometransit.util.config.AppConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class Logger {
    private static final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final String name;
    private final AppConfig config;
    private LogLevel currentLevel;

    private Logger(String name) {
        this.name = name;
        this.config = AppConfig.getInstance();
        this.currentLevel = LogLevel.fromString(config.getLoggingLevel());
    }

    public static Logger getLogger(String name) {
        return loggers.computeIfAbsent(name, Logger::new);
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getSimpleName());
    }

    public void trace(String message) {
        log(LogLevel.TRACE, message, null);
    }

    public void trace(String message, Throwable throwable) {
        log(LogLevel.TRACE, message, throwable);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void debug(String message, Throwable throwable) {
        log(LogLevel.DEBUG, message, throwable);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void info(String message, Throwable throwable) {
        log(LogLevel.INFO, message, throwable);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void warn(String message, Throwable throwable) {
        log(LogLevel.WARN, message, throwable);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable throwable) {
        log(LogLevel.ERROR, message, throwable);
    }

    // Static convenience method for backward compatibility
    private static final Logger defaultLogger = getLogger("Default");

    public static void log(String message) {
        defaultLogger.info(message);
    }

    private void log(LogLevel level, String message, Throwable throwable) {
        if (!isLevelEnabled(level)) {
            return;
        }

        String logEntry = formatLogEntry(level, message, throwable);

        // Log to console
        if (level.ordinal() >= LogLevel.WARN.ordinal()) {
            System.err.println(logEntry);
        } else {
            System.out.println(logEntry);
        }

        // Log to file
        logToFile(logEntry);
    }

    private boolean isLevelEnabled(LogLevel level) {
        return level.ordinal() >= currentLevel.ordinal();
    }

    private String formatLogEntry(LogLevel level, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        
        // Timestamp
        sb.append(LocalDateTime.now().format(TIMESTAMP_FORMAT));
        sb.append(" ");
        
        // Level
        sb.append("[").append(level.name()).append("]");
        sb.append(" ");
        
        // Logger name
        sb.append("[").append(name).append("]");
        sb.append(" ");
        
        // Message
        sb.append(message);
        
        // Exception
        if (throwable != null) {
            sb.append("\n").append(formatThrowable(throwable));
        }
        
        return sb.toString();
    }

    private String formatThrowable(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage());
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\n\tat ").append(element.toString());
        }
        
        if (throwable.getCause() != null) {
            sb.append("\nCaused by: ").append(formatThrowable(throwable.getCause()));
        }
        
        return sb.toString();
    }

    private void logToFile(String logEntry) {
        try {
            Path logFile = Paths.get(config.getLoggingFile());
            
            // Create parent directories if they don't exist
            Files.createDirectories(logFile.getParent());
            
            // Check file size and rotate if necessary
            if (Files.exists(logFile) && shouldRotateLog(logFile)) {
                rotateLogFile(logFile);
            }
            
            // Write log entry
            Files.writeString(logFile, logEntry + "\n", 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }

    private boolean shouldRotateLog(Path logFile) {
        try {
            long size = Files.size(logFile);
            long maxSize = parseLogFileSize(config.getLoggingMaxSize());
            return size >= maxSize;
        } catch (IOException e) {
            return false;
        }
    }

    private long parseLogFileSize(String sizeStr) {
        if (sizeStr == null || sizeStr.trim().isEmpty()) {
            return 10 * 1024 * 1024; // Default 10MB
        }
        
        String size = sizeStr.trim().toUpperCase();
        long multiplier = 1;
        
        if (size.endsWith("KB")) {
            multiplier = 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("MB")) {
            multiplier = 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        } else if (size.endsWith("GB")) {
            multiplier = 1024 * 1024 * 1024;
            size = size.substring(0, size.length() - 2);
        }
        
        try {
            return Long.parseLong(size.trim()) * multiplier;
        } catch (NumberFormatException e) {
            return 10 * 1024 * 1024; // Default 10MB
        }
    }

    private void rotateLogFile(Path logFile) throws IOException {
        int maxFiles = config.getLoggingMaxFiles();
        
        // Shift existing log files
        for (int i = maxFiles - 1; i > 0; i--) {
            Path oldFile = Paths.get(logFile.toString() + "." + i);
            Path newFile = Paths.get(logFile.toString() + "." + (i + 1));
            
            if (Files.exists(oldFile)) {
                if (i == maxFiles - 1) {
                    Files.delete(oldFile);
                } else {
                    Files.move(oldFile, newFile);
                }
            }
        }
        
        // Move current log file to .1
        Path firstRotated = Paths.get(logFile.toString() + ".1");
        Files.move(logFile, firstRotated);
    }

    public void setLevel(LogLevel level) {
        this.currentLevel = level;
    }

    public LogLevel getLevel() {
        return currentLevel;
    }

    public boolean isTraceEnabled() {
        return isLevelEnabled(LogLevel.TRACE);
    }

    public boolean isDebugEnabled() {
        return isLevelEnabled(LogLevel.DEBUG);
    }

    public boolean isInfoEnabled() {
        return isLevelEnabled(LogLevel.INFO);
    }

    public boolean isWarnEnabled() {
        return isLevelEnabled(LogLevel.WARN);
    }

    public boolean isErrorEnabled() {
        return isLevelEnabled(LogLevel.ERROR);
    }

    public enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR;

        public static LogLevel fromString(String level) {
            if (level == null) {
                return INFO;
            }
            
            try {
                return valueOf(level.toUpperCase());
            } catch (IllegalArgumentException e) {
                return INFO;
            }
        }
    }
}