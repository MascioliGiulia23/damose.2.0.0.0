package com.rometransit.util.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JsonUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    public static <T> String toJson(T object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(object);
    }
    
    public static <T> String toPrettyJson(T object) throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }
    
    public static <T> T fromJson(String json, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(json, clazz);
    }
    
    public static <T> T fromJson(InputStream inputStream, Class<T> clazz) throws IOException {
        return objectMapper.readValue(inputStream, clazz);
    }
    
    public static <T> T fromJsonFile(Path filePath, Class<T> clazz) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }
        String jsonContent = Files.readString(filePath);
        return fromJson(jsonContent, clazz);
    }
    
    public static <T> List<T> fromJsonArray(String json, Class<T> elementClass) throws JsonProcessingException {
        CollectionType listType = TypeFactory.defaultInstance().constructCollectionType(List.class, elementClass);
        return objectMapper.readValue(json, listType);
    }
    
    public static <T> List<T> fromJsonArrayFile(Path filePath, Class<T> elementClass) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }
        String jsonContent = Files.readString(filePath);
        return fromJsonArray(jsonContent, elementClass);
    }
    
    public static void writeJsonToFile(Object object, Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        String json = toPrettyJson(object);
        Files.writeString(filePath, json);
    }
    
    public static boolean isValidJson(String json) {
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> jsonToMap(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, Map.class);
    }
    
    public static String mapToJson(Map<String, Object> map) throws JsonProcessingException {
        return toJson(map);
    }
    
    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}