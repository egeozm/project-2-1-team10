package io.github.cantstop.backend;

public final class GameConstants {

    // Dice sum range (2...12), 11 columns on the board
    public static final int COL_MIN = 2;
    public static final int COL_MAX = 12;
    public static final int NUM_COLS = COL_MAX - COL_MIN + 1; // 11 total

    // Max number of active temporary runners in one turn
    public static final int MAX_TEMP_RUNNERS = 3;

    // how many to win
    public static final int TO_WIN = 3;

    // Max column heights for sums 2..12 (index = sum - 2)
    private static final int[] MAX_COLUMN_HEIGHTS = {
        3, 5, 7, 9, 11, 13, 11, 9, 7, 5, 3
    };

    // dice sum into column
    public static int sumToColumnID(int sum) {
        if (sum < COL_MIN || sum > COL_MAX) {
            throw new IllegalArgumentException("sum out of range: " + sum);
        }
        return sum - COL_MIN;
    }

    // column into dice sum
    public static int columnToSum(int col) {
        if (col < 0 || col >= NUM_COLS) {
            throw new IllegalArgumentException("col out of range: " + col);
        }
        return col + COL_MIN;
    }

    // max height for a given dice sum (2..12)
    public static int maxHeight(int sum) {
        return MAX_COLUMN_HEIGHTS[sumToColumnID(sum)];
    }

    // check if a sum is valid
    public static boolean isValidSum(int sum) {
        return sum >= COL_MIN && sum <= COL_MAX;
    }

    // check if a column index is valid
    public static boolean isValidCol(int col) {
        return col >= 0 && col < NUM_COLS;
    }
}
