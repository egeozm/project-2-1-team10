package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.*;

import java.util.List;
import java.util.Random;

/**
 * Wygodny kontroler: wywołujesz decideAndApply(state),
 * a on:
 *  - jeśli ROLL_OR_STOP: wybierze STOP lub ROLL; gdy ROLL, od razu obsłuży BUST/noBust i (przy noBust) wybierze MOVE
 *  - jeśli CHOOSE_MOVE: wybierze MOVE dla przekazanego lastRoll (musisz go podać)
 *
 * NIC nie zmienia w starym kodzie – tylko korzysta z TurnManager i MCTSPlayer.
 */
public final class MCTSController {

    public static final class ExecLog {
        public final MctsAction firstAction;   // STOP lub ROLL, albo MOVE (gdy start w CHOOSE_MOVE)
        public final DiceRoll roll;            // rzut kośćmi, jeśli był ROLL (inaczej null)
        public final Move chosenMove;          // wybrany move, jeśli był CHOOSE_MOVE (inaczej null)
        public final boolean bust;             // czy wystąpił bust po ROLL
        public final boolean terminal;         // czy po zastosowaniu akcji gra się skończyła

        ExecLog(MctsAction a, DiceRoll r, Move m, boolean bust, boolean terminal) {
            this.firstAction = a; this.roll = r; this.chosenMove = m; this.bust = bust; this.terminal = terminal;
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
        this.rng = (rng != null) ? rng : new Random();
    }

    /** Jeden krok gry: sam zajmie się STOP/ROLL oraz ewentualnym CHOOSE_MOVE. */
    public ExecLog decideAndApply(GameState state) {
        if (state.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            // 1) MCTS wybiera STOP / ROLL
            MctsAction a = mcts.decide(state);

            if (a.isStop()) {
                TurnManager.stop(state);
                boolean term = isTerminal(state);
                return new ExecLog(a, null, null, false, term);
            }

            // a == ROLL
            DiceRoll dr = TurnManager.roll(state, rng);
            List<Move> legal = TurnManager.getLegalMoves(state, dr);
            if (legal.isEmpty()) {
                TurnManager.bust(state);
                boolean term = isTerminal(state);
                return new ExecLog(a, dr, null, true, term);
            } else {
                TurnManager.noBust(state);
                // 2) MCTS wybiera MOVE w korzeniu CHOOSE_MOVE – używa nowego overload'u
                MctsAction moveAct = mcts.decide(state, dr);
                Move mv = (moveAct != null && moveAct.isMove()) ? moveAct.move : legal.get(0);
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

    /** Wariant: jesteś już w CHOOSE_MOVE i masz lastRoll. */
    public ExecLog decideAndApply(GameState state, DiceRoll lastRoll) {
        if (state.getTurnPhase() != TurnPhase.CHOOSE_MOVE) {
            throw new IllegalStateException("CHOOSE_MOVE overload called in phase: " + state.getTurnPhase());
        }
        MctsAction moveAct = mcts.decide(state, lastRoll);
        List<Move> legal = TurnManager.getLegalMoves(state, lastRoll);
        Move mv = (moveAct != null && moveAct.isMove()) ? moveAct.move : (legal.isEmpty() ? null : legal.get(0));
        if (mv == null) {
            // raczej nie powinno się zdarzyć – brak legalnych w CHOOSE_MOVE oznaczałby błąd w przepływie
            TurnManager.stop(state); // bezpieczny fallback
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
