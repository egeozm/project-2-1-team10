package io.github.cantstop.controller;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;

import java.util.List;
import java.util.Objects;

public final class MctsControllerAdapter implements IPlayerController {

    private final MCTSPlayer mcts;

    public MctsControllerAdapter(MCTSPlayer mcts) {
        this.mcts = Objects.requireNonNull(mcts);
    }

    @Override
    public Boolean rollOrStop(GameState state) {

        MctsAction a = mcts.decide(state);
        if (a == null) return true; // default: roll
        return a.isRoll();
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) return null;

        DiceRoll lr = state.getLastRoll();

        MctsAction a = (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE && lr != null)
            ? mcts.decide(state, lr)
            : mcts.decide(state);

        if (a != null && a.isMove()) return a.move;

        return legalMoves.get(0);
    }
}
