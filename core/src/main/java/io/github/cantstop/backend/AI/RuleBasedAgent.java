package io.github.cantstop.backend.AI;

import io.github.cantstop.backend.*;

import java.util.*;

public final class RuleBasedAgent {

    private final Random rng;


    public RuleBasedAgent(Random rng) {
        this.rng = Objects.requireNonNull(rng, "rng");
    }


    public Action chooseAction(GameState state, DiceRoll diceRoll) {
        DiceRoll roll = diceRoll != null ? diceRoll : DiceRoll.roll(rng);

        List<Move> legalMoves = TurnManager.getLegalMoves(state, roll);
        if (legalMoves.isEmpty()) {
            return StopAction.INSTANCE;
        }

        if (shouldStop(state, roll)) {
            return StopAction.INSTANCE;
        } else {
            Move move = chooseMove(state, roll);
            if (move == null && !legalMoves.isEmpty()) {
                move = legalMoves.get(0);
            }
            return new RollAction(move);
        }
    }

    // this is a placeholder example for when the bot should stop, and he should stop if he has 2 or more temporary
    // markers in the columns for the rolls 2-4 and/or 10-12. Because it's more unlikely to get those rolls, if he has 2
    // temporary markers on those then it's quite risky to try and roll
    private boolean shouldStop(GameState state, DiceRoll roll) {
        int count = 0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            if ((sum >= 2 && sum <= 4) || (sum >= 10 && sum <= 12)) {
                if (state.tempAtCol(col) > 0) {
                    count++;
                }
            }
        }

        return count >= 2;
    }

    // this is a rule placeholder example just to show how it works, the rule is that if the bot didn't stop with the
    // shouldStop method, then he should play 7, if he cant then 8 or 6 or both, and if he cant then 5 or 9 all
    // the way to 2 and 12. Pretty simple rule prioritising the most common dice pairings.
    private Move chooseMove(GameState state, DiceRoll roll) {
        List<Move> legal = TurnManager.getLegalMoves(state, roll);
        if (legal.isEmpty()) return null;

        Set<Integer> availableSums = new HashSet<>();
        int[][] pairings = roll.pairings();
        for (int i = 0; i < 3; i++) {
            availableSums.add(pairings[i][0]);
            availableSums.add(pairings[i][1]);
        }

        // made up priorities
        int[] priority1 = {7};
        int[] priority2 = {8, 6};
        int[] priority3 = {5, 9};
        int[] priority4= {4,10};
        int[] priority5= {3,11};
        int[] priority6= {2,12};

        int[][] priorities = {priority1, priority2, priority3, priority4, priority5, priority6};

        // pick first legal move that matches the priorities
        for (int[] group : priorities) {
            for (int sum : group) {
                if (!availableSums.contains(sum)) continue;

                for (Move m : legal) {
                    if (m.sumA() == sum || m.sumB() == sum) {
                        return m;
                    }
                }
            }
        }

        return legal.get(0);
    }


    public sealed interface Action permits StopAction, RollAction {
    }

    public enum StopAction implements Action {
        INSTANCE
    }

    public static final class RollAction implements Action {
        private final Move move;

        public RollAction(Move move) {
            this.move = Objects.requireNonNull(move);
        }

        public Move move() {
            return move;
        }

        @Override
        public String toString() {
            return "RollAction{" + move + "}";
        }
    }
}

