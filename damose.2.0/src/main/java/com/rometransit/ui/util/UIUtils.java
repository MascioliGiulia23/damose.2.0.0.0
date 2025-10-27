package com.rometransit.ui.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.Optional;

public class UIUtils {
    
    public static void showInfoDialog(String title, String message) {
        showInfoDialog(null, title, message);
    }
    
    public static void showInfoDialog(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void showWarningDialog(String title, String message) {
        showWarningDialog(null, title, message);
    }
    
    public static void showWarningDialog(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static void showErrorDialog(String title, String message) {
        showErrorDialog(null, title, message);
    }
    
    public static void showErrorDialog(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    public static boolean showConfirmDialog(String title, String message) {
        return showConfirmDialog(null, title, message);
    }
    
    public static boolean showConfirmDialog(Window owner, String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
    
    public static Optional<String> showInputDialog(String title, String message, String defaultValue) {
        return showInputDialog(null, title, message, defaultValue);
    }
    
    public static Optional<String> showInputDialog(Window owner, String title, String message, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(message);
        
        return dialog.showAndWait();
    }
    
    public static File showFileChooser(Stage stage, String title, FileChooser.ExtensionFilter... filters) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        
        if (filters != null && filters.length > 0) {
            fileChooser.getExtensionFilters().addAll(filters);
        }
        
        return fileChooser.showOpenDialog(stage);
    }
    
    public static File showSaveDialog(Stage stage, String title, String defaultFileName, 
                                     FileChooser.ExtensionFilter... filters) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        
        if (defaultFileName != null && !defaultFileName.isEmpty()) {
            fileChooser.setInitialFileName(defaultFileName);
        }
        
        if (filters != null && filters.length > 0) {
            fileChooser.getExtensionFilters().addAll(filters);
        }
        
        return fileChooser.showSaveDialog(stage);
    }
    
    public static void centerStageOnScreen(Stage stage) {
        stage.centerOnScreen();
    }
    
    public static void setStageIcon(Stage stage, String iconPath) {
        try {
            stage.getIcons().add(new javafx.scene.image.Image(
                UIUtils.class.getResourceAsStream(iconPath)
            ));
        } catch (Exception e) {
            System.err.println("Errore nel caricamento dell'icona: " + e.getMessage());
        }
    }
    
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public static String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + " sec";
        } else if (seconds < 3600) {
            int minutes = seconds / 60;
            int remainingSeconds = seconds % 60;
            if (remainingSeconds == 0) {
                return minutes + " min";
            } else {
                return String.format("%d min %d sec", minutes, remainingSeconds);
            }
        } else {
            int hours = seconds / 3600;
            int remainingMinutes = (seconds % 3600) / 60;
            if (remainingMinutes == 0) {
                return hours + " h";
            } else {
                return String.format("%d h %d min", hours, remainingMinutes);
            }
        }
    }
    
    public static void runOnUIThread(Runnable action) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            action.run();
        } else {
            javafx.application.Platform.runLater(action);
        }
    }
    
    public static String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}