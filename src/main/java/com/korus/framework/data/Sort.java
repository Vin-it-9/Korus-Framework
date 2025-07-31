package com.korus.framework.data;

public class Sort {
    public enum Direction { ASC, DESC }

    private String property;
    private Direction direction;

    public Sort(String property, Direction direction) {
        this.property = property;
        this.direction = direction;
    }

    public String getProperty() { return property; }
    public Direction getDirection() { return direction; }
}
