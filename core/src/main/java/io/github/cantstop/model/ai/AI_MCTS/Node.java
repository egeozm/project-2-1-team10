package io.github.cantstop.model.ai.AI_MCTS;

import io.github.cantstop.model.Player;
import java.util.*;

/*
 * One node in the MCTS search tree in open loop style
 *
 * The node does not store a GameState snapshot
 * It only keeps lightweight bookkeeping that the search needs
 *  - which player moves in this decision point
 *  - which action from the parent led to this node
 *  - statistics for tree policy and backup
 *  - topology children and a frontier of not yet expanded actions
 *
 * Value convention
 * Mean value equals valueSum divided by visits
 * The value is always from the root player perspective
 * This keeps backpropagation simple and avoids sign flips
 */
public final class Node {

    public enum Type { DECISION, CHANCE }
    public final Type type;

    /* Parent in the search tree or null for the root */
    public final Node parent;

    /* Already expanded children mapping from action to child node */
    public final Map<MctsAction, Node> children = new LinkedHashMap<>();

    /* Frontier with legal actions that are not expanded yet */
    public final List<MctsAction> untried = new ArrayList<>();

    /* How many times this node was visited during the search */
    public int visits = 0;

    /* Sum of rewards backed up to this node measured for the root player */
    public double valueSum = 0.0;

    /* Player who is on move in this decision point */
    public final Player playerToMove;

    /* The action taken in the parent that arrives at this node or null at the root */
    public MctsAction actionFromParent = null;

    /*
     * Create a node with an initial frontier of untried actions
     * parent           parent node or null for the root
     * playerToMove     player who moves in this node
     * untriedActions   legal actions available here may be empty
     */
    public Node(Node parent, Type type, Player playerToMove, Collection<MctsAction> untriedActions) {
        this.parent = parent;
        this.type = type;
        this.playerToMove = playerToMove;
        if (untriedActions != null) this.untried.addAll(untriedActions);
    }

    /* Mean value Q equals W divided by N safe for N equal zero returns zero */
    public double mean() {
        if (visits == 0) {
            return 0.0;
        } else {
            return valueSum / visits;
        }
    }

    /* True when there are still actions to expand */
    public boolean hasUntried() { return !untried.isEmpty(); }

    /* True when no children have been expanded yet */
    public boolean isLeaf() { return children.isEmpty(); }

    /*
     * Expand one action into a new child node and link it under this node
     * a                    action taken from this node
     * childPlayerToMove    player who moves in the child node
     * childUntried         initial frontier of the child with legal actions there
     * returns              the created child node
     */
    public Node addChild(MctsAction a, Type childType, Player childPlayerToMove, Collection<MctsAction> childUntried) {
        Node ch = new Node(this, childType, childPlayerToMove, childUntried);
        ch.actionFromParent = a;
        children.put(a, ch);
        return ch;
    }

    /*
     * Remove and return the next untried action to expand
     * The caller decides the ordering of the frontier beforehand
     */
    public MctsAction popUntried() { return untried.remove(0); }

    /*
     * Add a single visit and add the given reward to this node
     * Useful for simple virtual visit or optimistic init patterns
     */
    public void addVirtualVisit(double reward) { this.visits += 1; this.valueSum += reward; }

    @Override
    public String toString() {
        return "Node{type=" + type
            + ", player=" + playerToMove
            + ", N=" + visits
            + ", Q=" + String.format(java.util.Locale.ROOT, "%.4f", mean())
            + ", untried=" + untried.size()
            + ", children=" + children.size()
            + '}';
    }
}
