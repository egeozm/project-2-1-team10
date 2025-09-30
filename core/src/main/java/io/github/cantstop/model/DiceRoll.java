package io.github.cantstop.model;

import java.util.Random;
import java.util.Arrays;

public final class DiceRoll {
    private final int[] dice = new int[4];        // the four dice
    private final int[][] pairings = new int[3][2]; // the three possible pairings

    private DiceRoll(int d0, int d1, int d2, int d3) {
        dice[0] = d0;
        dice[1] = d1;
        dice[2] = d2;
        dice[3] = d3;

        // Precompute all 3 pairings
        pairings[0][0] = d0 + d1;
        pairings[0][1] = d2 + d3;
        pairings[1][0] = d0 + d2;
        pairings[1][1] = d1 + d3;
        pairings[2][0] = d0 + d3;
        pairings[2][1] = d1 + d2;
    }

    public static DiceRoll roll(Random rng) {
        return new DiceRoll(d6(rng), d6(rng), d6(rng), d6(rng));
    }

    private static int d6(Random r) {
        return 1 + r.nextInt(6);
    }

    /**
     * Returns a copy of the 4 dice values (never expose internal array).
     */
    public int[] dice() {
        return dice.clone();
    }

    /**
     * Returns a deep copy of the 3 pairings (never expose internals).
     */
    public int[][] pairings() {
        int[][] copy = new int[3][2];
        for (int i = 0; i < 3; i++) {
            copy[i][0] = pairings[i][0];
            copy[i][1] = pairings[i][1];
        }
        return copy;
    }

    /**
     * Return one specific pairing by index (0..2).
     */
    public int[] getPairing(int index) {
        if (index < 0 || index >= 3) {
            throw new IllegalArgumentException("Pairing index must be 0, 1, or 2");
        }
        return pairings[index].clone();
    }

    /**
     * Return a label like "Advance on 6 & 10".
     */
    public String pairingLabel(int index) {
        int[] p = getPairing(index);
        return "Advance on " + p[0] + " & " + p[1];
    }

    @Override
    public String toString() {
        return "Dice: " + Arrays.toString(dice) +
            " | Pairings: " + Arrays.deepToString(pairings);
    }
}
