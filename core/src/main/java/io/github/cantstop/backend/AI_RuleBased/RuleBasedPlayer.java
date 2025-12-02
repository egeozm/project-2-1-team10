package io.github.cantstop.backend.AI_RuleBased;

import io.github.cantstop.backend.*;
import io.github.cantstop.backend.AI_Expectiminimax.BustTable;
import io.github.cantstop.backend.AI_Expectiminimax.ExpectiminimaxPlayer;

import java.util.*;

public final class RuleBasedPlayer {

    private final Random rng;
    private final float riskAversion; // higher value = more likely to stop

    public RuleBasedPlayer(Random rng, float riskAversion) {
        this.rng = Objects.requireNonNull(rng, "rng");
        this.riskAversion = riskAversion;
    }

    // stop criterion: if bust chance + progressValue > 1, stop.
    // progressValue = progress made this turn (proportional to entire board)
    // highest possible value is about 0.28 (if 3 columns have been entirely progressed through by temporary markers in just this turn)
    // riskAversion is a linear scalar for progressValue, so higher risk aversion means more likely to stop
    // 1 = extremely risky, good strategy is probably somewhere around 6 to 12
    public Action chooseAction(GameState state, DiceRoll diceRoll) {
        if (diceRoll != null) {
            // We already rolled: according to rules, we MUST play this roll (or bust),
            // we are NOT allowed to choose STOP here.

            List<Move> legal = TurnManager.getLegalMoves(state, diceRoll);
            if (legal.isEmpty()) {
                // No legal move → bust. The engine should handle bust when it sees there
                // are no moves for this roll; we just signal "end of turn".
                return StopAction.INSTANCE;
            }

            Move bestMove = chooseMove(state, diceRoll);
            return new RollAction(bestMove);
        } else {
            // No roll yet: decide between STOP and the expected value of ROLL.

            float bustChance = computeBustChance(state); // value between 0 and 1
            float progressValue = computeProgressValue(state);

            if (progressValue + bustChance > 1f) {
                return StopAction.INSTANCE;
            }

            // We chose to roll; now actually roll and pick the move for that real outcome
            DiceRoll realRoll = DiceRoll.roll(rng);
            List<Move> legal = TurnManager.getLegalMoves(state, realRoll);
            if (legal.isEmpty()) {
                // instant bust on real roll → end turn
                return StopAction.INSTANCE;
            }

            Move bestMove = chooseMove(state, realRoll);
            return new RollAction(bestMove);
        }
    }

    private float computeBustChance(GameState state) {

        int mask = allowedSumsMask(state);
        return (float) BustTable.P[mask];
    }

    // Build allowed-sums bitmask from the current state's legality of singles
    private int allowedSumsMask(GameState s) {
        int mask = 0;
        for (int sum = GameConstants.COL_MIN; sum <= GameConstants.COL_MAX; sum++) {
            if (TurnManager.isSinglePlayable(s, sum)) {
                mask |= (1 << (sum - 2));
            }
        }
        return mask;
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
    private Move chooseMove(GameState state, DiceRoll roll) {
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

