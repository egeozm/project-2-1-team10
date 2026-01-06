package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;

import java.util.List;

public class HumanController implements IPlayerController {

    private Boolean rollOrStopDecision = null;
    private Move selectedMove = null;

    public HumanController() {}

    // ------------------------------------------------------------------------
    //  actions set by player via PlayScreen
    // ------------------------------------------------------------------------

    public void chooseRoll() {
        rollOrStopDecision = true;
    }

    public void chooseStop() {
        rollOrStopDecision = false;
    }

    public void chooseMove(Move move) {
        selectedMove = move;
    }

    // ------------------------------------------------------------------------
    //  actions polled by GameController
    // ------------------------------------------------------------------------

    @Override
    public Boolean rollOrStop(GameState state) {
        Boolean decision = rollOrStopDecision;
        rollOrStopDecision = null;
        return decision;
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        Move move = selectedMove;
        selectedMove = null;
        return move;
    }
}
