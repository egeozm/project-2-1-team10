package io.github.cantstop.model;

public enum Player {
    RED, BLUE;

    public Player opponent() {
        if (this == RED) {
            return BLUE;
        } else {
            return RED;
        }
    }

}

