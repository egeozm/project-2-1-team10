package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.TurnManager;

public final class MoveAction implements Action {

    private final Move move;

    public MoveAction(Move move) {
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

