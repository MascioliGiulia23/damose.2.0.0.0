package com.rometransit.ui.frontend.dashboard;

import com.rometransit.model.dto.dashboard.VehicleCrowdingData;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Custom ListCell per visualizzare i dati di affollamento dei veicoli.
 * Mostra: Route ID + Nome | ProgressBar colorata | Percentuale occupancy
 */
public class CrowdingListCell extends ListCell<VehicleCrowdingData> {

    private final HBox container;uuuuuuu
    private final Label routeLabel;
    private final ProgressBar progressBar;
    private final Label percentageLabel;
    private final Region spacer;

    public CrowdingListCell() {
        // Inizializza componenti
        routeLabel = new Label();
        routeLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 14px; -fx-font-weight: bold;");
        routeLabel.setMinWidth(250);
        routeLabel.setMaxWidth(250);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setPrefHeight(20);

        percentageLabel = new Label();
        percentageLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 13px;");
        percentageLabel.setMinWidth(120);
        percentageLabel.setAlignment(Pos.CENTER_RIGHT);

        spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Container HBox
        container = new HBox(15);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getChildren().addAll(routeLabel, progressBar, percentageLabel);
    }

    @Override
    protected void updateItem(VehicleCrowdingData item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            // Aggiorna labels
            routeLabel.setText(item.getDisplayText());
            percentageLabel.setText(item.getOccupancyText());

            // Aggiorna progress bar
            progressBar.setProgress(item.getPercentage() / 100.0);

            // Rimuovi tutte le style classes precedenti dalla progress bar
            progressBar.getStyleClass().removeIf(style ->
                style.equals("crowding-low") ||
                style.equals("crowding-medium") ||
                style.equals("crowding-high"));

            // Applica colore basato sul livello di affollamento
            String crowdingLevel = item.getCrowdingLevel().toLowerCase();
            progressBar.getStyleClass().add("crowding-" + crowdingLevel);

            setGraphic(container);
            setText(null);
        }
    }
}
