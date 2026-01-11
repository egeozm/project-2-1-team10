package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;

public interface Event {

    void apply(GameState state);
}
