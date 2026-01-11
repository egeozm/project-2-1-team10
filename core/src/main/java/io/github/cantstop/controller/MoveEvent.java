package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.TurnManager;

public final class MoveEvent implements Event {

    private final Move move;

    public MoveEvent(Move move) {
        this.move = move;
    }

    public Move getMove() {
        return move;
    }

    @Override
    public void apply(GameState state) {
        TurnManager.applyMove(state, move);
    }
}

