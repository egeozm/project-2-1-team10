package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.*;

import java.util.List;
import java.util.Random;

/*
 * Convenience controller for one decision step
 * Call decideAndApply on a GameState and it will
 *  - if the phase is ROLL_OR_STOP choose STOP or ROLL
 *    after ROLL it will handle BUST or noBust
 *    on noBust it will choose and apply a MOVE
 *  - if the phase is CHOOSE_MOVE use the provided last roll to choose and apply a MOVE
 *
 * This class does not change legacy code
 * It only calls TurnManager and MCTSPlayer
 */
public final class MCTSController {

    public static final class ExecLog {
        public final MctsAction firstAction;   // STOP or ROLL or MOVE when starting in CHOOSE_MOVE
        public final DiceRoll roll;            // non null only if a ROLL happened
        public final Move chosenMove;          // non null only if a MOVE was applied
        public final boolean bust;             // true if a bust occurred after ROLL
        public final boolean terminal;         // true if the game ended after the actions

        ExecLog(MctsAction a, DiceRoll r, Move m, boolean bust, boolean terminal) {
            this.firstAction = a;
            this.roll = r;
            this.chosenMove = m;
            this.bust = bust;
            this.terminal = terminal;
        }

        @Override public String toString() {
            return "ExecLog{" +
                "firstAction=" + firstAction +
                ", roll=" + roll +
                ", chosenMove=" + chosenMove +
                ", bust=" + bust +
                ", terminal=" + terminal +
                '}';
        }
    }

    private final MCTSPlayer mcts;
    private final Random rng;

    public MCTSController(MCTSPlayer mcts, Random rng) {
        this.mcts = mcts;
        if (rng != null) {
            this.rng = rng;
        } else {
            this.rng = new Random();
        }
    }

    /* One step of real play
     * Handles STOP or ROLL and the possible CHOOSE_MOVE that follows a noBust
     */
    public ExecLog decideAndApply(GameState state) {
        if (state.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {

            // 1 MCTS chooses STOP or ROLL
            MctsAction a = mcts.decide(state);

            if (a.isStop()) {
                TurnManager.stop(state);
                boolean term = isTerminal(state);
                return new ExecLog(a, null, null, false, term);
            }

            // a is ROLL
            DiceRoll dr = TurnManager.roll(state, rng);
            List<Move> legal = TurnManager.getLegalMoves(state, dr);
            if (legal.isEmpty()) {
                TurnManager.bust(state);
                boolean term = isTerminal(state);
                return new ExecLog(a, dr, null, true, term);
            } else {
                TurnManager.noBust(state);

                // 2 MCTS chooses MOVE at a CHOOSE_MOVE root using the overload
                MctsAction moveAct = mcts.decide(state, dr);

                Move mv;
                if (moveAct != null && moveAct.isMove()) {
                    mv = moveAct.move;
                } else {
                    mv = legal.get(0);
                }

                TurnManager.applyMove(state, mv);
                boolean term = isTerminal(state);
                return new ExecLog(a, dr, mv, false, term);
            }

        } else if (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
            throw new IllegalStateException("For CHOOSE_MOVE, call decideAndApply(state, lastRoll)");
        } else {
            throw new IllegalStateException("Unknown phase: " + state.getTurnPhase());
        }
    }

    /* Variant for when the phase is already CHOOSE_MOVE and the last roll is known */
    public ExecLog decideAndApply(GameState state, DiceRoll lastRoll) {
        if (state.getTurnPhase() != TurnPhase.CHOOSE_MOVE) {
            throw new IllegalStateException("CHOOSE_MOVE overload called in phase: " + state.getTurnPhase());
        }

        MctsAction moveAct = mcts.decide(state, lastRoll);
        List<Move> legal = TurnManager.getLegalMoves(state, lastRoll);

        Move mv;
        if (moveAct != null && moveAct.isMove()) {
            mv = moveAct.move;
        } else if (!legal.isEmpty()) {
            mv = legal.get(0);
        } else {
            mv = null;
        }

        if (mv == null) {
            // This should not normally happen
            // Having no legal moves in CHOOSE_MOVE would indicate a flow error
            TurnManager.stop(state);
            return new ExecLog(MctsAction.stop(), null, null, false, isTerminal(state));
        } else {
            TurnManager.applyMove(state, mv);
            return new ExecLog(MctsAction.move(mv), null, mv, false, isTerminal(state));
        }
    }

    private boolean isTerminal(GameState s) {
        return TurnManager.checkWinCondition(s, Player.RED)
            || TurnManager.checkWinCondition(s, Player.BLUE);
    }
}
