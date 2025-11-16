package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.Player;

import java.util.*;

/**
 * Węzeł drzewa MCTS (open-loop).
 * - Nie przechowuje GameState (stan jest kopiowany i modyfikowany w trakcie iteracji).
 * - Trzyma tylko statystyki, listę niepróbowanych akcji i odniesienia do dzieci.
 */
public final class Node {

    public final Node parent;
    public final Map<MctsAction, Node> children = new LinkedHashMap<>();
    public final List<MctsAction> untried = new ArrayList<>();

    // statystyki
    public int visits = 0;         // N
    public double valueSum = 0.0;  // W (suma nagród z perspektywy gracza w korzeniu)

    // meta
    public final Player playerToMove;          // kto jest na ruchu w tym węźle
    public MctsAction actionFromParent = null; // jaka akcja prowadziła z parenta do tego węzła

    public Node(Node parent, Player playerToMove, Collection<MctsAction> untriedActions) {
        this.parent = parent;
        this.playerToMove = playerToMove;
        if (untriedActions != null) this.untried.addAll(untriedActions);
    }

    /** Średnia wartość Q = W/N. */
    public double mean() {
        return visits == 0 ? 0.0 : (valueSum / visits);
    }

    /** Czy węzeł ma jeszcze niepróbowane akcje. */
    public boolean hasUntried() {
        return !untried.isEmpty();
    }

    /** Czy węzeł jest liściem (brak dzieci). */
    public boolean isLeaf() {
        return children.isEmpty();
    }

    /** Dodaje dziecko pod daną akcją (ustawia też actionFromParent). */
    public Node addChild(MctsAction a, Player childPlayerToMove, Collection<MctsAction> childUntried) {
        Node ch = new Node(this, childPlayerToMove, childUntried);
        ch.actionFromParent = a;
        children.put(a, ch);
        return ch;
    }

    /** Zdejmuje jedną akcję z listy untried (FIFO), lub według indeksu. */
    public MctsAction popUntried() {
        return untried.remove(0);
    }

    /** Dodaje bonusowe (wirtualne) wizyty – opcjonalne prajory. */
    public void addVirtualVisit(double reward) {
        this.visits += 1;
        this.valueSum += reward;
    }

    @Override public String toString() {
        return "Node{" +
            "player=" + playerToMove +
            ", N=" + visits +
            ", Q=" + String.format(java.util.Locale.ROOT, "%.4f", mean()) +
            ", untried=" + untried.size() +
            ", children=" + children.size() +
            '}';
    }
}
