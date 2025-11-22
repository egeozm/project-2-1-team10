package io.github.cantstop.backend.AI;

import io.github.cantstop.backend.*;

import java.util.*;

public final class RuleBasedAgent {

    private final Random rng;
    private final float riskAversion; // higher value = more likely to stop

    public RuleBasedAgent(Random rng, float riskAversion) {
        this.rng = Objects.requireNonNull(rng, "rng");
        this.riskAversion = riskAversion;
    }

    // stop criterion: if bust chance + progressValue > 1, stop.
    // progressValue = progress made this turn (proportional to entire board)
    // highest possible value is about 0.28 (if 3 columns have been entirely progressed through by temporary markers in just this turn)
    // riskAversion is a linear scalar for progressValue, so higher risk aversion means more likely to stop
    // 1 = extremely risky, good strategy is probably somewhere around 6 to 12
    public Action rollOrStop(GameState state) {

        float bustChance = computeBustChance(state); // value between 0 and 1
        float progressValue = computeProgressValue(state);

        if (progressValue + bustChance > 1f) {
            return StopAction.INSTANCE;
        } else {
            return RollAction;
        }
    }

    private float computeBustChance(GameState state) {

    }

    private float computeProgressValue(GameState state) {

        float progressValue = 0f;

        for (int i = 0; i < GameConstants.NUM_COLS; i++) {
            progressValue += (state.tempAtCol(i) - state.getMarkerHeight(state.getCurrentPlayer(), i)) / (float) GameConstants.maxHeight(GameConstants.columnToSum(i));
        }

        return progressValue / GameConstants.NUM_COLS * riskAversion;
    }

    // this is a rule placeholder example just to show how it works, the rule is that if the bot didn't stop with the
    // shouldStop method, then he should play 7, if he cant then 8 or 6 or both, and if he cant then 5 or 9 all
    // the way to 2 and 12. Pretty simple rule prioritising the most common dice pairings.
    private Move chooseMove(GameState state) {
        DiceRoll roll = state.getLastRoll();
        List<Move> legalMoves = TurnManager.getLegalMoves(state, roll);

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
        int[] priority4 = {4,10};
        int[] priority5 = {3,11};
        int[] priority6 = {2,12};

        int[][] priorities = {priority1, priority2, priority3, priority4, priority5, priority6};

        // pick first legal move that matches the priorities
        for (int[] group : priorities) {
            for (int sum : group) {
                if (!availableSums.contains(sum)) continue;

                for (Move m : legalMoves) {
                    if (m.sumA() == sum || m.sumB() == sum) {
                        return m;
                    }
                }
            }
        }

        return legalMoves.get(0);
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

