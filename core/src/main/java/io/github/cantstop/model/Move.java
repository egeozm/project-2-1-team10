package io.github.cantstop.model;

/**
 * Represents a chosen action:
 * pairing index (0..2) + two sums from that dice pairing
 */
public final class Move {

    private final int pairingIndex; // which dice pairing (0, 1, or 2)
    private final int sumA;         // first column sum
    private final int sumB;         // second column sum

    public Move(int pairingIndex, int sumA, int sumB) {
        this.pairingIndex = pairingIndex;
        this.sumA = sumA;
        this.sumB = sumB;
    }

    // getters
    public int pairingIndex() { return pairingIndex; }
    public int sumA() { return sumA; }
    public int sumB() { return sumB; }

    // true if both sums target the same column
    // true if the move uses only one sum (the other is 0)
    public boolean isSingle() {
        return sumA == 0 || sumB == 0;
    }


    // short print for simulation purposes
    @Override
    public String toString() {
        return "Move{pair=" + pairingIndex + ", sums=" + sumA + "+" + sumB + "}";
    }
}
