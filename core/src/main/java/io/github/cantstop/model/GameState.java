package io.github.cantstop.model;

import java.util.Arrays;

//redMarkers / blueMarkers / tempRunners / lockedColumns

public final class GameState {

    // ------------------------------------------------------------------------
    //  Fields
    // ------------------------------------------------------------------------

    private Player currentPlayer; // who moves now
    private TurnPhase turnPhase; // where in the turn are we [0 = player can roll or stop, 1 = player can choose move]

    // marker and runner heights per column (0..10)
    private final int[] redMarkers = new int[GameConstants.NUM_COLS];
    private final int[] blueMarkers = new int[GameConstants.NUM_COLS];
    private final int[] tempRunners = new int[GameConstants.NUM_COLS];

    private DiceRoll lastRoll;

    // ------------------------------------------------------------------------
    //  Initialization
    // ------------------------------------------------------------------------

    private GameState() {}

    // create initial state for a starting player
    public static GameState initialize(Player startingPlayer) {
        GameState gs = new GameState();
        gs.currentPlayer = startingPlayer;
        gs.turnPhase = TurnPhase.ROLL_OR_STOP;
        return gs;
    }

    // ------------------------------------------------------------------------
    //  Basic getters and setters
    // ------------------------------------------------------------------------

    public Player getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(Player p) { this.currentPlayer = p; }

    public TurnPhase getTurnPhase() { return turnPhase; }
    public void setTurnPhase(TurnPhase phase) { this.turnPhase = phase; }

    public int getMarkerHeight(Player p, int col) {
        return (p == Player.RED) ? redMarkers[col] : blueMarkers[col];
    }

    public DiceRoll getLastRoll() { return lastRoll; }
    public void setLastRoll(DiceRoll diceRoll) { this.lastRoll = diceRoll; }

    // ------------------------------------------------------------------------
    //  Complex getters
    // ------------------------------------------------------------------------

    // Whether this column is locked (either player has reached max height)
    public boolean isColumnLocked(int col) {
        int max = GameConstants.maxHeight(GameConstants.columnToSum(col));
        return redMarkers[col] == max || blueMarkers[col] == max;
    }

    // Whether there’s a temp runner in this column
    public boolean isColumnActive(int col) {
        return tempRunners[col] != 0;
    }

    // How many temp runners are currently active
    public int countActiveColumns() {
        int c = 0;
        for (int t : tempRunners) if (t != 0) c++;
        return c;
    }

    // ------------------------------------------------------------------------
    //  Complex setters
    // ------------------------------------------------------------------------

    // raise a TEMP runner in the column corresponding to the dice sum
    public void moveTempRunner(Player p, int sum) {
        int col = GameConstants.sumToColumnID(sum);
        if (isColumnLocked(col)) return;

        int base = getMarkerHeight(p, col);
        int current = tempRunners[col] == 0 ? base : tempRunners[col];
        int next = Math.min(current + 1, GameConstants.maxHeight(sum));

        tempRunners[col] = next;
    }

    // commit all TEMP to PERM for the given player; then clear TEMP
    public void commitTempRunners(Player p) {
        int[] target = (p == Player.RED) ? redMarkers : blueMarkers;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            if (tempRunners[col] == 0) continue;
            target[col] = Math.max(target[col], tempRunners[col]);
            tempRunners[col] = 0;
        }
    }

    // clear all TEMP runners (after bust)
    public void clearTempRunners() {
        Arrays.fill(tempRunners, 0);
    }

    // ------------------------------------------------------------------------
    //  Data accessors for AI / analytics
    // ------------------------------------------------------------------------

    // I thought they might come in handy when we deal with AI
    // Björn: for the AI we should probably have getters that access the entire arrays/gamestate instead, but we will see

    // by sum (2..12)
    public int redPermAtSum(int sum)  { return redMarkers[GameConstants.sumToColumnID(sum)]; }
    public int bluePermAtSum(int sum) { return blueMarkers[GameConstants.sumToColumnID(sum)]; }
    public int tempAtSum(int sum)     { return tempRunners[GameConstants.sumToColumnID(sum)]; }      // TEMP_NONE = none

    // by column (0..10)
    public int redPermAtCol(int col)  { return redMarkers[col]; }
    public int bluePermAtCol(int col) { return blueMarkers[col]; }
    public int tempAtCol(int col)     { return tempRunners[col]; }       // TEMP_NONE = none

    // copy of the whole state!!!
    // copy of the whole state!!!
    public GameState copy() {
        GameState gs = new GameState();

        gs.currentPlayer = this.currentPlayer;
        gs.turnPhase = this.turnPhase;
        gs.lastRoll = this.lastRoll;

        System.arraycopy(this.redMarkers,  0, gs.redMarkers,  0, GameConstants.NUM_COLS);
        System.arraycopy(this.blueMarkers, 0, gs.blueMarkers, 0, GameConstants.NUM_COLS);
        System.arraycopy(this.tempRunners, 0, gs.tempRunners, 0, GameConstants.NUM_COLS);

        return gs;
    }

    public boolean wouldLockAnyColumnOnStop() {
        Player p = currentPlayer;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = tempRunners[col];
            if (temp == 0) continue;

            int sum = GameConstants.columnToSum(col);
            int max = GameConstants.maxHeight(sum);
            int perm = getMarkerHeight(p, col);

            if (Math.max(perm, temp) >= max && perm < max) return true;
        }
        return false;
    }

    public boolean wouldWinOnStop() {
        Player p = currentPlayer;
        int count = 0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int max = GameConstants.maxHeight(sum);

            int perm = getMarkerHeight(p, col);
            int temp = tempRunners[col];
            int finalH = (temp == 0) ? perm : Math.max(perm, temp);

            if (finalH >= max) {
                count++;
                if (count >= GameConstants.TO_WIN) return true;
            }
        }
        return false;
    }

}
