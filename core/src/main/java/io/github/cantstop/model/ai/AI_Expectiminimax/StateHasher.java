package io.github.cantstop.model.ai.AI_Expectiminimax;

import io.github.cantstop.model.GameConstants;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Player;

import java.util.Random;

final class StateHasher {
    private static final int COLS = GameConstants.NUM_COLS; // 11 for sums 2..12
    private static final int MAXH = 13;                     // safe upper bound
    private static final long[][] P1_HEIGHT = new long[COLS][MAXH];
    private static final long[][] P2_HEIGHT = new long[COLS][MAXH];
    private static final long[] P1_LOCK = new long[COLS];
    private static final long[] P2_LOCK = new long[COLS];
    private static final long[] P1_RUNNER = new long[COLS];
    private static final long[] P2_RUNNER = new long[COLS];
    private static final long SIDE_RED_TO_MOVE;

    static {
        Random r = new Random(0xC0FFEE_1234ABCDL);
        for (int c = 0; c < COLS; c++) {
            for (int h = 0; h < MAXH; h++) {
                P1_HEIGHT[c][h] = r.nextLong(); // RED
                P2_HEIGHT[c][h] = r.nextLong(); // BLUE
            }
            P1_LOCK[c] = r.nextLong();
            P2_LOCK[c] = r.nextLong();
            P1_RUNNER[c] = r.nextLong();
            P2_RUNNER[c] = r.nextLong();
        }
        SIDE_RED_TO_MOVE = r.nextLong();
    }

    static long hash(GameState s) {
        long h = 0L;

        // side to move: RED vs BLUE
        if (s.getCurrentPlayer() == Player.RED) h ^= SIDE_RED_TO_MOVE;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            // permanent heights
            int redH = clamp(s.redPermAtCol(col), 0, MAXH - 1);
            int blueH = clamp(s.bluePermAtCol(col), 0, MAXH - 1);
            h ^= P1_HEIGHT[col][redH];
            h ^= P2_HEIGHT[col][blueH];

            // locked?
            boolean locked = (redH == maxH) || (blueH == maxH);
            if (locked) {
                // mark which side locked it
                if (redH == maxH) h ^= P1_LOCK[col];
                if (blueH == maxH) h ^= P2_LOCK[col];
            }

            // runner occupancy (temp runner present -> belongs to current mover, but we encode both just in case)
            boolean temp = s.tempAtCol(col) != 0;
            if (temp) {
                // runners are for current player only in your rules; still encode for both for stability
                if (s.getCurrentPlayer() == Player.RED) h ^= P1_RUNNER[col];
                else h ^= P2_RUNNER[col];
            }
        }
        return h;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
