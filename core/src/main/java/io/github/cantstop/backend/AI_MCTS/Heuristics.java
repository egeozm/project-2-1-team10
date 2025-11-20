package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.GameConstants;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.Player;

import java.util.Random;

/*
 * Heuristic for MCTS rollouts in Can't Stop
 * This class reads public state and constants and never mutates the game
 *
 * Idea
 * We estimate how close the perspective player is to winning using several stable signals
 *  - Permanent progress on all columns compared to the opponent
 *  - Turn momentum from temporary runners counted only for the player who is on turn
 *  - Near wins where a column is one step from the top
 *  - Number of columns already locked
 *  - Tiny deterministic noise used to break ties
 *
 * Weights aligned with AIPlayer evaluation
 *  progressTerm  0.60 times difference in permanent progress
 *  tempoTerm     0.08 times difference in active temporary runners
 *  nearWinTerm   0.30 times difference in near wins
 *  lockTerm      0.10 times difference in locked columns
 *  noise         about 1e-6 deterministic seed for stable tie breaking
 *
 * The raw score can be used directly or mapped to the range 0..1 with normalize01
 */
public final class Heuristics {

    private Heuristics() {}

    // Weight of each component in the final score
    private static final double W_PROGRESS = 0.60;
    private static final double W_TEMPO    = 0.08;
    private static final double W_NEARWIN  = 0.30;
    private static final double W_LOCKS    = 0.10;

    // Very small deterministic noise for stable tie breaking in search
    private static final Random NOISE_RNG  = new Random(42);

    /*
     * Compute a scalar evaluation from the perspective of the given player
     * Larger values are better for the perspective player
     *
     * Important
     * Tempo from temporary runners is attributed to the player who is on turn
     * Only that player can benefit from them right away
     */
    public static double evaluate(GameState state, Player perspective) {
        final Player opp = perspective.opponent();
        final Player current = state.getCurrentPlayer();

        // 1 Permanent progress across all columns normalized by column height
        double myProgress = 0.0;
        double oppProgress = 0.0;

        // 2 Turn momentum number of active temporary runners for the player on turn
        int myActive = 0;
        int oppActive = 0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);

            int myPerm;
            if (perspective == Player.RED) {
                myPerm = state.redPermAtCol(col);
            } else {
                myPerm = state.bluePermAtCol(col);
            }

            int oppPerm;
            if (opp == Player.RED) {
                oppPerm = state.redPermAtCol(col);
            } else {
                oppPerm = state.bluePermAtCol(col);
            }

            myProgress  += ((double) myPerm)  / maxH;
            oppProgress += ((double) oppPerm) / maxH;

            final int temp = state.tempAtCol(col);
            // Temp runners always belong to the current player by rules
            // We count them for the side that is on turn
            if (current == perspective && temp > 0) myActive++;
            if (current == opp         && temp > 0) oppActive++;
        }

        final double progressTerm = W_PROGRESS * (myProgress - oppProgress);
        final double tempoTerm    = W_TEMPO    * (myActive   - oppActive);

        // 3 Near wins columns where the permanent marker is one step from the top
        final int nearWins     = nearWinCount(state, perspective);
        final int oppNearWins  = nearWinCount(state, opp);
        final double nearWinTerm = W_NEARWIN * (nearWins - oppNearWins);

        // 4 Already locked columns a strong secured advantage
        final int myLocks  = countLocks(state, perspective);
        final int oppLocks = countLocks(state, opp);
        final double lockTerm = W_LOCKS * (myLocks - oppLocks);

        // 5 Tiny deterministic noise to avoid identical ties in tree search
        final double noise = NOISE_RNG.nextDouble() * 1e-6;

        return progressTerm + tempoTerm + nearWinTerm + lockTerm + noise;
    }

    /*
     * Map an arbitrary score to the 0..1 range using a logistic curve
     * Handy when the heuristic acts like a pseudo reward at rollout cutoffs
     */
    public static double normalize01(double h) {
        final double alpha = 1.5; // tunable sharpness that can be tuned in arena matches
        return 1.0 / (1.0 + Math.exp(-alpha * h));
    }

    /*
     * Count columns where the player is exactly one permanent step from the top
     * These positions have high leverage and are usually worth pushing
     */
    public static int nearWinCount(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);
            final int h    = state.getMarkerHeight(p, col);
            if (h == maxH - 1) c++;
        }
        return c;
    }

    /*
     * Count columns that are already locked by the given player
     * A column is considered locked when the permanent marker reached the maximum height
     */
    public static int countLocks(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);
            if (state.getMarkerHeight(p, col) == maxH) c++;
        }
        return c;
    }
}
