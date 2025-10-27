package com.rometransit.ui.component;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

public class ToggleButton extends StackPane {

    private final BooleanProperty on = new SimpleBooleanProperty(false);

    public ToggleButton() {
        // background "pill"
        Rectangle bg = new Rectangle(44, 22);
        bg.setArcWidth(22);
        bg.setArcHeight(22);
        bg.fillProperty().bind(on
                .map(v -> v ? Color.web("#007DFF") : Color.web("#C8C8C8")));

        // knob
        Circle knob = new Circle(9);
        knob.setFill(Color.WHITE);
        knob.setStroke(Color.web("#323232"));

        // posizionamento knob
        knob.translateXProperty().bind(on.map(v -> v ? 22.0 : -.0));//v1 spostamento a destra v2 spostamento a sinistra
        knob.translateYProperty().bind(on.map(v -> -1.10));

        setPadding(new Insets(1));
        setMinSize(44, 22);
        setMaxSize(44, 22);
        setCursor(Cursor.HAND);
        setAlignment(Pos.BASELINE_LEFT);
        getChildren().addAll(bg, knob);

        // toggle on click
        setOnMouseClicked(e -> on.set(!on.get()));
    }

    public BooleanProperty onProperty() { return on; }
    public boolean isOn() { return on.get(); }
    public void setOn(boolean value) { on.set(value); }

}
