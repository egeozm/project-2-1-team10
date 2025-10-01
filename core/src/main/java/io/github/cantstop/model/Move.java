package io.github.cantstop.model;

import io.github.cantstop.utensils.ConstantsBE;
import java.util.Objects;

public final class Move {
    private final int pairingIndex; // 0/1/2 - which combination of dices player choose
    private final int sumA;
    private final int sumB;

    private Move(int pairingIndex, int sumA, int sumB) {
        validate(pairingIndex, sumA, sumB);
        this.pairingIndex = pairingIndex;
        this.sumA = sumA;
        this.sumB = sumB;
    }

    // Factory
    public static Move of(int pairingIndex, int sumA, int sumB) {
        return new Move(pairingIndex, sumA, sumB);
    }

    // Build move from DiceRoll + chosen pairing
    public static Move fromPairing(DiceRoll r, int pairingIndex) {
        if (pairingIndex < 0 || pairingIndex > 2)
            throw new IllegalArgumentException("no such pairingIndex");

        // get the dice for this pairing
        int[] dice = r.getPairing(pairingIndex);

        // First pair = dice[0] + dice[1]
        int sumA = dice[0] + dice[1];
        // Second pair = dice[2] + dice[3]
        int sumB = dice[2] + dice[3];

        return new Move(pairingIndex, sumA, sumB);
    }

    private static void validate(int pairingIndex, int a, int b) {
        if (pairingIndex < 0 || pairingIndex > 2)
            throw new IllegalArgumentException("no such pairingIndex");
        if (a < ConstantsBE.COL_MIN || a > ConstantsBE.COL_MAX)
            throw new IllegalArgumentException("sumA must be in 2 to 12");
        if (b < ConstantsBE.COL_MIN || b > ConstantsBE.COL_MAX)
            throw new IllegalArgumentException("sumB must be in 2 to 12");
    }

    public int pairingIndex() { return pairingIndex; }
    public int sumA() { return sumA; }
    public int sumB() { return sumB; }

    public boolean isSingle() { return sumA == sumB; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Move m)) return false;
        return pairingIndex == m.pairingIndex && sumA == m.sumA && sumB == m.sumB;
    }

    @Override
    public int hashCode() {
        return Objects.hash(pairingIndex, sumA, sumB);
    }

    @Override
    public String toString() {
        return "Move{pair=" + pairingIndex + ", sums=" + sumA + "+" + sumB + "}";
    }
}
