package com.rometransit.model.entity;

import java.util.Objects;

public class Shape {
    private String shapeId;
    private double shapePtLat;
    private double shapePtLon;
    private int shapePtSequence;
    private double shapeDistTraveled;

    public Shape() {}

    public Shape(String shapeId, double shapePtLat, double shapePtLon, int shapePtSequence) {
        this.shapeId = shapeId;
        this.shapePtLat = shapePtLat;
        this.shapePtLon = shapePtLon;
        this.shapePtSequence = shapePtSequence;
    }

    public Shape(String shapeId, double shapePtLat, double shapePtLon, int shapePtSequence, double shapeDistTraveled) {
        this.shapeId = shapeId;
        this.shapePtLat = shapePtLat;
        this.shapePtLon = shapePtLon;
        this.shapePtSequence = shapePtSequence;
        this.shapeDistTraveled = shapeDistTraveled;
    }

    public String getShapeId() {
        return shapeId;
    }

    public void setShapeId(String shapeId) {
        this.shapeId = shapeId;
    }

    public double getShapePtLat() {
        return shapePtLat;
    }

    public void setShapePtLat(double shapePtLat) {
        this.shapePtLat = shapePtLat;
    }

    public double getShapePtLon() {
        return shapePtLon;
    }

    public void setShapePtLon(double shapePtLon) {
        this.shapePtLon = shapePtLon;
    }

    public int getShapePtSequence() {
        return shapePtSequence;
    }

    public void setShapePtSequence(int shapePtSequence) {
        this.shapePtSequence = shapePtSequence;
    }

    public double getShapeDistTraveled() {
        return shapeDistTraveled;
    }

    public void setShapeDistTraveled(double shapeDistTraveled) {
        this.shapeDistTraveled = shapeDistTraveled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shape shape = (Shape) o;
        return shapePtSequence == shape.shapePtSequence &&
                Objects.equals(shapeId, shape.shapeId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shapeId, shapePtSequence);
    }

    @Override
    public String toString() {
        return "Shape{" +
                "shapeId='" + shapeId + '\'' +
                ", shapePtLat=" + shapePtLat +
                ", shapePtLon=" + shapePtLon +
                ", shapePtSequence=" + shapePtSequence +
                ", shapeDistTraveled=" + shapeDistTraveled +
                '}';
    }
}