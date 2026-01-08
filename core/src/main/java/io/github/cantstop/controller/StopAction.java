package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.TurnManager;

public final class StopAction implements Action {

    public StopAction() {}

    @Override
    public void apply(GameState state) {
        TurnManager.stop(state);
    }
}
