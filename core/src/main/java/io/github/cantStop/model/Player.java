package io.github.cantStop.model;

public enum Player {
    RED, BLUE;

    public Player opponent() { return this == RED ? BLUE : RED; }
}
