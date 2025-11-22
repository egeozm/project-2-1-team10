package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/*
 * Open loop MCTS for Can't Stop compatible with your API
 * The algorithm builds a search tree of actions without storing full GameState snapshots in nodes
 * It uses Selection Expansion Simulation and Backpropagation in a loop
 */
public final class MCTSPlayer {

    private final Random rng;
    private final int maxIterations;
    private final double explorationC;
    private final int rolloutMaxRolls;

    public MCTSPlayer(Random rng, int maxIterations, double explorationC, int rolloutMaxRolls) {
        if (rng != null) {
            this.rng = rng;
        } else {
            this.rng = ThreadLocalRandom.current();
        }
        this.maxIterations = Math.max(1, maxIterations);
        this.explorationC = explorationC;
        this.rolloutMaxRolls = Math.max(1, rolloutMaxRolls);
    }

    /* Decide an action STOP ROLL or MOVE for the current phase */
    public MctsAction decide(GameState rootState) {
        final Player rootPlayer = rootState.getCurrentPlayer();
        final List<MctsAction> rootActions = legalActionsFrom(rootState, null);
        final Node root = new Node(null, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lastRoll = null;

            // Selection
            while (!node.hasUntried() && !isTerminal(s)) {
                node = selectUCT(node);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lastRoll = step.lastRoll;
                if (step.terminal) break;
            }

            // Expansion
            if (!isTerminal(s) && node.hasUntried()) {
                sortUntriedForExpansion(node.untried, s, lastRoll, rootPlayer);
                MctsAction a = node.popUntried();
                StepResult step = applyActionInPlace(s, a);
                Node child = node.addChild(a, s.getCurrentPlayer(), legalActionsFrom(s, step.lastRoll));
                child.actionFromParent = a;

                if (step.terminal) {
                    double reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
                    backpropagate(child, reward);
                    continue;
                }
                node = child;
                lastRoll = step.lastRoll;
            }

            // Simulation or terminal reached during selection
            double reward;
            if (!isTerminal(s)) {
                reward = rolloutFrom(s, rootPlayer);
            } else {
                reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
            }
            backpropagate(node, reward);
        }

        return bestActionAtRoot(root);
    }

    // ---------------- UCT ----------------

    private Node selectUCT(Node parent) {
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        final double lnN = Math.log(Math.max(1, parent.visits));
        for (Node ch : parent.children.values()) {
            double q = ch.mean();
            double u = explorationC * Math.sqrt(lnN / Math.max(1, ch.visits));
            double score = q + u;
            if (score > bestScore) {
                bestScore = score;
                best = ch;
            }
        }
        return best;
    }

    // ---------- Expansion ordering and ranking ----------

    private void sortUntriedForExpansion(List<MctsAction> untried, GameState s, DiceRoll lastRoll, Player root) {
        untried.sort((a, b) -> {
            int ra = rankMoveHeuristic(a, s);
            int rb = rankMoveHeuristic(b, s);
            if (ra != rb) return Integer.compare(rb, ra);
            double ha = lookaheadHeuristic(s, a, root);
            double hb = lookaheadHeuristic(s, b, root);
            return Double.compare(hb, ha);
        });
    }

    private int rankMoveHeuristic(MctsAction a, GameState s) {
        if (a.isMove()) {
            Move mv = a.move;
            int score = 0;
            // both meaning not single
            if (!mv.isSingle()) score += 1000;
            // closing a column
            if (closesColumn(s, mv)) score += 500;
            // pushing an already active temp runner
            if (pushesActive(s, mv)) score += 50;
            // prefer frequent sums six seven eight
            if (prefersFrequent(mv)) score += 10;
            return score;
        }
        // usually ROLL over STOP to keep exploring
        if (a.isRoll()) return 5;
        return 1; // STOP
    }

    private boolean closesColumn(GameState s, Move mv) {
        // applyMove semantics in your code
        // if sumA greater than zero advanceOne sumA and same for sumB
        // closing when perm plus temp plus one reaches or exceeds max height
        int[] sums = { mv.sumA(), mv.sumB() };
        for (int sum : sums) {
            if (sum <= 0) continue;
            int col  = GameConstants.sumToColumnID(sum);
            int maxH = GameConstants.maxHeight(sum);
            int perm = s.getMarkerHeight(s.getCurrentPlayer(), col);
            int temp = s.tempAtCol(col);
            int next = perm + temp + 1;
            if (next >= maxH) return true;
        }
        return false;
    }

    private boolean pushesActive(GameState s, Move mv) {
        int[] sums = { mv.sumA(), mv.sumB() };
        for (int sum : sums) {
            if (sum <= 0) continue;
            int col = GameConstants.sumToColumnID(sum);
            if (s.tempAtCol(col) > 0) return true;
        }
        return false;
    }

    private boolean prefersFrequent(Move mv) {
        int a = mv.sumA();
        int b = mv.sumB();
        boolean aOk = a >= 6 && a <= 8;
        boolean bOk = b >= 6 && b <= 8;
        return aOk || bOk;
    }

    private double lookaheadHeuristic(GameState s, MctsAction a, Player root) {
        GameState copy = s.copy();
        StepResult r = applyActionInPlace(copy, a);
        if (r.terminal) {
            return winnerIs(copy, root) ? 1.0 : 0.0;
        }
        double val = io.github.cantstop.backend.AI_MCTS.Heuristics.evaluate(copy, root);
        return io.github.cantstop.backend.AI_MCTS.Heuristics.normalize01(val);
    }

    // ---------------- Rollout ----------------

    private double rolloutFrom(GameState start, Player root) {
        GameState s = start.copy();
        int rollsLeft = rolloutMaxRolls;

        while (!isTerminal(s) && rollsLeft > 0) {
            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                if (policyShouldStop(s)) {
                    TurnManager.stop(s);
                    continue;
                } else {
                    rollsLeft--;
                    DiceRoll dr = TurnManager.roll(s, rng);
                    List<Move> legal = TurnManager.getLegalMoves(s, dr);
                    if (legal.isEmpty()) {
                        TurnManager.bust(s);
                        continue;
                    }
                    TurnManager.noBust(s);
                    Move m = policyChooseMove(legal, s);
                    TurnManager.applyMove(s, m);
                }
            } else {
                // in practice this path is rare because CHOOSE_MOVE follows ROLL with no bust
                TurnManager.stop(s);
            }
        }

        if (isTerminal(s)) {
            return winnerIs(s, root) ? 1.0 : 0.0;
        }
        double h = io.github.cantstop.backend.AI_MCTS.Heuristics.evaluate(s, root);
        return io.github.cantstop.backend.AI_MCTS.Heuristics.normalize01(h);
    }

    // –– rollout policies ––

    private boolean policyShouldStop(GameState s) {
        int active = 0;
        int tempSum = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int t = s.tempAtCol(col);
            if (t > 0) {
                active++;
                tempSum += t;
            }
        }
        if (active >= 3 && tempSum >= 5) return true;
        if (active >= 2 && tempSum >= 6) return true;
        return false;
    }

    private Move policyChooseMove(List<Move> legal, GameState s) {
        Move best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Move mv : legal) {
            int score = 0;
            if (!mv.isSingle()) score += 1000;      // both
            if (closesColumn(s, mv)) score += 500;  // closing
            if (pushesActive(s, mv)) score += 50;   // pushing active
            if (prefersFrequent(mv)) score += 10;   // preference for six to eight
            if (score > bestScore) {
                bestScore = score;
                best = mv;
            }
        }
        if (best != null) {
            return best;
        } else {
            return legal.get(0);
        }
    }

    // ---------------- Backprop ----------------

    private void backpropagate(Node node, double reward) {
        for (Node n = node; n != null; n = n.parent) {
            n.visits++;
            n.valueSum += reward; // reward is always from the root player perspective
        }
    }

    // ---------------- Decision at root ----------------

    private MctsAction bestActionAtRoot(Node root) {
        MctsAction bestA = null;
        Node bestN = null;
        for (Map.Entry<MctsAction, Node> e : root.children.entrySet()) {
            Node n = e.getValue();
            boolean takeByVisits = bestN == null || n.visits > bestN.visits;
            boolean tieBreakByMean = bestN != null && n.visits == bestN.visits && n.mean() > bestN.mean();
            if (takeByVisits || tieBreakByMean) {
                bestN = n;
                bestA = e.getKey();
            }
        }
        if (bestA == null && !root.untried.isEmpty()) {
            return root.untried.get(0);
        }
        if (bestA != null) {
            return bestA;
        } else {
            return MctsAction.roll();
        }
    }

    // ---------------- Game helpers ----------------

    private List<MctsAction> legalActionsFrom(GameState s, DiceRoll lastRoll) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            return Arrays.asList(MctsAction.stop(), MctsAction.roll());
        } else {
            // CHOOSE_MOVE requires last roll from the previous ROLL
            if (lastRoll == null) {
                return Collections.emptyList();
            }
            List<Move> moves = TurnManager.getLegalMoves(s, lastRoll);
            List<MctsAction> out = new ArrayList<>(moves.size());
            for (Move m : moves) out.add(MctsAction.move(m));
            return out;
        }
    }

    private boolean isTerminal(GameState s) {
        return TurnManager.checkWinCondition(s, Player.RED)
            || TurnManager.checkWinCondition(s, Player.BLUE);
    }

    private boolean winnerIs(GameState s, Player p) {
        return TurnManager.checkWinCondition(s, p);
    }

    private StepResult applyActionInPlace(GameState s, MctsAction a) {
        switch (a.kind) {
            case STOP -> {
                TurnManager.stop(s);
                return new StepResult(isTerminal(s), null);
            }
            case ROLL -> {
                DiceRoll dr = TurnManager.roll(s, rng);
                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    return new StepResult(isTerminal(s), null);
                } else {
                    TurnManager.noBust(s);          // transition to CHOOSE_MOVE
                    return new StepResult(isTerminal(s), dr);
                }
            }
            case MOVE -> {
                TurnManager.applyMove(s, a.move);
                return new StepResult(isTerminal(s), null);
            }
        }
        throw new IllegalStateException("Unknown action kind: " + a.kind);
    }

    private static final class StepResult {
        final boolean terminal;
        final DiceRoll lastRoll; // non null only after ROLL with no bust
        StepResult(boolean terminal, DiceRoll lastRoll) {
            this.terminal = terminal;
            this.lastRoll = lastRoll;
        }
    }

    /*
     * Decision at a CHOOSE_MOVE root where the last roll is already known
     * Returns a MOVE action
     */
    public MctsAction decide(GameState rootState, DiceRoll lastRoll) {
        if (rootState.getTurnPhase() != TurnPhase.CHOOSE_MOVE) {
            throw new IllegalStateException("decide(state,lastRoll) only for CHOOSE_MOVE phase");
        }
        final Player rootPlayer = rootState.getCurrentPlayer();

        // Root starts with MOVE actions for the given last roll
        final List<MctsAction> rootActions = legalActionsFrom(rootState, lastRoll);
        final Node root = new Node(null, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lr = lastRoll; // important since CHOOSE_MOVE root starts with a known roll

            // Selection
            while (!node.hasUntried() && !isTerminal(s)) {
                node = selectUCT(node);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lr = step.lastRoll;
                if (step.terminal) break;
            }

            // Expansion
            if (!isTerminal(s) && node.hasUntried()) {
                sortUntriedForExpansion(node.untried, s, lr, rootPlayer);
                MctsAction a = node.popUntried();
                StepResult step = applyActionInPlace(s, a);
                Node child = node.addChild(a, s.getCurrentPlayer(), legalActionsFrom(s, step.lastRoll));
                child.actionFromParent = a;

                if (step.terminal) {
                    double reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
                    backpropagate(child, reward);
                    continue;
                }
                node = child;
                lr = step.lastRoll;
            }

            // Simulation or terminal
            double reward;
            if (!isTerminal(s)) {
                reward = rolloutFrom(s, rootPlayer);
            } else {
                reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
            }
            backpropagate(node, reward);
        }

        // In CHOOSE_MOVE we expect a MOVE
        MctsAction best = bestActionAtRoot(root);
        boolean okMove = best != null && best.isMove();
        if (okMove) {
            return best;
        } else {
            List<MctsAction> acts = legalActionsFrom(rootState, lastRoll);
            if (acts.isEmpty()) {
                return null;
            } else {
                return acts.get(0);
            }
        }
    }

}
