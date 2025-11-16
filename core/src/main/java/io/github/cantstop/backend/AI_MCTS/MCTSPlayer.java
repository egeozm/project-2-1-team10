package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Open-loop MCTS dla Can't Stop – zgodny z Waszym API. */
public final class MCTSPlayer {

    private final Random rng;
    private final int maxIterations;
    private final double explorationC;
    private final int rolloutMaxRolls;

    public MCTSPlayer(Random rng, int maxIterations, double explorationC, int rolloutMaxRolls) {
        this.rng = (rng != null) ? rng : ThreadLocalRandom.current();
        this.maxIterations = Math.max(1, maxIterations);
        this.explorationC = explorationC;
        this.rolloutMaxRolls = Math.max(1, rolloutMaxRolls);
    }

    /** Zwraca MctsAction (STOP/ROLL/MOVE) dla bieżącej fazy. */
    public MctsAction decide(GameState rootState) {
        final Player rootPlayer = rootState.getCurrentPlayer();
        final List<MctsAction> rootActions = legalActionsFrom(rootState, null);
        final Node root = new Node(null, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lastRoll = null;

            // SELECTION
            while (!node.hasUntried() && !isTerminal(s)) {
                node = selectUCT(node);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lastRoll = step.lastRoll;
                if (step.terminal) break;
            }

            // EXPANSION
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

            // SIMULATION lub terminal w selekcji
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
            if (score > bestScore) { bestScore = score; best = ch; }
        }
        return best;
    }

    // ---------- Expansion ordering / ranking ----------

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
            // both (= nie single)
            if (!mv.isSingle()) score += 1000;
            // domknięcie?
            if (closesColumn(s, mv)) score += 500;
            // pchanie aktywnej?
            if (pushesActive(s, mv)) score += 50;
            // preferuj częste sumy 6–8
            if (prefersFrequent(mv)) score += 10;
            return score;
        }
        // zwykle ROLL > STOP (chcemy eksplorować)
        if (a.isRoll()) return 5;
        return 1; // STOP
    }

    private boolean closesColumn(GameState s, Move mv) {
        // U Was applyMove: jeśli sumA()>0 -> advanceOne(sumA), sumB()>0 -> advanceOne(sumB)
        // Zamknięcie jeśli perm + temp + 1 >= maxHeight dla którejś sumy użytej w MOVE
        int[] sums = { mv.sumA(), mv.sumB() };
        for (int sum : sums) {
            if (sum <= 0) continue; // single może mieć 0 po drugiej stronie
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
        int a = mv.sumA(), b = mv.sumB();
        return (a >= 6 && a <= 8) || (b >= 6 && b <= 8);
    }

    private double lookaheadHeuristic(GameState s, MctsAction a, Player root) {
        GameState copy = s.copy();
        StepResult r = applyActionInPlace(copy, a);
        if (r.terminal) return winnerIs(copy, root) ? 1.0 : 0.0;
        return io.github.cantstop.backend.AI_MCTS.Heuristics.normalize01(
            io.github.cantstop.backend.AI_MCTS.Heuristics.evaluate(copy, root));
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
                // w praktyce tu nie trafimy (CHOOSE_MOVE zawsze po ROLL+noBust)
                TurnManager.stop(s);
            }
        }

        if (isTerminal(s)) return winnerIs(s, root) ? 1.0 : 0.0;
        double h = io.github.cantstop.backend.AI_MCTS.Heuristics.evaluate(s, root);
        return io.github.cantstop.backend.AI_MCTS.Heuristics.normalize01(h);
    }

    // –– rollout policies ––

    private boolean policyShouldStop(GameState s) {
        int active = 0, tempSum = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int t = s.tempAtCol(col);
            if (t > 0) { active++; tempSum += t; }
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
            if (closesColumn(s, mv)) score += 500;  // domknięcie
            if (pushesActive(s, mv)) score += 50;   // pchanie aktywnej
            if (prefersFrequent(mv)) score += 10;   // 6–8
            if (score > bestScore) { bestScore = score; best = mv; }
        }
        return best != null ? best : legal.get(0);
    }

    // ---------------- Backprop ----------------

    private void backpropagate(Node node, double reward) {
        for (Node n = node; n != null; n = n.parent) {
            n.visits++;
            n.valueSum += reward; // reward zawsze z perspektywy gracza w korzeniu
        }
    }

    // ---------------- Decyzja w korzeniu ----------------

    private MctsAction bestActionAtRoot(Node root) {
        MctsAction bestA = null;
        Node bestN = null;
        for (Map.Entry<MctsAction, Node> e : root.children.entrySet()) {
            Node n = e.getValue();
            if (bestN == null
                || n.visits > bestN.visits
                || (n.visits == bestN.visits && n.mean() > bestN.mean())) {
                bestN = n;
                bestA = e.getKey();
            }
        }
        if (bestA == null && !root.untried.isEmpty()) return root.untried.get(0);
        return bestA != null ? bestA : MctsAction.roll();
    }

    // ---------------- Helpers gry ----------------

    private List<MctsAction> legalActionsFrom(GameState s, DiceRoll lastRoll) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            return Arrays.asList(MctsAction.stop(), MctsAction.roll());
        } else {
            // CHOOSE_MOVE – musimy mieć lastRoll z poprzedniego ROLL
            if (lastRoll == null) return Collections.emptyList();
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
                    TurnManager.noBust(s);          // przejście do CHOOSE_MOVE
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
        final DiceRoll lastRoll; // != null tylko po ROLL+noBust
        StepResult(boolean terminal, DiceRoll lastRoll) {
            this.terminal = terminal;
            this.lastRoll = lastRoll;
        }
    }



    /**
     * Decyzja w korzeniu, gdy faza to CHOOSE_MOVE – wymagany lastRoll z poprzedniego ROLL.
     * Zwraca MctsAction.MOVE(mv).
     */
    public MctsAction decide(GameState rootState, DiceRoll lastRoll) {
        if (rootState.getTurnPhase() != TurnPhase.CHOOSE_MOVE) {
            throw new IllegalStateException("decide(state,lastRoll) only for CHOOSE_MOVE phase");
        }
        final Player rootPlayer = rootState.getCurrentPlayer();

        // Root ma od razu listę MOVE pod dane lastRoll
        final List<MctsAction> rootActions = legalActionsFrom(rootState, lastRoll);
        final Node root = new Node(null, rootPlayer, rootActions);

        for (int it = 0; it < maxIterations; it++) {
            GameState s = rootState.copy();
            Node node = root;
            DiceRoll lr = lastRoll; // ważne: root CHOOSE_MOVE startuje z już znanym rzutem

            // SELECTION
            while (!node.hasUntried() && !isTerminal(s)) {
                node = selectUCT(node);
                StepResult step = applyActionInPlace(s, node.actionFromParent);
                lr = step.lastRoll; // aktualizuj po ROLL+noBust
                if (step.terminal) break;
            }

            // EXPANSION
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

            // SIMULATION / terminal
            double reward;
            if (!isTerminal(s)) {
                reward = rolloutFrom(s, rootPlayer);
            } else {
                reward = winnerIs(s, rootPlayer) ? 1.0 : 0.0;
            }
            backpropagate(node, reward);
        }

        // W CHOOSE_MOVE oczekujemy MOVE
        MctsAction best = bestActionAtRoot(root);
        if (best == null || !best.isMove()) {
            // fallback: jeśli cokolwiek poszło nie tak, wybierz pierwszy legalny MOVE
            List<MctsAction> acts = legalActionsFrom(rootState, lastRoll);
            return acts.isEmpty() ? null : acts.get(0);
        }
        return best;
    }

}
