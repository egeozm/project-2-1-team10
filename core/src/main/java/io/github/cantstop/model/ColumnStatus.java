package io.github.cantstop.model;

public enum ColumnStatus {
    OPEN,
    LOCKED_RED,
    LOCKED_BLUE;

    public boolean isLocked() {
        return this == LOCKED_RED || this == LOCKED_BLUE; }
}
