package com.rometransit.ui.component;

import com.rometransit.model.entity.Route;
import com.rometransit.util.logging.Logger;
import com.rometransit.util.language.LanguageManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.function.Consumer;

/**
 * Dialog for selecting route direction (andata/ritorno)
 */
public class DirectionSelector {

    private Stage dialog;
    private Route route;
    private String direction0Name;
    private String direction1Name;
    private Consumer<Integer> onDirectionSelected;

    public DirectionSelector(Stage owner) {
        // Initialize direction names with translations
        direction0Name = LanguageManager.getInstance().getString("direction.direction0");
        direction1Name = LanguageManager.getInstance().getString("direction.direction1");

        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(LanguageManager.getInstance().getString("direction.title"));
        dialog.setResizable(false);
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public void setDirectionNames(String direction0, String direction1) {
        this.direction0Name = direction0;
        this.direction1Name = direction1;
    }

    public void setOnDirectionSelected(Consumer<Integer> callback) {
        this.onDirectionSelected = callback;
    }

    public void show() {
        VBox layout = new VBox(15);
        layout.setPadding(new Insets(20));
        layout.setAlignment(Pos.CENTER);
        layout.setStyle("-fx-background-color: white;");

        // Title label
        Label titleLabel = new Label(LanguageManager.getInstance().getString("direction.selectDirection"));
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        // Route info label
        Label routeLabel = new Label(route != null ?
            route.getRouteShortName() + " - " + route.getRouteLongName() :
            LanguageManager.getInstance().getString("direction.unknownRoute"));
        routeLabel.setStyle("-fx-font-size: 12px;");

        // Direction 0 button
        Button dir0Button = new Button(direction0Name);
        dir0Button.setPrefWidth(300);
        dir0Button.setStyle("-fx-font-size: 13px; -fx-padding: 10px; " +
                "-fx-background-color: #3498db; -fx-text-fill: white; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");
        dir0Button.setOnAction(e -> {
            if (onDirectionSelected != null) {
                onDirectionSelected.accept(0);
            }
            Logger.log("Direction 0 selected: " + direction0Name);
            dialog.close();
        });

        // Direction 1 button
        Button dir1Button = new Button(direction1Name);
        dir1Button.setPrefWidth(300);
        dir1Button.setStyle("-fx-font-size: 13px; -fx-padding: 10px; " +
                "-fx-background-color: #2ecc71; -fx-text-fill: white; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");
        dir1Button.setOnAction(e -> {
            if (onDirectionSelected != null) {
                onDirectionSelected.accept(1);
            }
            Logger.log("Direction 1 selected: " + direction1Name);
            dialog.close();
        });

        // Cancel button
        Button cancelButton = new Button(LanguageManager.getInstance().getString("direction.cancel"));
        cancelButton.setPrefWidth(300);
        cancelButton.setStyle("-fx-font-size: 12px; -fx-padding: 8px; " +
                "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; " +
                "-fx-border-radius: 5px; -fx-background-radius: 5px;");
        cancelButton.setOnAction(e -> {
            Logger.log("Direction selection cancelled");
            dialog.close();
        });

        layout.getChildren().addAll(titleLabel, routeLabel, dir0Button, dir1Button, cancelButton);

        Scene scene = new Scene(layout, 350, 280);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}
