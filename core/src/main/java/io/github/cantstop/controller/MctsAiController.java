package io.github.cantstop.controller;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;

import java.util.List;
import java.util.Objects;

public final class MctsAiController implements IPlayerController {
    private final MCTSPlayer mcts;

    public MctsAiController(MCTSPlayer mcts) {
        this.mcts = Objects.requireNonNull(mcts);
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        // bezpieczeństwo reguł: jak nie możesz stop -> zawsze roll
        if (state.countActiveColumns() == 0) return true;

        MctsAction a = mcts.decide(state);
        if (a == null) return true;       // fallback: roll
        return a.isRoll();                // true=ROLL, false=STOP
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) return null;

        DiceRoll lr = state.getLastRoll();
        if (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE && lr != null) {
            MctsAction a = mcts.decide(state, lr);
            if (a != null && a.isMove()) {
                // upewnij się że zwracany move jest legalny
                for (Move m : legalMoves) {
                    if (m.equals(a.move)) return m;
                }
            }
        }

        // fallback: pierwszy legalny
        return legalMoves.get(0);
    }
}
