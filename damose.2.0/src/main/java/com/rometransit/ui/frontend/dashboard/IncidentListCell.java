package com.rometransit.ui.frontend.dashboard;

import com.rometransit.model.entity.TransportIncident;
import com.rometransit.util.language.LanguageManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

/**
 * Custom ListCell per visualizzare gli incident/alert del trasporto.
 * Mostra: Severity badge | Type | Location | Time | Affected Routes
 */
public class IncidentListCell extends ListCell<TransportIncident> {

    private final VBox container;
    private final HBox headerBox;
    private final Label severityBadge;
    private final Label typeLabel;
    private final Label locationLabel;
    private final Label timeLabel;
    private final Label routesLabel;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public IncidentListCell() {
        // Severity badge
        severityBadge = new Label();
        severityBadge.getStyleClass().add("severity-badge");
        severityBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white; " +
                "-fx-padding: 3px 10px; -fx-background-radius: 10px;");

        // Type label
        typeLabel = new Label();
        typeLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 14px; -fx-font-weight: bold;");

        // Header box (badge + type)
        headerBox = new HBox(10);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getChildren().addAll(severityBadge, typeLabel);

        // Location label
        locationLabel = new Label();
        locationLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 12px; -fx-text-fill: #555;");

        // Time label
        timeLabel = new Label();
        timeLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 12px; -fx-text-fill: #555;");

        // Routes label
        routesLabel = new Label();
        routesLabel.setStyle("-fx-font-family: 'Poppins'; -fx-font-size: 12px; -fx-text-fill: #555;");
        routesLabel.setWrapText(true);

        // Container VBox
        container = new VBox(5);
        container.setPadding(new Insets(8, 10, 8, 10));
        container.getChildren().addAll(headerBox, locationLabel, timeLabel, routesLabel);
    }

    @Override
    protected void updateItem(TransportIncident item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
        } else {
            // Update severity badge - mostra il tipo (RITARDO/INCIDENTE) invece della severità
            String badgeText = "RITARDO".equals(item.getType()) ? "RITARDO" : "INCIDENTE";
            severityBadge.setText(badgeText);

            // Rimuovi style classes precedenti
            severityBadge.getStyleClass().removeIf(style ->
                style.startsWith("severity-"));

            severityBadge.getStyleClass().add("severity-badge");
            severityBadge.getStyleClass().add("severity-" + item.getSeverity().name().toLowerCase());

            // Applica colore background al badge in base al TIPO (non alla severità)
            // RITARDO (arancione) vs INCIDENTE (rosso)
            String badgeColor;
            if ("RITARDO".equals(item.getType())) {
                // RITARDI: arancione con gradazione per severità
                switch (item.getSeverity()) {
                    case HIGH:
                        badgeColor = "#FF6F00"; // Arancione scuro
                        break;
                    case MEDIUM:
                        badgeColor = "#FF9800"; // Arancione medio
                        break;
                    case LOW:
                    default:
                        badgeColor = "#FFB74D"; // Arancione chiaro
                        break;
                }
            } else {
                // INCIDENTI: rosso con gradazione per severità
                switch (item.getSeverity()) {
                    case HIGH:
                        badgeColor = "#C62828"; // Rosso scuro
                        break;
                    case MEDIUM:
                        badgeColor = "#F44336"; // Rosso medio
                        break;
                    case LOW:
                    default:
                        badgeColor = "#E57373"; // Rosso chiaro
                        break;
                }
            }
            severityBadge.setStyle(severityBadge.getStyle() + "-fx-background-color: " + badgeColor + ";");

            // Update other labels
            typeLabel.setText(item.getType());
            locationLabel.setText("📍 " + item.getLocation());
            timeLabel.setText("🕐 " + item.getStartTime().format(TIME_FORMATTER));

            // Format affected routes
            String routesText = item.getAffectedRoutes().isEmpty()
                    ? "🚌 " + LanguageManager.getInstance().getString("incident.noRoute")
                    : "🚌 " + LanguageManager.getInstance().getString("incident.routesPrefix") + " " + String.join(", ", item.getAffectedRoutes());
            routesLabel.setText(routesText);

            setGraphic(container);
            setText(null);
        }
    }
}
