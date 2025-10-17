package io.github.cantstop.model;

import io.github.cantstop.utensils.ConstantsBE;
import java.util.Arrays;

/**
 * Single source of truth for game state (array-only):
 * permRed / permBlue / temp / statusArr.
 */
public final class GameState {

    private Player toMove;       // who moves now
    private boolean bustPending; // last roll was bust (no legal moves)

    // permanent heights per column (0..10)
    private final int[] permRed  = new int[ConstantsBE.NUM_COLS];
    private final int[] permBlue = new int[ConstantsBE.NUM_COLS];

    // temporary heights for current turn, TEMP_NONE = no runner
    private final int[] temp     = new int[ConstantsBE.NUM_COLS];

    // column status: 0=open, 1=locked by RED, 2=locked by BLUE
    private final int[] statusArr = new int[ConstantsBE.NUM_COLS];

    // how many columns each player has locked
    private int redLocked  = 0;
    private int blueLocked = 0;

    private GameState() {}

    // create initial state for a starting player
    public static GameState initial(Player start) {
        GameState s = new GameState();
        s.toMove = start;
        Arrays.fill(s.temp, ConstantsBE.TEMP_NONE);
        Arrays.fill(s.statusArr, ConstantsBE.STATUS_OPEN);
        return s;
    }

    // --- basic getters / setters ---

    public Player toMove() { return toMove; }

    public void setToMove(Player p) { this.toMove = p; }

    public boolean bustPending() { return bustPending; }

    public void setBustPending(boolean b) { this.bustPending = b; }

    public int lockedFor(Player p) {
        int value;
        if (p == Player.RED) {
            value = redLocked;
        } else {
            value = blueLocked;
        }
        return value;
    }

    public void incLocked(Player p) {
        if (p == Player.RED) {
            redLocked++;
        } else {
            blueLocked++;
        }
    }

    public boolean isWin(Player p) {
        return lockedFor(p) >= ConstantsBE.TO_WIN;
    }

    // --- logic-facing API (used by Rules) ---

    public boolean isLocked(int col) {
        return statusArr[col] != ConstantsBE.STATUS_OPEN;
    }

    public boolean isActive(int col) {
        return temp[col] != ConstantsBE.TEMP_NONE;
    }

    public int activeRunnersCount() {
        int c = 0;
        for (int t : temp) {
            if (t != ConstantsBE.TEMP_NONE) {
                c++;
            }
        }
        return c;
    }

    public int permHeight(Player p, int col) {
        int h;
        if (p == Player.RED) {
            h = permRed[col];
        } else {
            h = permBlue[col];
        }
        return h;
    }

    // raise a TEMP runner in the column determined by the sum
    public void addTemp(Player p, int sum) {
        int col = ConstantsBE.sumToColumnID(sum);
        if (isLocked(col)) {
            return;
        }

        int base = permHeight(p, col);
        int cur;
        if (temp[col] == ConstantsBE.TEMP_NONE) {
            cur = base;
        } else {
            cur = temp[col];
        }

        int max = ConstantsBE.maxHeight(sum);
        int next = cur + 1;
        if (next > max) {
            next = max;
        }
        temp[col] = next;
    }

    // commit all TEMP to PERM for the given player; then clear TEMP
    public void commitTemps(Player p) {
        for (int col = 0; col < ConstantsBE.NUM_COLS; col++) {
            if (temp[col] == ConstantsBE.TEMP_NONE) {
                continue;
            }

            if (p == Player.RED) {
                if (temp[col] > permRed[col]) {
                    permRed[col] = temp[col];
                }
            } else {
                if (temp[col] > permBlue[col]) {
                    permBlue[col] = temp[col];
                }
            }

            temp[col] = ConstantsBE.TEMP_NONE;
        }
    }

    // lock a column for the given player (only if not already locked)
    public void lockColumn(Player p, int col) {
        if (isLocked(col)) {
            return;
        }

        int newStatus;
        if (p == Player.RED) {
            newStatus = ConstantsBE.STATUS_LOCKED_RED;
        } else {
            newStatus = ConstantsBE.STATUS_LOCKED_BLUE;
        }
        statusArr[col] = newStatus;
    }

    // clear all TEMP runners (e.g., after bust or stop)
    public void clearTemps() {
        Arrays.fill(temp, ConstantsBE.TEMP_NONE);
    }

    // --- read-only "view" helpers (frontend / AI can read state via these) ---

    // by sum (2..12)
    public int redPermAtSum(int sum)  { return permRed[ConstantsBE.sumToColumnID(sum)]; }
    public int bluePermAtSum(int sum) { return permBlue[ConstantsBE.sumToColumnID(sum)]; }
    public int tempAtSum(int sum)     { return temp[ConstantsBE.sumToColumnID(sum)]; }      // TEMP_NONE = none
    public int statusAtSum(int sum)   { return statusArr[ConstantsBE.sumToColumnID(sum)]; } // 0/1/2

    // by column (0..10)
    public int redPermAtCol(int col)  { return permRed[col]; }
    public int bluePermAtCol(int col) { return permBlue[col]; }
    public int tempAtCol(int col)     { return temp[col]; }       // TEMP_NONE = none
    public int statusAtCol(int col)   { return statusArr[col]; }

    // copy the whole state (useful for AI / search)
    public GameState copy() {
        GameState g = new GameState();
        g.toMove = this.toMove;
        g.bustPending = this.bustPending;

        System.arraycopy(this.permRed,   0, g.permRed,   0, ConstantsBE.NUM_COLS);
        System.arraycopy(this.permBlue,  0, g.permBlue,  0, ConstantsBE.NUM_COLS);
        System.arraycopy(this.temp,      0, g.temp,      0, ConstantsBE.NUM_COLS);
        System.arraycopy(this.statusArr, 0, g.statusArr, 0, ConstantsBE.NUM_COLS);

        g.redLocked = this.redLocked;
        g.blueLocked = this.blueLocked;

        return g;
    }
}
