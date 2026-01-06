package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;

public sealed interface Action permits RollAction, StopAction, MoveAction, WaitForInputAction {

    void apply(GameState state);
}
