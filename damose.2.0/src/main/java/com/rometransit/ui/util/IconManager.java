package com.rometransit.ui.util;

import com.rometransit.model.enums.TransportType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.HashMap;
import java.util.Map;

public class IconManager {
    private static IconManager instance;
    private final Map<String, Image> iconCache;
    
    private static final String ICONS_PATH = "/icons/";
    
    private IconManager() {
        this.iconCache = new HashMap<>();
        preloadIcons();
    }
    
    public static synchronized IconManager getInstance() {
        if (instance == null) {
            instance = new IconManager();
        }
        return instance;
    }
    
    private void preloadIcons() {
        try {
            loadIcon("bus.png");
            loadIcon("tram.png");
            loadIcon("metro.png");
            loadIcon("stop.png");
            loadIcon("location.png");
            loadIcon("favorite.png");
            loadIcon("favorite-filled.png");
            loadIcon("search.png");
            loadIcon("settings.png");
            loadIcon("user.png");
            loadIcon("refresh.png");
            loadIcon("warning.png");
            loadIcon("error.png");
            loadIcon("info.png");
            loadIcon("success.png");
        } catch (Exception e) {
            System.err.println("Errore nel precaricamento delle icone: " + e.getMessage());
        }
    }
    
    private void loadIcon(String iconName) {
        try {
            String path = ICONS_PATH + iconName;
            var stream = getClass().getResourceAsStream(path);
            if (stream != null) {
                Image icon = new Image(stream);
                iconCache.put(iconName, icon);
            } else {
                // Crea un'icona placeholder se il file non esiste
                createPlaceholderIcon(iconName);
            }
        } catch (Exception e) {
            System.err.println("Errore nel caricamento dell'icona " + iconName + ": " + e.getMessage());
            createPlaceholderIcon(iconName);
        }
    }
    
    private void createPlaceholderIcon(String iconName) {
        // Crea un'immagine placeholder semplice
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(24, 24);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
        
        // Sfondo grigio
        gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
        gc.fillRect(0, 0, 24, 24);
        
        // Bordo
        gc.setStroke(javafx.scene.paint.Color.DARKGRAY);
        gc.strokeRect(0, 0, 24, 24);
        
        // Simbolo "?" al centro
        gc.setFill(javafx.scene.paint.Color.DARKGRAY);
        gc.setFont(javafx.scene.text.Font.font(16));
        gc.fillText("?", 8, 16);
        
        javafx.scene.image.WritableImage placeholder = new javafx.scene.image.WritableImage(24, 24);
        canvas.snapshot(null, placeholder);
        iconCache.put(iconName, placeholder);
    }
    
    public Image getIcon(String iconName) {
        Image icon = iconCache.get(iconName);
        if (icon == null) {
            loadIcon(iconName);
            icon = iconCache.get(iconName);
        }
        return icon;
    }
    
    public ImageView getIconView(String iconName, double size) {
        Image icon = getIcon(iconName);
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        return imageView;
    }
    
    public ImageView getIconView(String iconName) {
        return getIconView(iconName, 16);
    }
    
    public Image getTransportTypeIcon(TransportType transportType) {
        return switch (transportType) {
            case BUS -> getIcon("bus.png");
            case TRAM -> getIcon("tram.png");
            case METRO -> getIcon("metro.png");
            case TRAIN -> getIcon("train.png");
            case FERRY -> getIcon("ferry.png");
        };
    }
    
    public ImageView getTransportTypeIconView(TransportType transportType, double size) {
        Image icon = getTransportTypeIcon(transportType);
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        return imageView;
    }
    
    public ImageView getTransportTypeIconView(TransportType transportType) {
        return getTransportTypeIconView(transportType, 16);
    }
    
    public Image getStopIcon() {
        return getIcon("stop.png");
    }
    
    public ImageView getStopIconView(double size) {
        return getIconView("stop.png", size);
    }
    
    public Image getFavoriteIcon(boolean filled) {
        return getIcon(filled ? "favorite-filled.png" : "favorite.png");
    }
    
    public ImageView getFavoriteIconView(boolean filled, double size) {
        return getIconView(filled ? "favorite-filled.png" : "favorite.png", size);
    }
    
    public Image getStatusIcon(String status) {
        return switch (status.toLowerCase()) {
            case "success", "online" -> getIcon("success.png");
            case "warning", "limited" -> getIcon("warning.png");
            case "error", "offline" -> getIcon("error.png");
            default -> getIcon("info.png");
        };
    }
    
    public ImageView getStatusIconView(String status, double size) {
        Image icon = getStatusIcon(status);
        ImageView imageView = new ImageView(icon);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        return imageView;
    }
    
    public void clearCache() {
        iconCache.clear();
    }
    
    public int getCacheSize() {
        return iconCache.size();
    }
}