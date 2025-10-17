package io.github.cantstop.rules;

import io.github.cantstop.model.*;
import io.github.cantstop.utensils.ConstantsBE;

import java.util.ArrayList;
import java.util.List;

/**
 * Game logic – rules for valid moves, busts, column locks, etc.
 */
public final class Rules {

    private Rules() {} // no objects, static-only class

    // --- MAIN RULES ---

    // Find all legal pairings (moves) for the current roll and game state
    public static List<Move> getLegalMoves(GameState s, DiceRoll r) {
        int[][] p = r.pairings();          // all 3 possible pairings
        List<Move> moves = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            int a = p[i][0];
            int b = p[i][1];

            if (canUsePairing(s, a, b)) {
                moves.add(Move.of(i, a, b)); // add if legal
            }
        }
        return moves;
    }

    // True if there are no legal pairings for the current roll
    public static boolean isBust(GameState s, DiceRoll r) {
        return getLegalMoves(s, r).isEmpty();
    }

    // Apply chosen pairing (raise TEMP runners in the right columns)
    public static void applyMove(GameState s, Move m) {
        advanceOne(s, m.sumA());
        advanceOne(s, m.sumB());
    }

    // Stop: commit TEMP -> PERM, lock full columns, clear TEMP, switch player
    public static void stop(GameState s) {
        Player player = s.toMove();
        s.commitTemps(player);

        // check every column for possible lock
        for (int col = 0; col < ConstantsBE.NUM_COLS; col++) {
            int sum = ConstantsBE.colToSum(col);
            int max = ConstantsBE.maxHeight(sum);

            if (!s.isLocked(col) && s.permHeight(player, col) >= max) {
                s.lockColumn(player, col);
                s.incLocked(player);
            }
        }

        // cleanup and pass the turn
        s.clearTemps();
        s.setToMove(player.opponent());
        s.setBustPending(false);
    }

    // Continue turn: do nothing, player rolls again
    public static void continueTurn(GameState s) {
        // nothing to do here
    }

    // --- HELPERS ---

    // Raise one TEMP runner in a column if it's not locked
    private static void advanceOne(GameState s, int sum) {
        int col = ConstantsBE.sumToColumnID(sum);
        if (s.isLocked(col)) {
            return;
        }
        s.addTemp(s.toMove(), sum);
    }

    // Check if a given pairing (sumA, sumB) is legal under the current state
    // respects locked columns and the 3-runner limit
    private static boolean canUsePairing(GameState s, int sumA, int sumB) {
        int colA = ConstantsBE.sumToColumnID(sumA);
        int colB = ConstantsBE.sumToColumnID(sumB);

        boolean aLocked = s.isLocked(colA);
        boolean bLocked = s.isLocked(colB);

        // both locked -> illegal
        if (aLocked && bLocked) {
            return false;
        }

        int active = s.activeRunnersCount();
        int cap = ConstantsBE.MAX_TEMP_RUNNERS;

        // same column (double move)
        if (colA == colB) {
            if (aLocked) {
                return false;
            }
            if (s.isActive(colA) || active < cap) {
                return true;
            } else {
                return false;
            }
        }

        // different columns
        int newRunners = 0;
        if (!aLocked && !s.isActive(colA)) {
            newRunners++;
        }
        if (!bLocked && !s.isActive(colB)) {
            newRunners++;
        }

        if (active + newRunners <= cap) {
            return true;
        } else {
            return false;
        }
    }
}
