package io.github.cantstop.model;

import java.util.Random;

public class DiceRoll {
    private final int[] dice; // always 4 dice values

    public DiceRoll(int[] dice) {
        if (dice.length != 4) {
            throw new IllegalArgumentException("DiceRoll must have exactly 4 values");
        }
        this.dice = dice;
    }

    public static DiceRoll roll(Random rng) {
        int[] dice = new int[4];
        for (int i = 0; i < 4; i++) {
            dice[i] = rng.nextInt(6) + 1;
        }
        return new DiceRoll(dice);
    }

    /** Return the raw dice values (copy to avoid external mutation) */
    public int[] dice() {
        return dice.clone();
    }

    /**
     * Return one of the three possible pairings.
     * Each pairing is represented as 4 dice values (two pairs).
     *
     * pairingIndex = 0 → (d0+d1, d2+d3)
     * pairingIndex = 1 → (d0+d2, d1+d3)
     * pairingIndex = 2 → (d0+d3, d1+d2)
     */
    public int[] getPairing(int pairingIndex) {
        switch (pairingIndex) {
            case 0:
                return new int[]{dice[0], dice[1], dice[2], dice[3]};
            case 1:
                return new int[]{dice[0], dice[2], dice[1], dice[3]};
            case 2:
                return new int[]{dice[0], dice[3], dice[1], dice[2]};
            default:
                throw new IllegalArgumentException("Pairing index must be 0, 1, or 2");
        }
    }
}
