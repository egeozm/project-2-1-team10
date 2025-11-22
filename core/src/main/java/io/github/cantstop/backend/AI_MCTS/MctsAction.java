package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.Move;
import java.util.Objects;

/*
 * One edge action in the MCTS tree
 *
 * There are three kinds of decisions our search can take
 *  - STOP which ends the turn and commits temporary progress
 *  - ROLL which rolls four dice and can lead to bust or to choose move
 *  - MOVE which picks a concrete pairing valid only when phase is choose move
 *
 * For MOVE actions the field move must be non null and encodes the chosen pairing
 * STOP and ROLL carry no extra payload and their move is null
 */
public final class MctsAction {

    /* The type of decision this action represents */
    public enum Kind { STOP, ROLL, MOVE }

    /* Which kind of action this is */
    public final Kind kind;

    /* Concrete move payload set only when kind equals MOVE */
    public final Move move;

    private MctsAction(Kind kind, Move move) {
        this.kind = kind;
        this.move = move;
    }

    /* Create a STOP action */
    public static MctsAction stop() { return new MctsAction(Kind.STOP, null); }

    /* Create a ROLL action */
    public static MctsAction roll() { return new MctsAction(Kind.ROLL, null); }

    /* Create a MOVE action for the given pairing choice */
    public static MctsAction move(Move mv) {
        if (mv == null) throw new IllegalArgumentException("move == null");
        return new MctsAction(Kind.MOVE, mv);
    }

    /* Convenience predicates for readability */
    public boolean isStop() { return kind == Kind.STOP; }
    public boolean isRoll() { return kind == Kind.ROLL; }
    public boolean isMove() { return kind == Kind.MOVE; }

    /* Value semantics actions compare by kind and payload */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MctsAction that)) return false;
        return kind == that.kind && Objects.equals(move, that.move);
    }

    @Override
    public int hashCode() { return Objects.hash(kind, move); }

    @Override
    public String toString() {
        switch (kind) {
            case STOP: return "STOP";
            case ROLL: return "ROLL";
            case MOVE: return "MOVE(" + move + ")";
            default:   return "UNKNOWN";
        }
    }
}
