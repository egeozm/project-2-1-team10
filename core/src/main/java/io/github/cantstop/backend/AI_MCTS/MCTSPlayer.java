package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.*;
import io.github.cantstop.backend.AI_Expectiminimax.RollBucketer;

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
    private Player rootPlayer;


    // DPW defaults (backwards compatible constructor)
    private static final double DEFAULT_DPW_K = 4.0;
    private static final double DEFAULT_DPW_ALPHA = 0.5;

    // --- DPW parameters (chance nodes) ---
    // Start values (we will tune later in self-play)
    private final double dpwK;
    private final double dpwAlpha;

    // outcome frequency lookup (for weighted pick among existing outcomes)
    private final Map<MctsAction, Integer> outcomeFreq = new HashMap<>();

    // 4-arg constructor (compat)
    public MCTSPlayer(Random rng, int maxIterations, double explorationC, int rolloutMaxRolls) {
        this(rng, maxIterations, explorationC, rolloutMaxRolls, DEFAULT_DPW_K, DEFAULT_DPW_ALPHA);
    }


    public MCTSPlayer(Random rng, int maxIterations, double explorationC, int rolloutMaxRolls, double dpwK, double dpwAlpha) {
        if (rng != null) {
            this.rng = rng;
        } else {
            this.rng = ThreadLocalRandom.current();
        }
        this.maxIterations = Math.max(1, maxIterations);
        this.explorationC = explorationC;
        this.rolloutMaxRolls = Math.max(1, rolloutMaxRolls);
        this.dpwK = dpwK;
        this.dpwAlpha = dpwAlpha;

        initOutcomeFreq();
    }



    /* Decide an action STOP ROLL or MOVE for the current phase */
    public MctsAction decide(GameState rootState) {
        this.rootPlayer = rootState.getCurrentPlayer();
        final Player rootPlayer = this.rootPlayer;
        final List<MctsAction> rootActions = legalActionsFrom(rootState, (MctsAction) null);
        final Node root = new Node(null, Node.Type.DECISION, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            MctsAction lastOutcome = null;

            // Selection (handles CHANCE nodes explicitly)
            while (!isTerminal(s)) {

                // CHANCE node: DPW + sample outcome
                if (node.type == Node.Type.CHANCE) {

                    int limit = dpwLimit(node);

                    // 1) propose a random outcome according to true distribution
                    MctsAction outcome = sampleOutcomeAction();

                    // 2) DPW rule: if this is a new outcome but we already reached the limit,
                    //    reuse an existing outcome (weighted)
                    if (!node.children.containsKey(outcome) && node.children.size() >= limit) {
                        outcome = pickExistingOutcomeWeighted(node);
                    }

                    // 3) apply chosen outcome to the state
                    StepResult step = applyActionInPlace(s, outcome);
                    lastOutcome = step.lastOutcome;

                    Node ch = node.children.get(outcome);
                    if (ch == null) {
                        // expand new outcome child -> DECISION node
                        Node created = node.addChild(
                            outcome,
                            Node.Type.DECISION,
                            s.getCurrentPlayer(),
                            legalActionsFrom(s, lastOutcome)
                        );
                        node = created;
                        break; // expanded, stop selection
                    } else {
                        node = ch;
                        if (step.terminal) break;
                        continue;
                    }
                }

                // DECISION node: standard selection until we find a node with untried actions
                if (node.hasUntried()) break;

                node = selectUCT(node);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lastOutcome = step.lastOutcome;

                if (step.terminal) break;
            }

            // Expansion (DECISION node only)
            if (!isTerminal(s) && node.type == Node.Type.DECISION && node.hasUntried()) {
                sortUntriedForExpansion(node.untried, s, rootPlayer);
                MctsAction a = node.popUntried();
                StepResult step = applyActionInPlace(s, a);
                lastOutcome = step.lastOutcome;

                Node child;
                if (a.isRoll()) {
                    // After deciding to ROLL, we go to a CHANCE node (no direct game-state change here)
                    child = node.addChild(a, Node.Type.CHANCE, s.getCurrentPlayer(), Collections.emptyList());
                } else {
                    // STOP or MOVE -> normal DECISION node
                    child = node.addChild(a, Node.Type.DECISION, s.getCurrentPlayer(), legalActionsFrom(s, lastOutcome));
                }
                node = child;

                if (step.terminal) {
                    double reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
                    backpropagate(node, reward);
                    continue;
                }
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
        double logN = Math.log(Math.max(1, parent.visits));
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        boolean opponentTurn = (parent.playerToMove != this.rootPlayer);

        for (Node child : parent.children.values()) {
            // Defensive: if unvisited, take it immediately
            if (child.visits == 0) return child;

            double q = child.valueSum / child.visits; // value for ROOT player in [0,1]

            // Opponent tries to minimize root's outcome -> flip for mover perspective
            double qFromMoverPerspective = opponentTurn ? (1.0 - q) : q;

            double u = explorationC * Math.sqrt(logN / child.visits);
            double score = qFromMoverPerspective + u;

            if (score > bestScore) {
                bestScore = score;
                best = child;
            }
        }
        return best;
    }


    // ---------- Expansion ordering and ranking ----------

    private void sortUntriedForExpansion(List<MctsAction> untried, GameState s, Player root) {
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

    private void initOutcomeFreq() {
        outcomeFreq.clear();
        for (RollBucketer.Entry e : RollBucketer.entries()) {
            RollBucketer.Bucket b = e.bucket;
            MctsAction a = MctsAction.outcome(b.a, b.b, b.c);
            outcomeFreq.put(a, e.frequency);
        }
    }

    private int dpwLimit(Node chanceNode) {
        // m(n) = ceil( K * N(n)^alpha )
        int n = Math.max(1, chanceNode.visits);
        int m = (int) Math.ceil(dpwK * Math.pow(n, dpwAlpha));
        return Math.max(1, m);
    }

    private MctsAction pickExistingOutcomeWeighted(Node chanceNode) {
        if (chanceNode.children.isEmpty()) {
            return sampleOutcomeAction();
        }

        int total = 0;
        for (MctsAction a : chanceNode.children.keySet()) {
            total += outcomeFreq.getOrDefault(a, 1);
        }

        int r = rng.nextInt(Math.max(1, total));
        int acc = 0;
        for (MctsAction a : chanceNode.children.keySet()) {
            acc += outcomeFreq.getOrDefault(a, 1);
            if (r < acc) return a;
        }

        // fallback
        return chanceNode.children.keySet().iterator().next();
    }

    private MctsAction sampleOutcomeAction() {
        // Weighted by frequency (total = 1296)
        int r = rng.nextInt(RollBucketer.totalOutcomes());
        int acc = 0;
        for (RollBucketer.Entry e : RollBucketer.entries()) {
            acc += e.frequency;
            if (r < acc) {
                RollBucketer.Bucket b = e.bucket;
                return MctsAction.outcome(b.a, b.b, b.c);
            }
        }
        // Fallback (should never happen)
        RollBucketer.Entry e = RollBucketer.entries().get(0);
        RollBucketer.Bucket b = e.bucket;
        return MctsAction.outcome(b.a, b.b, b.c);
    }

    // Tree version (no DiceRoll in nodes)
    private List<MctsAction> legalActionsFrom(GameState s, MctsAction lastOutcome) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {

            int tempSum = 0;
            for (int col = 0; col < GameConstants.NUM_COLS; col++) {
                tempSum += s.tempAtCol(col);
            }

            // If we have no temporary progress, STOP is pointless (banking 0)
            if (tempSum == 0) {
                return Collections.singletonList(MctsAction.roll());
            }

            return Arrays.asList(MctsAction.stop(), MctsAction.roll());
        } else {
            // CHOOSE_MOVE requires last outcome from the previous OUTCOME
            if (lastOutcome == null || !lastOutcome.isOutcome()) {
                return Collections.emptyList();
            }
            List<Move> moves = TurnManager.getLegalMovesFromPairings(
                s, lastOutcome.packedA, lastOutcome.packedB, lastOutcome.packedC
            );
            List<MctsAction> out = new ArrayList<>(moves.size());
            for (Move m : moves) out.add(MctsAction.move(m));
            return out;
        }
    }

    // GUI/known-roll version (keeps DiceRoll)
    private List<MctsAction> legalActionsFrom(GameState s, DiceRoll lastRoll) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {

            int tempSum = 0;
            for (int col = 0; col < GameConstants.NUM_COLS; col++) {
                tempSum += s.tempAtCol(col);
            }

            // If we have no temporary progress, STOP is pointless (banking 0)
            if (tempSum == 0) {
                return Collections.singletonList(MctsAction.roll());
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

    // Tree apply (ROLL does NOT roll; OUTCOME does bust/noBust)
    private StepResult applyActionInPlace(GameState s, MctsAction a) {
        switch (a.kind) {
            case STOP -> {
                TurnManager.stop(s);
                return new StepResult(isTerminal(s), null);
            }
            case ROLL -> {
                // IMPORTANT: ROLL no longer samples dice here.
                // The random outcome is handled by an OUTCOME action from a CHANCE node.
                return new StepResult(isTerminal(s), null);
            }
            case OUTCOME -> {
                // Apply a sampled dice outcome represented as (packedA,packedB,packedC)
                List<Move> legal = TurnManager.getLegalMovesFromPairings(s, a.packedA, a.packedB, a.packedC);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    return new StepResult(isTerminal(s), null);
                } else {
                    TurnManager.noBust(s); // -> CHOOSE_MOVE
                    return new StepResult(isTerminal(s), a); // pass outcome forward
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
        final MctsAction lastOutcome; // non-null only after OUTCOME with no bust
        StepResult(boolean terminal, MctsAction lastOutcome) {
            this.terminal = terminal;
            this.lastOutcome = lastOutcome;
        }
    }

    // Known-roll apply (ROLL DOES roll; OUTCOME is unused here)
    private StepResultRoll applyActionInPlaceWithRoll(GameState s, MctsAction a) {
        switch (a.kind) {
            case STOP -> {
                TurnManager.stop(s);
                return new StepResultRoll(isTerminal(s), null);
            }
            case ROLL -> {
                DiceRoll dr = TurnManager.roll(s, rng);
                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    return new StepResultRoll(isTerminal(s), null);
                } else {
                    TurnManager.noBust(s);
                    return new StepResultRoll(isTerminal(s), dr);
                }
            }
            case MOVE -> {
                TurnManager.applyMove(s, a.move);
                return new StepResultRoll(isTerminal(s), null);
            }
            case OUTCOME -> {
                // Not used in this mode
                return new StepResultRoll(isTerminal(s), null);
            }
        }
        throw new IllegalStateException("Unknown action kind: " + a.kind);
    }

    private static final class StepResultRoll {
        final boolean terminal;
        final DiceRoll lastRoll; // non null only after ROLL with no bust
        StepResultRoll(boolean terminal, DiceRoll lastRoll) {
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
        final Node root = new Node(null, Node.Type.DECISION, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lr = lastRoll; // important since CHOOSE_MOVE root starts with a known roll

            // Selection
            while (!node.hasUntried() && !isTerminal(s)) {
                node = selectUCT(node);
                StepResultRoll step = applyActionInPlaceWithRoll(s, node.actionFromParent);
                lr = step.lastRoll;
                if (step.terminal) break;
            }

            // Expansion
            if (!isTerminal(s) && node.hasUntried()) {
                sortUntriedForExpansion(node.untried, s, rootPlayer);
                MctsAction a = node.popUntried();
                StepResultRoll step = applyActionInPlaceWithRoll(s, a);

                Node child = node.addChild(a, Node.Type.DECISION, s.getCurrentPlayer(),
                    legalActionsFrom(s, step.lastRoll));
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
