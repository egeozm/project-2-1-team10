package io.github.cantstop.model;

//4h
import io.github.cantstop.utensils.ConstantsBE;
import java.util.HashSet;
import java.util.Set;

public final class GameState {
    private Player toMove; // flag
    private final ColumnState[] columns = new ColumnState[ConstantsBE.NUM_COLS];
    private int tempRunnersCount = 0;  // 3 is max
    private final Set<Integer> activeTempCols = new HashSet<>(); //set of all temporal markers used at the moment
    private int redLocked = 0, blueLocked = 0; // score (0/3)
    private boolean bustPending = false;

    // for each colum we initialize ColumnState and then set who starts the game
    public static GameState initial(Player starting) {
        GameState s = new GameState();
        s.toMove = starting;
        for (int i = 0; i < s.columns.length; i++) {
            s.columns[i] = new ColumnState();
        }
        return s;
    }


    public void addActiveTempCol(int col) {
        activeTempCols.add(col);
        tempRunnersCount = activeTempCols.size(); }

    // set them null
    public void clearAllTemps() {
        activeTempCols.clear();
        tempRunnersCount = 0;
        for (var c: columns) c.clearTemp(); }


    // getters
    public Player toMove() {
        return toMove;
    }

    public ColumnState[] columns() {
        return columns;
    }

    public int tempRunnersCount() {
        return tempRunnersCount;
    }

    public Set<Integer> activeTempCols() {
        return Set.copyOf(activeTempCols);
    }

    public int lockedFor(Player p) {
        return p == Player.RED ? redLocked : blueLocked;
    }

    public boolean bustPending() {
        return bustPending;
    }


    // helpers used later in Rules
    public void setToMove(Player p) {
        this.toMove = p;
    }

    public void setBustPending(boolean v) {
        this.bustPending = v;
    }

    public void incLocked(Player p) {
        if (p == Player.RED) redLocked++;
        else blueLocked++;
    }

    public boolean isWin(Player p) {
        return lockedFor(p) >= ConstantsBE.TO_WIN;
    }


}
