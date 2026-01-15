// on my local device i also made files needed to select difficulty and change parameters of the ai
//instructions i recieved were not that clear so I won't commit them, in case someone else has a better idea in mind
//i will just leave this file commited for now to go off on the idea i was thinking, and we can iterate upon it


package io.github.cantstop.controller;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;

import java.util.List;
import java.util.Objects;

public final class MctsControllerAdapter implements IPlayerController {

    private final MCTSPlayer mcts;
    private final AiDifficulty difficulty;

    public MctsControllerAdapter(MCTSPlayer mcts) {
        this(mcts, AiDifficulty.NORMAL);
    }

    public MctsControllerAdapter(MCTSPlayer mcts, AiDifficulty difficulty) {
        this.mcts = Objects.requireNonNull(mcts);
        this.difficulty = Objects.requireNonNull(difficulty);
    }

    public AiDifficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        MctsAction a = mcts.decide(state);

        if (a == null) return true;

        if (!a.isRoll() && !a.isStop()) return true;

        return a.isRoll();
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) return null;

        DiceRoll lr = state.getLastRoll();

        MctsAction a = (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE && lr != null)
            ? mcts.decide(state, lr)
            : mcts.decide(state);

        if (a != null && a.isMove() && a.move != null && legalMoves.contains(a.move)) {
            return a.move;
        }

        return legalMoves.get(0);
    }
}

