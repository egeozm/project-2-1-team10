package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.Move;
import java.util.Objects;

/**
 * Pojedyncza akcja dla MCTS: STOP, ROLL lub MOVE(<Move>).
 * Nie zależy od żadnych zmian w istniejącym kodzie.
 */
public final class MctsAction {

    public enum Kind { STOP, ROLL, MOVE }

    public final Kind kind;
    public final Move move; // != null tylko gdy kind == MOVE

    private MctsAction(Kind kind, Move move) {
        this.kind = kind;
        this.move = move;
    }

    // Fabryki
    public static MctsAction stop() { return new MctsAction(Kind.STOP, null); }
    public static MctsAction roll() { return new MctsAction(Kind.ROLL, null); }
    public static MctsAction move(Move mv) {
        if (mv == null) throw new IllegalArgumentException("move == null");
        return new MctsAction(Kind.MOVE, mv);
    }

    public boolean isStop() { return kind == Kind.STOP; }
    public boolean isRoll() { return kind == Kind.ROLL; }
    public boolean isMove() { return kind == Kind.MOVE; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MctsAction that)) return false;
        return kind == that.kind && Objects.equals(move, that.move);
    }

    @Override public int hashCode() {
        return Objects.hash(kind, move);
    }

    @Override public String toString() {
        return switch (kind) {
            case STOP -> "STOP";
            case ROLL -> "ROLL";
            case MOVE -> "MOVE(" + move + ")";
        };
    }
}
