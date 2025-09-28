package io.github.cantstop.rules;

import io.github.cantstop.model.*;
import io.github.cantstop.utensils.ConstantsBE;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


// * Rules of cant stop

public final class Rules {

    private Rules() {
        // utility class
    }

        // LEGAL MOVES

    // Returns list of all possible moves
    public static List<Move> getLegalMoves(GameState s, DiceRoll r) {
        int[][] p = r.pairings();
        List<Move> moves = new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            int a = p[i][0];
            int b = p[i][1];
            if (canUsePairing(s, a, b)) {
                moves.add(Move.of(i, a, b));
            }
        }
        return moves;
    }

    // is it a bust? (no legal moves)
    public static boolean isBust(GameState s, DiceRoll r) {
        return getLegalMoves(s, r).isEmpty();
    }

    //APPLY MOVE

    // Increase temp markers, uptade game state, there is check for legality
    public static void applyMove(GameState s, Move m) {
        advanceOne(s, m.sumA());
        advanceOne(s, m.sumB());
    }

    // TURN (STOP/CONTINUE)

    public static void stop(GameState s) {
        Player player = s.toMove();

        for (int col = 0; col < ConstantsBE.NUM_COLS; col++) {
            int sum = ConstantsBE.colToSum(col);
            ColumnState cs = s.columns()[col];

            // temp to perm
            cs.commitTempToPerm(player, sum);

            // if reached top - lock and count++
            if (!cs.status().isLocked() && reachedTopFor(player, cs, sum)) {
                cs.lockFor(player, sum);
                s.incLocked(player);
            }
        }

        // clear temp markers and set move to opponent
        s.clearAllTemps();
        s.setToMove(player.opponent());
        s.setBustPending(false);
    }

    // nothing from the stop happens
    public static void continueTurn(GameState s) {
        // just a way to show that we keep rolling
    }

    // MOVES

    private static void advanceOne(GameState s, int sum) {
        int col = ConstantsBE.sumToColumnID(sum);
        ColumnState cs = s.columns()[col];

        // just in case, it should not happen for legal moves
        if (cs.status().isLocked()) return;

        cs.applyTempAdvance(s.toMove(), sum);
        s.addActiveTempCol(col);
    }

    // which pairngs can be played
    private static boolean canUsePairing(GameState s, int sumA, int sumB) {
        // no 2 locked colms at same time
        boolean aLocked = isColumnLocked(s, sumA);
        boolean bLocked = isColumnLocked(s, sumB);
        if (aLocked && bLocked) return false;

        // active < MAX_TEMP_RUNNERS (max 3 runners)
        Set<Integer> active = s.activeTempCols();
        int activeCount = active.size();
        int maxRunners = ConstantsBE.MAX_TEMP_RUNNERS;

        int colA = ConstantsBE.sumToColumnID(sumA);
        int colB = ConstantsBE.sumToColumnID(sumB);

        // same column
        if (colA == colB) {
            if (aLocked) return false; // one column is blocked so the other has to too
            boolean alreadyActive = active.contains(colA);
            return alreadyActive || (activeCount < maxRunners);
        }

        // two different colms
        // count is used to check whether we dont exceed the limit
        int newRunners = 0;
        if (!aLocked && !active.contains(colA)) newRunners++;
        if (!bLocked && !active.contains(colB)) newRunners++;


        boolean anyUsable = (!aLocked || !bLocked);

        return anyUsable && (activeCount + newRunners <= maxRunners);
    }

    // is the given column blocked
    private static boolean isColumnLocked(GameState s, int sum) {
        int col = ConstantsBE.sumToColumnID(sum);
        return s.columns()[col].status().isLocked();
    }

    // TURN

    // did the player reached top (just check count)
    private static boolean reachedTopFor(Player p, ColumnState cs, int sum) {
        int max = ConstantsBE.maxHeight(sum);
        return cs.permHeightFor(p) >= max;
    }
}
