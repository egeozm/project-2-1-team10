package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Player;
import io.github.cantstop.model.TurnManager;

public class GameOverEvent implements Event{

    private Player winner;

    public GameOverEvent(Player winner) {
        this.winner = winner;
    }

    public Player getWinner() {return this.winner;}

    @Override
    public void apply(GameState state) {}
}
