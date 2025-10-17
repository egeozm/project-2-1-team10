package io.github.cantstop.model;

/**
 * Represents a chosen move:
 * pairing index (0..2) + two sums from that dice pairing.
 */
public final class Move {

    private final int pairingIndex; // which dice pairing (0, 1, or 2)
    private final int sumA;         // first column sum
    private final int sumB;         // second column sum

    private Move(int pairingIndex, int sumA, int sumB) {
        this.pairingIndex = pairingIndex;
        this.sumA = sumA;
        this.sumB = sumB;
    }

    // create a move with given pairing index and sums
    public static Move of(int pairingIndex, int sumA, int sumB) {
        return new Move(pairingIndex, sumA, sumB);
    }

    // getters
    public int pairingIndex() { return pairingIndex; }
    public int sumA() { return sumA; }
    public int sumB() { return sumB; }

    // true if both sums target the same column
    public boolean isSingle() {
        if (sumA == sumB) {
            return true;
        } else {
            return false;
        }
    }

    // short debug-friendly string
    @Override
    public String toString() {
        return "Move{pair=" + pairingIndex + ", sums=" + sumA + "+" + sumB + "}";
    }
}
