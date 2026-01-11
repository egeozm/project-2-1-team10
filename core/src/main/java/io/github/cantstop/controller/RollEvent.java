package io.github.cantstop.controller;

import io.github.cantstop.model.DiceRoll;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.TurnManager;

public final class RollEvent implements Event {

    private final DiceRoll roll;
    private final boolean isBust;

    public RollEvent(DiceRoll roll, boolean isBust) {
        this.roll = roll;
        this.isBust = isBust;
    }

    public DiceRoll getRoll() {
        return roll;
    }

    public boolean isBust() {
        return isBust;
    }

    @Override
    public void apply(GameState state) {
        state.setLastRoll(roll);
        if (isBust) {
            TurnManager.bust(state);
        } else {
            TurnManager.noBust(state);
        }
    }
}

