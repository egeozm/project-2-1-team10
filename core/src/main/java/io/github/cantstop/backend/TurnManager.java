package io.github.cantstop.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Game logic:
 * rules for valid moves, busts, column locks, etc..
 */
public final class TurnManager {

    private TurnManager() {} // utility class

    // ------------------------------------------------------------------------
    //  Phase 1: Stop or Roll
    // ------------------------------------------------------------------------

    // Stop: commit TEMP to PERM, lock full columns, clear TEMP, switch player
    public static void stop(GameState s) {
        Player player = s.getCurrentPlayer();
        s.commitTempRunners(player);
        s.setCurrentPlayer(player.opponent());
        s.setTurnPhase(TurnPhase.ROLL_OR_STOP);
    }

    // Roll
    public static DiceRoll roll(GameState s, Random rng) {
        return DiceRoll.roll(rng);
    }

    // ------------------------------------------------------------------------
    //  Check Possible Moves
    // ------------------------------------------------------------------------

    // Björn: these methods all happen in direct consequence of each other
    // but I kept them separate to give the GUI more control
    // but there is probably a simpler way to do this

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


    public static void bust(GameState s) {
        Player player = s.getCurrentPlayer();
        s.clearTempRunners();
        s.setCurrentPlayer(player.opponent());
        s.setTurnPhase(TurnPhase.ROLL_OR_STOP);
    }

    public static void noBust(GameState s) {
        s.setTurnPhase(TurnPhase.CHOOSE_MOVE);
    }

    // ------------------------------------------------------------------------
    //  Phase 2: Apply Chosen Move
    // ------------------------------------------------------------------------

    // Apply chosen pairing (raise TEMP runners in the right columns)
    public static void applyMove(GameState s, Move m) {
        advanceOne(s, m.sumA());
        advanceOne(s, m.sumB());
        s.setTurnPhase(TurnPhase.ROLL_OR_STOP);
    }

    // Raise one TEMP runner in a column if it's not locked
    private static void advanceOne(GameState s, int sum) {
        int col = GameConstants.sumToColumnID(sum);
        if (s.isColumnLocked(col)) {
            return;
        }
        s.moveTempRunner(s.getCurrentPlayer(), sum);
    }

    // ------------------------------------------------------------------------
    //  Win condition check
    // ------------------------------------------------------------------------

    // Returns true if the given player has met the win condition
    public static boolean checkWinCondition(GameState s, Player p) {
        int count = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int maxHeight = GameConstants.maxHeight(GameConstants.columnToSum(col));

            if (p == Player.RED && s.getMarkerHeight(Player.RED, col) == maxHeight) count++;
            if (p == Player.BLUE && s.getMarkerHeight(Player.BLUE, col) == maxHeight) count++;

            if (count >= GameConstants.TO_WIN) return true; // early exit
        }
        return false;
    }

    // Check if a given pairing (sumA, sumB) is legal under the current state
    // respects locked columns and the 3-runners limit

    // TO DO: it currently only evaluates whether the entire pairing is legal or not
    // We also need to account for the case where only one move is legal
    // or both are legal but only 1 new runner can be added (so the player has to choose)
    private static boolean canUsePairing(GameState s, int sumA, int sumB) {
        int colA = GameConstants.sumToColumnID(sumA);
        int colB = GameConstants.sumToColumnID(sumB);

        if (s.isColumnLocked(colA) && s.isColumnLocked(colB)) return false;

        int active = s.countActiveColumns();
        int newRunners = 0;

        if (!s.isColumnLocked(colA) && !s.isColumnActive(colA)) newRunners++;
        if (!s.isColumnLocked(colB) && !s.isColumnActive(colB) && colA != colB) newRunners++;

        return active + newRunners <= GameConstants.MAX_TEMP_RUNNERS;
    }
}
