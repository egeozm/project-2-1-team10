package io.github.cantStop.utensils;

public final class ConstantsBE {
    private ConstantsBE() {}

    public static final int COL_MIN = 2;
    public static final int COL_MAX = 12;
    public static final int NUM_COLS = COL_MAX - COL_MIN + 1;

    public static final int MAX_TEMP_RUNNERS = 3;
    public static final int TO_WIN = 3;

    public static final int[] MAX_HEIGHT = new int[] {
        3,5,7,9,11,13,11,9,7,5,3
    };

    // sum - given combination ex. 1+1=2, 1+2=3 ...

    // changes sum of dices into the array(board) index
    public static int sumToColumnID(int sum) {
        if (sum < COL_MIN || sum > COL_MAX) throw new IllegalArgumentException("sum out of range");
        return sum - COL_MIN;
    }

    // changes array index into sum of dices (other way around)
    public static int colToSum(int col) {
        if (col < 0 || col >= NUM_COLS) throw new IllegalArgumentException("col out of range");
        return col + COL_MIN;
    }

    // height of a given column
    public static int maxHeight(int sum) {
        if (sum < COL_MIN || sum > COL_MAX) throw new IllegalArgumentException("sum out of range");
        return MAX_HEIGHT[sum - COL_MIN];
    }

}
