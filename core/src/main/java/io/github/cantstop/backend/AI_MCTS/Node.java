package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.Player;
import java.util.*;

public final class Node {
    public final Node parent;
    public final Map<MctsAction, Node> children = new LinkedHashMap<>();
    public final List<MctsAction> untried = new ArrayList<>();

    public int visits = 0;
    public double valueSum = 0.0;

    public final Player playerToMove;
    public MctsAction actionFromParent = null;

    public Node(Node parent, Player playerToMove, Collection<MctsAction> untriedActions) {
        this.parent = parent;
        this.playerToMove = playerToMove;
        if (untriedActions != null) this.untried.addAll(untriedActions);
    }

    public double mean() { return visits == 0 ? 0.0 : (valueSum / visits); }
    public boolean hasUntried() { return !untried.isEmpty(); }
    public boolean isLeaf() { return children.isEmpty(); }

    public Node addChild(MctsAction a, Player childPlayerToMove, Collection<MctsAction> childUntried) {
        Node ch = new Node(this, childPlayerToMove, childUntried);
        ch.actionFromParent = a;
        children.put(a, ch);
        return ch;
    }
    public MctsAction popUntried() { return untried.remove(0); }
    public void addVirtualVisit(double reward) { this.visits += 1; this.valueSum += reward; }

    @Override public String toString() {
        return "Node{player=" + playerToMove + ", N=" + visits + ", Q=" + String.format(java.util.Locale.ROOT, "%.4f", mean()) +
            ", untried=" + untried.size() + ", children=" + children.size() + '}';
    }
}
