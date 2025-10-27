package com.rometransit.data.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;

/**
 * Legacy DatabaseManager stub for backward compatibility
 *
 * This class is deprecated and replaced by SQLiteDatabaseManager
 * It's kept as a stub to avoid breaking existing code during migration
 *
 * @deprecated Use {@link SQLiteDatabaseManager} and {@link com.rometransit.data.repository.GTFSRepository} instead
 */
@Deprecated
public class DatabaseManager {
    private static DatabaseManager instance;
    private final ObjectMapper objectMapper;

    private DatabaseManager() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        System.out.println("⚠️  Legacy DatabaseManager stub initialized - use SQLiteDatabaseManager instead");
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public <T> void save(String collection, String id, T object) {
        // No-op stub
    }

    public <T> Optional<T> find(String collection, String id, Class<T> clazz) {
        return Optional.empty();
    }

    public <T> List<T> findAll(String collection, Class<T> clazz) {
        return new ArrayList<>();
    }

    public void delete(String collection, String id) {
        // No-op stub
    }

    public boolean exists(String collection, String id) {
        return false;
    }

    public long count(String collection) {
        return 0;
    }

    public void flush() {
        // No-op stub
    }

    public void shutdown() {
        // No-op stub
    }

    public void clearCollection(String collection) {
        // No-op stub
    }

    public Set<String> getCollectionNames() {
        return new HashSet<>();
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
