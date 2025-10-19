package io.github.cantstop.backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Game logic:
 * rules for valid moves, busts, column locks, etc..
 */
public final class TurnManager {

    private TurnManager() {} // utility class

    // Find all legal pairings (moves) for the current roll and game state and save them in a list
    public static List<Move> getLegalMoves(GameState s, DiceRoll r) {
        int[][] p = r.pairings();          // all 3 possible pairings
        List<Move> moves = new ArrayList<>(3);

        for (int i = 0; i < 3; i++) {
            int a = p[i][0];
            int b = p[i][1];

            if (canUsePairing(s, a, b)) {
                moves.add(new Move(i, a, b)); // add if legal
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

    // Stop: commit TEMP to PERM, lock full columns, clear TEMP, switch player
    public static void stop(GameState s) {
        Player player = s.getCurrentPlayer();
        s.commitTemps(player);

        // check every column for possible lock
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int max = GameConstants.maxHeight(sum);

            if (!s.isLocked(col) && s.permHeight(player, col) >= max) {
                s.lockColumn(player, col);
                s.incLocked(player);
            }
        }

        // cleanup and pass the turn
        s.clearTemps();
        s.setCurrentPlayer(player.opponent());
    }

    // Continue turn: just do nothing, player rolls again
    public static void continueTurn(GameState s) {
        // just empty body, its more for readability
    }

    // Raise one TEMP runner in a column if it's not locked
    private static void advanceOne(GameState s, int sum) {
        int col = GameConstants.sumToColumnID(sum);
        if (s.isLocked(col)) {
            return;
        }
        s.addTemp(s.getCurrentPlayer(), sum);
    }

    // Check if a given pairing (sumA, sumB) is legal under the current state
    // respects locked columns and the 3-runners limit

    // TO DO: it currently only evaluates whether the entire pairing is legal or not
    // We also need to account for the case where only one move is legal
    // or both are legal but only 1 new runner can be added (so the player has to choose)
    private static boolean canUsePairing(GameState s, int sumA, int sumB) {
        int colA = GameConstants.sumToColumnID(sumA);
        int colB = GameConstants.sumToColumnID(sumB);

        if (s.isLocked(colA) && s.isLocked(colB)) return false;

        int active = s.activeRunnersCount();
        int newRunners = 0;

        if (!s.isLocked(colA) && !s.isActive(colA)) newRunners++;
        if (!s.isLocked(colB) && !s.isActive(colB) && colA != colB) newRunners++;

        return active + newRunners <= GameConstants.MAX_TEMP_RUNNERS;
    }
}
