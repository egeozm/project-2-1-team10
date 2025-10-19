package io.github.cantstop.backend;

import java.util.Random;

/**
 * Roll of four dices (4 × d)
 * + methods for the three possible pairings (sum combinations)
 */
public final class DiceRoll {

    private final int[] dice = new int[4]; // stores values of 4 dice

    private DiceRoll(int d0, int d1, int d2, int d3) {
        // save rolled values
        dice[0] = d0;
        dice[1] = d1;
        dice[2] = d2;
        dice[3] = d3;
    }

    // rolls 4 dice (values 1–6) and returns a new DiceRoll
    public static DiceRoll roll(Random rng) {
        return new DiceRoll(
            1 + rng.nextInt(6),
            1 + rng.nextInt(6),
            1 + rng.nextInt(6),
            1 + rng.nextInt(6)
        );
    }

    // returns a copy of the raw dice values
    public int[] dice() {
        return dice.clone();
    }

    // returns the 3 possible pairings as sums: [[a,b], [c,d], [e,f]]
    public int[][] pairings() {
        int[][] p = new int[3][2];

        // pairing 0
        p[0][0] = dice[0] + dice[1];
        p[0][1] = dice[2] + dice[3];

        // pairing 1
        p[1][0] = dice[0] + dice[2];
        p[1][1] = dice[1] + dice[3];

        // pairing 2
        p[2][0] = dice[0] + dice[3];
        p[2][1] = dice[1] + dice[2];

        return p;
    }
}
