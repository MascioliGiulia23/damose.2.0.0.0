package com.rometransit.ui.waypoint;

import com.rometransit.model.entity.Route;
import com.rometransit.model.entity.Shape;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.List;

/**
 * Waypoint per disegnare linee rosse che seguono il percorso di una linea/autobus
 * La linea segue esattamente i punti GTFS shape della route
 */
public class RoutePathWaypoint extends Waypoint {
    private final Route route;
    private final List<Shape> shapePoints;
    private final String routeName;
    private final Color pathColor;
    private final double lineWidth;
    private final boolean showArrows;

    /**
     * Costruttore con colore rosso di default
     */
    public RoutePathWaypoint(String id, Route route, List<Shape> shapePoints, String routeName) {
        this(id, route, shapePoints, routeName, Color.web("#e74c3c"), 3.5, true); // Rosso di default
    }

    /**
     * Costruttore con colore personalizzato
     */
    public RoutePathWaypoint(String id, Route route, List<Shape> shapePoints, String routeName,
                            Color pathColor, double lineWidth, boolean showArrows) {
        super(id);
        this.route = route;
        this.shapePoints = shapePoints;
        this.routeName = routeName;
        this.pathColor = pathColor;
        this.lineWidth = lineWidth;
        this.showArrows = showArrows;
    }

    @Override
    public void render(GraphicsContext gc, int zoom, GeoToScreenConverter geoToScreenConverter) {
        if (!visible || shapePoints == null || shapePoints.isEmpty()) return;

        // Ordina i punti per sequenza (dovrebbero già essere ordinati)
        shapePoints.sort((a, b) -> Integer.compare(a.getShapePtSequence(), b.getShapePtSequence()));

        // Imposta stile della linea
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setLineJoin(StrokeLineJoin.ROUND);

        // Disegna prima un bordo bianco più spesso per contrasto
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(getAdaptiveLineWidth(zoom) + 3);
        drawPathLine(gc, geoToScreenConverter);

        // Disegna un bordo nero intermedio per definizione
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(getAdaptiveLineWidth(zoom) + 1.5);
        drawPathLine(gc, geoToScreenConverter);

        // Disegna la linea principale (rossa o colore personalizzato)
        gc.setStroke(pathColor);
        gc.setLineWidth(getAdaptiveLineWidth(zoom));
        drawPathLine(gc, geoToScreenConverter);

        // Disegna frecce direzionali ad alti livelli di zoom
        if (showArrows && zoom >= 15) {
            drawDirectionalArrows(gc, geoToScreenConverter, zoom);
        }

        // Disegna etichetta del percorso
        if (zoom >= 13 && routeName != null) {
            drawRouteLabel(gc, geoToScreenConverter);
        }
    }

    /**
     * Disegna la linea del percorso collegando tutti i punti shape
     */
    private void drawPathLine(GraphicsContext gc, GeoToScreenConverter geoToScreenConverter) {
        boolean firstPoint = true;
        double prevScreenX = 0;
        double prevScreenY = 0;

        for (Shape shapePoint : shapePoints) {
            double[] screenCoords = geoToScreenConverter.convert(
                shapePoint.getShapePtLat(),
                shapePoint.getShapePtLon()
            );
            double screenX = screenCoords[0];
            double screenY = screenCoords[1];

            if (firstPoint) {
                firstPoint = false;
            } else {
                // Disegna segmento di linea dal punto precedente al punto corrente
                gc.strokeLine(prevScreenX, prevScreenY, screenX, screenY);
            }

            prevScreenX = screenX;
            prevScreenY = screenY;
        }
    }

    /**
     * Disegna frecce direzionali lungo il percorso per indicare la direzione del viaggio
     */
    private void drawDirectionalArrows(GraphicsContext gc, GeoToScreenConverter geoToScreenConverter, int zoom) {
        if (shapePoints.size() < 2) return;

        // Intervallo tra le frecce (più frecce a zoom più alto)
        int arrowInterval = Math.max(5, 25 - zoom);

        gc.setFill(pathColor.darker());

        for (int i = arrowInterval; i < shapePoints.size(); i += arrowInterval) {
            Shape curr = shapePoints.get(i);
            Shape prev = shapePoints.get(i - 1);

            double[] currScreen = geoToScreenConverter.convert(curr.getShapePtLat(), curr.getShapePtLon());
            double[] prevScreen = geoToScreenConverter.convert(prev.getShapePtLat(), prev.getShapePtLon());

            double dx = currScreen[0] - prevScreen[0];
            double dy = currScreen[1] - prevScreen[1];

            // Calcola l'angolo della direzione
            double angle = Math.atan2(dy, dx);

            // Disegna la freccia
            double arrowSize = getArrowSize(zoom);
            double arrowX = currScreen[0];
            double arrowY = currScreen[1];

            // Punti della punta della freccia
            double angle1 = angle + Math.PI * 0.75;
            double angle2 = angle - Math.PI * 0.75;

            double x1 = arrowX + arrowSize * Math.cos(angle1);
            double y1 = arrowY + arrowSize * Math.sin(angle1);
            double x2 = arrowX + arrowSize * Math.cos(angle2);
            double y2 = arrowY + arrowSize * Math.sin(angle2);

            // Disegna triangolo per la freccia
            gc.fillPolygon(
                new double[]{arrowX, x1, x2},
                new double[]{arrowY, y1, y2},
                3
            );
        }
    }

    /**
     * Disegna l'etichetta del percorso (nome della linea) vicino all'inizio del percorso
     */
    private void drawRouteLabel(GraphicsContext gc, GeoToScreenConverter geoToScreenConverter) {
        if (shapePoints.isEmpty()) return;

        // Prendi un punto circa a 1/4 del percorso per l'etichetta
        int labelPointIndex = Math.min(shapePoints.size() / 4, shapePoints.size() - 1);
        Shape labelPoint = shapePoints.get(labelPointIndex);

        double[] screenCoords = geoToScreenConverter.convert(
            labelPoint.getShapePtLat(),
            labelPoint.getShapePtLon()
        );

        double textX = screenCoords[0] + 8;
        double textY = screenCoords[1] - 8;

        // Disegna sfondo per l'etichetta
        double textWidth = routeName.length() * 7;
        gc.setFill(Color.WHITE);
        gc.fillRoundRect(textX - 3, textY - 14, textWidth + 6, 18, 4, 4);

        gc.setStroke(pathColor);
        gc.setLineWidth(2);
        gc.strokeRoundRect(textX - 3, textY - 14, textWidth + 6, 18, 4, 4);

        // Disegna il testo
        gc.setFill(pathColor.darker());
        gc.setFont(javafx.scene.text.Font.font("Arial", javafx.scene.text.FontWeight.BOLD, 11));
        gc.fillText(routeName, textX, textY);
    }

    /**
     * Calcola la larghezza della linea in base al livello di zoom
     */
    private double getAdaptiveLineWidth(int zoom) {
        if (zoom >= 16) return lineWidth * 2.0;
        if (zoom >= 14) return lineWidth * 1.5;
        if (zoom >= 12) return lineWidth;
        return lineWidth * 0.7;
    }

    /**
     * Calcola la dimensione delle frecce in base al livello di zoom
     */
    private double getArrowSize(int zoom) {
        if (zoom >= 17) return 10;
        if (zoom >= 16) return 8;
        return 7;
    }

    public Route getRoute() {
        return route;
    }

    public List<Shape> getShapePoints() {
        return shapePoints;
    }

    public String getRouteName() {
        return routeName;
    }

    public Color getPathColor() {
        return pathColor;
    }

    public int getShapePointCount() {
        return shapePoints != null ? shapePoints.size() : 0;
    }
}
