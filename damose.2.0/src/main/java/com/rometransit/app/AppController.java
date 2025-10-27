package com.rometransit.app;

import com.rometransit.service.network.NetworkManager;
import com.rometransit.service.user.UserService;
import com.rometransit.util.config.AppConfig;
import javafx.application.Platform;
import javafx.concurrent.Task;

public class AppController {
    private static AppController instance;
    
    private final AppConfig appConfig;
    private final NetworkManager networkManager;
    private final UserService userService;
    
    private AppController() {
        this.appConfig = AppConfig.getInstance();
        this.networkManager = NetworkManager.getInstance();
        this.userService = UserService.getInstance();
    }
    
    public static synchronized AppController getInstance() {
        if (instance == null) {
            instance = new AppController();
        }
        return instance;
    }
    
    public void initialize() {
        Task<Void> initTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                networkManager.initialize();
                userService.initialize();
                return null;
            }
        };
        
        initTask.setOnSucceeded(e -> Platform.runLater(() -> {
            // Inizializzazione completata
        }));
        
        new Thread(initTask).start();
    }
    
    public void shutdown() {
        networkManager.shutdown();
        userService.shutdown();
    }
    
    public AppConfig getAppConfig() {
        return appConfig;
    }
    
    public NetworkManager getNetworkManager() {
        return networkManager;
    }
    
    public UserService getUserService() {
        return userService;
    }
}