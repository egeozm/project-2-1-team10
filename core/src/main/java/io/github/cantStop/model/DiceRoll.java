package io.github.cantStop.model;

import java.util.Random;

public final class DiceRoll {
    private final int[] dice = new int[4];

    private DiceRoll(int d0, int d1, int d2, int d3) {
        dice[0]=d0; dice[1]=d1; dice[2]=d2; dice[3]=d3;
    }

    public static DiceRoll roll(Random rng) {
        return new DiceRoll(d6(rng), d6(rng), d6(rng), d6(rng));
    }

    private static int d6(Random r) { return 1 + r.nextInt(6); }

    public int[] dice() { return dice.clone();}
    //clone since dice is private and we dont want it modificated

    public int[][] pairings() {
        int a = dice[0] + dice[1], b = dice[2] + dice[3];
        int c = dice[0] + dice[2], d = dice[1] + dice[3];
        int e = dice[0] + dice[3], f = dice[1] + dice[2];
        return new int[][] { {a,b}, {c,d}, {e,f} };
    }
}
