package io.github.cantstop.model.ai.AI_MCTS;

import io.github.cantstop.model.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import io.github.cantstop.model.ai.AI_Expectiminimax.BustTable;

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
    private final double dpwK;
    private final double dpwAlpha;
    private final long timeBudgetMs;


    public MCTSPlayer(Random rng,
                      int maxIterations,
                      double explorationC,
                      int rolloutMaxRolls,
                      double dpwK,
                      double dpwAlpha,
                      long timeBudgetMs) {
        this.rng = (rng != null) ? rng : ThreadLocalRandom.current();
        this.maxIterations = Math.max(1, maxIterations);
        this.explorationC = explorationC;
        this.rolloutMaxRolls = Math.max(1, rolloutMaxRolls);
        this.dpwK = dpwK;
        this.dpwAlpha = dpwAlpha;
        this.timeBudgetMs = Math.max(0, timeBudgetMs);
    }

    public MCTSPlayer(Random rng, int maxIterations, double explorationC, int rolloutMaxRolls) {
        this(rng, maxIterations, explorationC, rolloutMaxRolls, 0.0, 0.0, 0L);
    }


    /* Decide an action STOP ROLL or MOVE for the current phase */
    public MctsAction decide(GameState rootState) {
        // HARD SAFETY: if STOP is available and our stop-policy says stop, do it immediately.
// This makes GUI behavior human-like (prevents endless rolling).
        if (rootState.getTurnPhase() == TurnPhase.ROLL_OR_STOP
            && rootState.countActiveColumns() > 0
            && policyShouldStop(rootState)) {
            return MctsAction.stop();
        }
        final Player rootPlayer = rootState.getCurrentPlayer();
        final List<MctsAction> rootActions = legalActionsFrom(rootState, null);
        final Node root = new Node(null, Node.Type.DECISION, rootPlayer, rootActions);

        final long deadlineNs = (timeBudgetMs > 0)
            ? System.nanoTime() + timeBudgetMs * 1_000_000L
            : Long.MAX_VALUE;

        for (int it = 0; it < maxIterations && System.nanoTime() < deadlineNs; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lastRoll = null;

            // ---------------- Selection (DPW-aware) ----------------
            while (!isTerminal(s)) {

                int limit = dpwLimit(node.visits);

                boolean canExpandHere = node.hasUntried() && node.children.size() < limit;
                if (canExpandHere) break;

                if (node.children.isEmpty()) break;

                node = selectUCT(node, rootPlayer);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lastRoll = step.lastRoll;
                if (step.terminal) break;
            }

            // ---------------- Expansion (DPW-aware) ----------------
            if (!isTerminal(s)) {
                int limit = dpwLimit(node.visits);
                if (node.hasUntried() && node.children.size() < limit) {
                    sortUntriedForExpansion(node.untried, s, lastRoll, rootPlayer);
                    MctsAction a = node.popUntried();

                    StepResult step = applyActionInPlace(s, a);

                    Node child = node.addChild(
                        a,
                        Node.Type.DECISION,
                        s.getCurrentPlayer(),
                        legalActionsFrom(s, step.lastRoll)
                    );
                    child.actionFromParent = a;

                    if (step.terminal) {
                        double reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
                        backpropagate(child, reward);
                        continue;
                    }

                    node = child;
                    lastRoll = step.lastRoll;
                }
            }

            // ---------------- Simulation / Terminal ----------------
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

    private Node selectUCT(Node parent, Player rootPlayer) {
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        final double lnN = Math.log(Math.max(1, parent.visits));

        final double sign = (parent.playerToMove == rootPlayer) ? 1.0 : -1.0;

        for (Node ch : parent.children.values()) {
            double q = ch.mean();
            double u = explorationC * Math.sqrt(lnN / Math.max(1, ch.visits));
            double score = sign * q + u;

            if (score > bestScore) {
                bestScore = score;
                best = ch;
            }
        }
        return best;
    }


    // ---------- Expansion ordering and ranking ----------

    private void sortUntriedForExpansion(List<MctsAction> untried, GameState s, DiceRoll lastRoll, Player root) {
        final boolean isRootTurn = (s.getCurrentPlayer() == root);
        final int sign = isRootTurn ? 1 : -1;

        untried.sort((a, b) -> {
            int ra = sign * rankMoveHeuristic(a, s);
            int rb = sign * rankMoveHeuristic(b, s);
            if (ra != rb) return Integer.compare(rb, ra);

            double ha = sign * lookaheadHeuristic(s, a, root);
            double hb = sign * lookaheadHeuristic(s, b, root);
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
        //if (a.isRoll()) return 5;
        //return 1; // STOP
        // Don't hard-bias roll vs stop; let lookaheadHeuristic decide.
        return 0;

    }

    private boolean closesColumn(GameState s, Move mv) {
        Player p = s.getCurrentPlayer();
        int[] sums = { mv.sumA(), mv.sumB() };

        for (int sum : sums) {
            if (sum <= 0) continue;

            int col  = GameConstants.sumToColumnID(sum);
            if (s.isColumnLocked(col)) continue;

            int maxH = GameConstants.maxHeight(sum);
            int cur  = currentHeight(s, col, p);
            int next = Math.min(cur + 1, maxH);

            if (next >= maxH) return true;
        }
        return false;
    }


    private boolean pushesActive(GameState s, Move mv) {
        Player p = s.getCurrentPlayer();
        int[] sums = { mv.sumA(), mv.sumB() };
        for (int sum : sums) {
            if (sum <= 0) continue;
            int col = GameConstants.sumToColumnID(sum);

            // tempAtCol is absolute height; we need gain this turn
            if (tempGainThisTurn(s, col, p) > 0) return true;
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
        double val = Heuristics.evaluate(copy, root);
        return Heuristics.normalize01(val);
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

                    s.setLastRoll(dr);

                    List<Move> legal = TurnManager.getLegalMoves(s, dr);
                    if (legal.isEmpty()) {
                        TurnManager.bust(s);
                        s.setLastRoll(null);
                        continue;
                    }

                    TurnManager.noBust(s);
                    Move m = policyChooseMove(legal, s);
                    TurnManager.applyMove(s, m);
                    s.setLastRoll(null);
                }
            } else { // CHOOSE_MOVE
                DiceRoll lr = s.getLastRoll();
                if (lr == null) {
                    TurnManager.stop(s);
                    continue;
                }

                List<Move> legal = TurnManager.getLegalMoves(s, lr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    s.setLastRoll(null);
                    continue;
                }

                Move m = policyChooseMove(legal, s);
                TurnManager.applyMove(s, m);
                s.setLastRoll(null);
            }
        }

        if (isTerminal(s)) {
            return winnerIs(s, root) ? 1.0 : 0.0;
        }
        double h = Heuristics.evaluate(s, root);
        return Heuristics.normalize01(h);
    }


    private double computeBustChance(GameState s) {
        int mask = allowedSumsMask(s);
        return BustTable.P[mask];
    }

    private int allowedSumsMask(GameState s) {
        int mask = 0;
        for (int sum = GameConstants.COL_MIN; sum <= GameConstants.COL_MAX; sum++) {
            if (TurnManager.isSinglePlayable(s, sum)) {
                mask |= (1 << (sum - 2));
            }
        }
        return mask;
    }

    private double computeProgressValue(GameState state) {
        double progressValue = 0.0;
        Player p = state.getCurrentPlayer();

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = state.tempAtCol(col);
            int perm = state.getMarkerHeight(p, col);
            int gain = Math.max(temp - perm, 0);
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            progressValue += (double) gain / (double) maxH;
        }

        return progressValue / GameConstants.NUM_COLS;
    }


    private double computeMaxColumnFrac(GameState state) {
        Player p = state.getCurrentPlayer();
        double best = 0.0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = state.tempAtCol(col);
            if (temp == 0) continue;

            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);
            int perm = state.getMarkerHeight(p, col);
            int gain = Math.max(temp - perm, 0);

            best = Math.max(best, (double) gain / (double) maxH);
        }
        return best;
    }


    private boolean wouldLockAnyColumnOnStop(GameState s) {
        Player p = s.getCurrentPlayer();

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = s.tempAtCol(col);
            if (temp == 0) continue;

            int sum  = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            if (temp >= maxH && s.getMarkerHeight(p, col) < maxH) {
                return true;
            }
        }
        return false;
    }


    // –– rollout policies ––


    private static final double STOP_PROGRESS_W = 7.0;
    private static final double STOP_BUST_W     = 2.0;
    private static final double MIN_PROGRESS_TO_CONSIDER_STOP = 0.02;

    private boolean policyShouldStop(GameState s) {
        if (s.countActiveColumns() == 0) return false;

        if (wouldWinOnStop(s)) return true;
        if (wouldCompleteAnyColumnOnStop(s)) return true;

        double bust = computeBustChance(s);     // 0..1
        double prog = computeProgressValue(s);  // ~0..0.27

        if (prog < MIN_PROGRESS_TO_CONSIDER_STOP) return false;

        return prog * STOP_PROGRESS_W + bust * STOP_BUST_W > 1.0;
    }



    private double maxProgressFractionThisTurn(GameState s) {
        Player p = s.getCurrentPlayer();
        double best = 0.0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = s.tempAtCol(col);
            if (temp == 0) continue;

            int perm = s.getMarkerHeight(p, col);
            int gain = Math.max(0, temp - perm);
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            double frac = (maxH == 0) ? 0.0 : (gain / (double) maxH);
            if (frac > best) best = frac;
        }
        return best;
    }



    private boolean wouldCompleteAnyColumnOnStop(GameState s) {
        Player p = s.getCurrentPlayer();
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int temp = s.tempAtCol(col);
            if (temp == 0) continue;

            int perm = s.getMarkerHeight(p, col);
            if (temp <= perm) continue;

            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            if (temp >= maxH) return true;
        }
        return false;
    }

    private boolean wouldWinOnStop(GameState s) {
        Player p = s.getCurrentPlayer();
        int count = 0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            int perm = s.getMarkerHeight(p, col);
            int temp = s.tempAtCol(col);

            if (perm >= maxH) { count++; continue; }

            if (temp > perm && temp >= maxH) count++;
        }
        return count >= GameConstants.TO_WIN;
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
        double bestMean = Double.NEGATIVE_INFINITY;
        int bestVisits = -1;

        for (Map.Entry<MctsAction, Node> e : root.children.entrySet()) {
            Node n = e.getValue();
            double m = n.mean();

            if (m > bestMean) {
                bestMean = m;
                bestVisits = n.visits;
                bestA = e.getKey();
            } else if (m == bestMean) {

                if (n.visits > bestVisits) {
                    bestVisits = n.visits;
                    bestA = e.getKey();
                }
            }
        }

        if (bestA == null && !root.untried.isEmpty()) return root.untried.get(0);
        return (bestA != null) ? bestA : MctsAction.roll();
    }


    // ---------------- Game helpers ----------------

    private List<MctsAction> legalActionsFrom(GameState s, DiceRoll lastRoll) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            boolean canStop = s.countActiveColumns() > 0;

            if (!canStop) {
                return Collections.singletonList(MctsAction.roll());
            }

            if (wouldLockAnyColumnOnStop(s)) {
                return Collections.singletonList(MctsAction.stop());
            }

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
                s.setLastRoll(dr);

                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    s.setLastRoll(null);
                    return new StepResult(isTerminal(s), null);
                } else {
                    TurnManager.noBust(s);
                    return new StepResult(isTerminal(s), dr);
                }
            }
            case MOVE -> {
                TurnManager.applyMove(s, a.move);
                s.setLastRoll(null);
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
        final List<MctsAction> rootActions = legalActionsFrom(rootState, lastRoll);
        final Node root = new Node(null, Node.Type.DECISION, rootPlayer, rootActions);

        final long deadlineNs = (timeBudgetMs > 0)
            ? System.nanoTime() + timeBudgetMs * 1_000_000L
            : Long.MAX_VALUE;

        for (int it = 0; it < maxIterations && System.nanoTime() < deadlineNs; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lr = lastRoll;

            // ---------------- Selection (DPW-aware) ----------------
            while (!isTerminal(s)) {

                int limit = dpwLimit(node.visits);
                boolean canExpandHere = node.hasUntried() && node.children.size() < limit;
                if (canExpandHere) break;

                if (node.children.isEmpty()) break;

                node = selectUCT(node, rootPlayer);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lr = step.lastRoll;
                if (step.terminal) break;
            }

            // ---------------- Expansion (DPW-aware) ----------------
            if (!isTerminal(s)) {
                int limit = dpwLimit(node.visits);
                if (node.hasUntried() && node.children.size() < limit) {
                    sortUntriedForExpansion(node.untried, s, lr, rootPlayer);
                    MctsAction a = node.popUntried();

                    StepResult step = applyActionInPlace(s, a);

                    Node child = node.addChild(
                        a,
                        Node.Type.DECISION,
                        s.getCurrentPlayer(),
                        legalActionsFrom(s, step.lastRoll)
                    );
                    child.actionFromParent = a;

                    if (step.terminal) {
                        double reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
                        backpropagate(child, reward);
                        continue;
                    }

                    node = child;
                    lr = step.lastRoll;
                }
            }

            // ---------------- Simulation / Terminal ----------------
            double reward;
            if (!isTerminal(s)) {
                reward = rolloutFrom(s, rootPlayer);
            } else {
                reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
            }
            backpropagate(node, reward);
        }

        MctsAction best = bestActionAtRoot(root);
        if (best != null && best.isMove()) return best;

        List<MctsAction> acts = legalActionsFrom(rootState, lastRoll);
        return acts.isEmpty() ? null : acts.get(0);
    }


    private int currentHeight(GameState s, int col, Player p) {
        int perm = s.getMarkerHeight(p, col);
        int temp = s.tempAtCol(col);
        return (temp == 0) ? perm : temp;
    }

    private int tempGainThisTurn(GameState s, int col, Player p) {
        int temp = s.tempAtCol(col);
        if (temp == 0) return 0;
        int perm = s.getMarkerHeight(p, col);
        return Math.max(0, temp - perm);
    }



    private int dpwLimit(int visits) {
        if (dpwK <= 0.0) return Integer.MAX_VALUE;
        double v = Math.max(1, visits);
        return Math.max(1, (int)Math.floor(dpwK * Math.pow(v, dpwAlpha)));
    }


}
