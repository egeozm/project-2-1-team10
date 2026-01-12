package io.github.cantstop.model.ai;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_Expectiminimax.BustTable;

import java.util.*;

public final class RuleBasedPlayer implements IPlayerController {

    private final Random rng;
    private final float progressWeight; // higher value = more likely to stop
    private final float bustChanceWeight;  // higher value = more likely to stop

    public RuleBasedPlayer(Random rng, float progressWeight, float bustChanceWeight) {
        this.rng = Objects.requireNonNull(rng, "rng");
        this.progressWeight = progressWeight;
        this.bustChanceWeight = bustChanceWeight;
    }

    public RuleBasedPlayer(Random rng, float progressWeight) {
        this(rng, progressWeight, 1.0f);
    }

    // stop criterion: if bust chance + progressValue > 1, stop.
    // progressValue = progress made this turn (proportional to entire board)
    // highest possible value is about 0.28 (if 3 columns have been entirely progressed through by temporary markers in just this turn)
    // riskAversion is a linear scalar for progressValue, so higher risk aversion means more likely to stop
    // 1 = extremely risky, good strategy is probably somewhere around 6 to 12
    @Override
    public Boolean rollOrStop(GameState state) { // true = roll, false = stop

        float bustChance = computeBustChance(state); // value between 0 and 1
        float progressValue = computeProgressValue(state);
        //System.out.print("bustChance: " + bustChance * bustChanceWeight);
        //System.out.print(", progressValue: " + progressValue * progressWeight);

        if (progressValue * progressWeight + bustChance * bustChanceWeight > 1f) { // stop if rolling is not worth the risk
            //System.out.println(", stop");
            return false;
        } else {
            //System.out.println(", roll");
            return true;
        }
    }

    // this is a rule placeholder example just to show how it works, the rule is that if the bot didn't stop with the
    // shouldStop method, then he should play 7, if he cant then 8 or 6 or both, and if he cant then 5 or 9 all
    // the way to 2 and 12. Pretty simple rule prioritising the most common dice pairings.
    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {

//        Set<Integer> availableSums = new HashSet<>();
//        int[][] pairings = roll.pairings();
//        for (int i = 0; i < 3; i++) {
//            availableSums.add(pairings[i][0]);
//            availableSums.add(pairings[i][1]);
//        }
//
//        // made up priorities
//        int[] priority1 = {7};
//        int[] priority2 = {8, 6};
//        int[] priority3 = {5, 9};
//        int[] priority4 = {4,10};
//        int[] priority5 = {3,11};
//        int[] priority6 = {2,12};
//
//        int[][] priorities = {priority1, priority2, priority3, priority4, priority5, priority6};
//
//        // pick first legal move that matches the priorities
//        for (int[] group : priorities) {
//            for (int sum : group) {
//                if (!availableSums.contains(sum)) continue;
//
//                for (Move m : legalMoves) {
//                    if (m.sumA() == sum || m.sumB() == sum) {
//                        return m;
//                    }
//                }
//            }
//        }

        return legalMoves.get(0);
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
            progressValue += (Math.max(state.tempAtCol(i) - state.getMarkerHeight(state.getCurrentPlayer(), i), 0)) / (float) GameConstants.maxHeight(GameConstants.columnToSum(i));
        }

        return progressValue / GameConstants.NUM_COLS;
    }
}

