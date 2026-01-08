package io.github.cantstop.model.ai.AI_MCTS;

import io.github.cantstop.model.Move;
import java.util.Objects;
import java.util.Collections;
import java.util.List;

/*
 * One edge action in the MCTS tree
 *
 * There are three kinds of decisions our search can take
 *  - STOP which ends the turn and commits temporary progress
 *  - ROLL which rolls four dice and can lead to bust or to choose move
 *  - OUTCOME which represents a sampled roll bucket outcome
 *  - MOVE which picks a concrete pairing valid only when phase is choose move
 *
 * For MOVE actions the field move must be non null and encodes the chosen pairing
 * STOP and ROLL carry no extra payload and their move is null
 */
public final class MctsAction {

    /* The type of decision this action represents */
    public enum Kind { STOP, ROLL, OUTCOME, MOVE }

    /* Which kind of action this is */
    public final Kind kind;

    /* Concrete move payload set only when kind equals MOVE */
    public final Move move;

    // Payload only for OUTCOME (packed pairings from RollBucketer)
    public final int packedA;
    public final int packedB;
    public final int packedC;

    private MctsAction(Kind kind, Move move, int packedA, int packedB, int packedC) {
        this.kind = kind;
        this.move = move;
        this.packedA = packedA;
        this.packedB = packedB;
        this.packedC = packedC;
    }

    /* Create a STOP action */
    public static MctsAction stop() { return new MctsAction(Kind.STOP, null, 0, 0, 0); }

    /* Create a ROLL action */
    public static MctsAction roll() { return new MctsAction(Kind.ROLL, null, 0, 0, 0); }

    public static MctsAction outcome(int packedA, int packedB, int packedC) {
        return new MctsAction(Kind.OUTCOME, null, packedA, packedB, packedC);
    }

    /* Create a MOVE action for the given pairing choice */
    public static MctsAction move(Move mv) {
        if (mv == null) throw new IllegalArgumentException("move == null");
        return new MctsAction(Kind.MOVE, mv, 0, 0, 0);
    }

    /* Convenience predicates for readability */
    public boolean isStop() { return kind == Kind.STOP; }
    public boolean isRoll() { return kind == Kind.ROLL; }
    public boolean isOutcome() { return kind == Kind.OUTCOME; }
    public boolean isMove() { return kind == Kind.MOVE; }

    /* Value semantics actions compare by kind and payload */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MctsAction that)) return false;
        if (kind != that.kind) return false;

        if (kind == Kind.MOVE) return Objects.equals(move, that.move);
        if (kind == Kind.OUTCOME) {
            return packedA == that.packedA && packedB == that.packedB && packedC == that.packedC;
        }
        return true; // STOP/ROLL no payload
    }

    @Override
    public int hashCode() {
        if (kind == Kind.MOVE) return Objects.hash(kind, move);
        if (kind == Kind.OUTCOME) return Objects.hash(kind, packedA, packedB, packedC);
        return Objects.hash(kind);
    }

    @Override
    public String toString() {
        switch (kind) {
            case STOP: return "STOP";
            case ROLL: return "ROLL";
            case OUTCOME: return "OUTCOME(" + packedA + "," + packedB + "," + packedC + ")";
            case MOVE: return "MOVE(" + move + ")";
            default:   return "UNKNOWN";
        }
    }
}
