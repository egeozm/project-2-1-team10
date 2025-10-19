package io.github.cantstop.backend;

import java.util.Arrays;

//redRunners / blueRunners / tempRunners / lockedColumns

public final class GameState {

    private Player currentPlayer; // who moves now
    private int turnState = 0; // where in the turn are we [0 = player can roll or stop, 1 = player can choose move]

    // permanent heights per column (0..10)
    private final int[] redRunners = new int[GameConstants.NUM_COLS];
    private final int[] blueRunners = new int[GameConstants.NUM_COLS];

    // temporary heights for current turn, TEMP_NONE = no runner
    private final int[] tempRunners = new int[GameConstants.NUM_COLS];

    // column status: 0=open, 1=locked by RED, 2=locked by BLUE
    private final int[] lockedColumns = new int[GameConstants.NUM_COLS];

    // how many columns each player has locked
    private int numberOfRedColumns = 0;
    private int numberOfBlueColumns = 0;

    public GameState(Player startingPlayer) {
        this.currentPlayer = startingPlayer;
    }

    // getters/setters

    public Player getCurrentPlayer() { return currentPlayer; }

    public void setCurrentPlayer(Player p) { this.currentPlayer = p; }

    public int lockedFor(Player p) {
        int value;
        if (p == Player.RED) {
            value = numberOfRedColumns;
        } else {
            value = numberOfBlueColumns;
        }
        return value;
    }

    // increase the locked count
    public void incLocked(Player p) {
        if (p == Player.RED) {
            numberOfRedColumns++;
        } else {
            numberOfBlueColumns++;
        }
    }

    public boolean isWin(Player p) {
        return lockedFor(p) >= GameConstants.TO_WIN;
    }

    // --- logic-facing API (used by Rules) ---

    public boolean isLocked(int col) {
        return lockedColumns[col] != GameConstants.STATUS_OPEN;
    }

    public boolean isActive(int col) {
        return tempRunners[col] != GameConstants.TEMP_NONE;
    }

    public int activeRunnersCount() {
        int c = 0;
        for (int t : tempRunners) {
            if (t != GameConstants.TEMP_NONE) {
                c++;
            }
        }
        return c;
    }

    public int permHeight(Player p, int col) {
        int h;
        if (p == Player.RED) {
            h = redRunners[col];
        } else {
            h = blueRunners[col];
        }
        return h;
    }

    // raise a TEMP runner in the column determined by the sum
    public void addTemp(Player p, int sum) {
        int col = GameConstants.sumToColumnID(sum);
        if (isLocked(col)) {
            return;
        }

        int base = permHeight(p, col);
        int cur;
        if (tempRunners[col] == GameConstants.TEMP_NONE) {
            cur = base;
        } else {
            cur = tempRunners[col];
        }

        int max = GameConstants.maxHeight(sum);
        int next = cur + 1;
        if (next > max) {
            next = max;
        }
        tempRunners[col] = next;
    }

    // commit all TEMP to PERM for the given player; then clear TEMP
    public void commitTemps(Player p) {
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            if (tempRunners[col] == GameConstants.TEMP_NONE) {
                continue;
            }

            if (p == Player.RED) {
                if (tempRunners[col] > redRunners[col]) {
                    redRunners[col] = tempRunners[col];
                }
            } else {
                if (tempRunners[col] > blueRunners[col]) {
                    blueRunners[col] = tempRunners[col];
                }
            }

            tempRunners[col] = GameConstants.TEMP_NONE;
        }
    }

    // lock a column for the given player (only if not already locked)
    public void lockColumn(Player p, int col) {
        if (isLocked(col)) {
            return;
        }

        int newStatus;
        if (p == Player.RED) {
            newStatus = GameConstants.STATUS_LOCKED_RED;
        } else {
            newStatus = GameConstants.STATUS_LOCKED_BLUE;
        }
        lockedColumns[col] = newStatus;
    }

    // clear all TEMP runners (e.g., after bust or stop)
    public void clearTemps() {
        Arrays.fill(tempRunners, GameConstants.TEMP_NONE);
    }

    // I thought they might come in handy when we deal with AI

    // by sum (2..12)
    public int redPermAtSum(int sum)  { return redRunners[GameConstants.sumToColumnID(sum)]; }
    public int bluePermAtSum(int sum) { return blueRunners[GameConstants.sumToColumnID(sum)]; }
    public int tempAtSum(int sum)     { return tempRunners[GameConstants.sumToColumnID(sum)]; }      // TEMP_NONE = none
    public int statusAtSum(int sum)   { return lockedColumns[GameConstants.sumToColumnID(sum)]; } // 0/1/2

    // by column (0..10)
    public int redPermAtCol(int col)  { return redRunners[col]; }
    public int bluePermAtCol(int col) { return blueRunners[col]; }
    public int tempAtCol(int col)     { return tempRunners[col]; }       // TEMP_NONE = none
    public int statusAtCol(int col)   { return lockedColumns[col]; }

    // copy of the whole state!!!
    public GameState copy() {
        GameState gs = new GameState(this.currentPlayer);

        System.arraycopy(this.redRunners,   0, gs.redRunners,   0, GameConstants.NUM_COLS);
        System.arraycopy(this.blueRunners,  0, gs.blueRunners,  0, GameConstants.NUM_COLS);
        System.arraycopy(this.tempRunners,      0, gs.tempRunners,      0, GameConstants.NUM_COLS);
        System.arraycopy(this.lockedColumns, 0, gs.lockedColumns, 0, GameConstants.NUM_COLS);

        gs.numberOfRedColumns = this.numberOfRedColumns;
        gs.numberOfBlueColumns = this.numberOfBlueColumns;

        return gs;
    }
}
