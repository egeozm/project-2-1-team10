package io.github.cantstop.backend;

import java.util.*;

/**
 * AI for Can't Stop using expectiminimax with heuristic evaluation.
 * - If a dice roll is provided, it evaluates STOP vs ROLL+Move.
 * - ROLL branch is a chance node over all 1296 possible dice outcomes (weighted equally).
 * - If no dice roll is provided, it simulates a ROLL (using the provided RNG) and then evaluates ROLL+Move.
 * <p>
 * The AI returns either:
 * - StopAction (commit current temp runners)
 * - RollAction(Move m) (apply a legal move for the given dice roll)
 */
public final class AIPlayer {

    // Small epsilon to break evaluation ties
    private static final double EPS = 1e-9;

    // Depth defaults; you can tune these
    public static final int DEFAULT_DEPTH_ROLL_PHASE = 4; // expectiminimax depth for ROLL (chance) nodes
    public static final int DEFAULT_DEPTH_AFTER_STOP = 5; // deterministic depth after STOP

    private final Random rng;

    // TT + node types
    private final TranspositionTable tt = new TranspositionTable(20); // ~1M slots
    private int ttEpoch = 0;
    private static final byte NODE_MAX = 0, NODE_MIN = 1, NODE_CHANCE = 2;

    // killer move tiny cache (optional but helpful)
    private final Map<Long, Move> killer = new HashMap<>();

    // bust penalty weight (tune later)
    private static final double LAMBDA_BUST = 80.0;

    // optional: deadline for timed search
    private long searchDeadlineNanos = Long.MAX_VALUE;

    public AIPlayer() {
        this(new Random());
    }

    public AIPlayer(Random rng) {
        this.rng = Objects.requireNonNull(rng, "rng");
    }

    /**
     * Decide whether to STOP or ROLL and, if ROLL, which Move to select.
     * The diceRoll parameter must be non-null if the caller already rolled; otherwise the AI will simulate a roll.
     */
    public Action chooseAction(GameState state, DiceRoll diceRoll) {
        return chooseAction(state, diceRoll, DEFAULT_DEPTH_ROLL_PHASE, DEFAULT_DEPTH_AFTER_STOP);
    }

    /**
     * Same as chooseAction, but allows specifying custom depths.
     */
    public Action chooseAction(GameState state, DiceRoll diceRoll, int rollDepth, int stopDepth) {
        if (diceRoll != null) {
            // We already know the roll: compare STOP vs ROLL+best move for THIS roll
            double stopScore = evalStop(state, stopDepth);
            double rollScore = evalRoll(state, diceRoll, rollDepth);
            if (stopScore + EPS >= rollScore) return StopAction.INSTANCE;
            Move bestMove = bestMoveForRoll(state, diceRoll, rollDepth);
            return new RollAction(bestMove);
        } else {
            // No roll yet: decide STOP vs expected value of ROLL (chance node)
            double stopScore = evalStop(state, stopDepth);
            double rollScore = expectedValueRoll(state, rollDepth, true); // current player is maximizing here

            if (stopScore + EPS >= rollScore) return StopAction.INSTANCE;

            // We chose to roll; now actually roll and pick the move for that real outcome
            DiceRoll realRoll = DiceRoll.roll(rng);
            List<Move> legal = TurnManager.getLegalMoves(state, realRoll);
            if (legal.isEmpty()) {
                // instant bust if no move is possible, but action must still be "ROLL"
                // You could return a special RollAction meaning "bust" or just pick a dummy move.
                // Here we choose the conservative path: return StopAction would be illegal; so pick a dummy single if any.
                return StopAction.INSTANCE; // If your engine requires an explicit "ROLL" action type, create it. Otherwise STOP is safest.
            }
            Move bestMove = bestMoveForRoll(state, realRoll, rollDepth);
            return new RollAction(bestMove);
        }
    }

    // ---------------------------
    // Evaluation helpers
    // ---------------------------

    // Evaluate STOP action from this state (deterministic)
    private double evalStop(GameState state, int depth) {
        GameState next = state.copy();
        TurnManager.stop(next);

        // If stopping immediately wins, just return a huge win and stop wasting time
        Player me = state.getCurrentPlayer();
        if (TurnManager.checkWinCondition(next, me)) return 1_000_000.0;
        if (TurnManager.checkWinCondition(next, me.opponent())) return -1_000_000.0; // practically impossible

        // After STOP, it's opponent's turn → minimizing node
        return expectiminimax(next, depth - 1, false);
    }


    public Action chooseActionWithTime(GameState state, DiceRoll diceRoll, long millis) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        this.searchDeadlineNanos = deadline;
        Action best = null;
        for (int d = 1; ; d++) {
            ttEpoch++;
            Action a = chooseAction(state, diceRoll, /*rollDepth=*/d, /*stopDepth=*/d + 1);
            if (System.nanoTime() >= deadline) return best != null ? best : a;
            best = a;
        }
    }


    private boolean hasPendingChance(GameState s) {
        // Your turn model: chance occurs when we choose ROLL, not as a separate phase in GameState.
        // In expectiminimax we call expectedValueRoll only from decision nodes, so this can return false here.
        return false;
    }

    // Evaluate ROLL + Move by branching over chance nodes and then maximizing over legal moves
    private double evalRoll(GameState state, DiceRoll actualRoll, int depth) {
        List<Move> legal = TurnManager.getLegalMoves(state, actualRoll);
        if (legal.isEmpty()) {
            // Bust: switch player without committing
            GameState bust = state.copy();
            TurnManager.bust(bust);
            double val = expectiminimax(bust, depth - 1, false); // minimize from opponent
            Player me = state.getCurrentPlayer();
            if (TurnManager.checkWinCondition(bust, me)) return 1_000_000 + val;
            if (TurnManager.checkWinCondition(bust, me.opponent())) return -1_000_000 + val;
            return val;
        }

        double best = Double.NEGATIVE_INFINITY;
        for (Move m : legal) {
            GameState child = state.copy();
            TurnManager.applyMove(child, m);
            // No win check here because you don't win by making a normal move
            double v = expectiminimax(child, depth - 1, false); // opponent will minimize after we roll next
            if (v > best) best = v;
        }
        return best;
    }

    // Pick the best move for the given roll using one-ply lookahead with chance expectation over rollDepth
    private Move bestMoveForRoll(GameState state, DiceRoll roll, int depth) {
        List<Move> legal = TurnManager.getLegalMoves(state, roll);
        Move best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Move m : legal) {
            GameState child = state.copy();
            TurnManager.applyMove(child, m);
            double v = expectiminimax(child, depth - 1, false); // minimize next
            if (v > bestScore - EPS) {
                bestScore = v;
                best = m;
            }
        }

        // Fallback (should not happen if legal moves exist)
        if (best == null) {
            best = legal.get(0);
        }
        return best;
    }

    // ---------------------------
    // Expectiminimax with heuristic evaluation
    // ---------------------------

    // maximize=true => the player to move is a maximizing player.
    // In our usage after STOP or after applying a move (ROLL), the next player is minimizing.
    private double expectiminimax(GameState state, int depth, boolean maximize) {

        long key = StateHasher.hash(state);
        byte nodeType = hasPendingChance(state) ? NODE_CHANCE : (maximize ? NODE_MAX : NODE_MIN);

        TTEntry hit = tt.get(key);
        if (hit != null && hit.nodeType == nodeType && hit.depth >= depth) {
            return hit.value;
        }


        // Terminal check
        Player red = Player.RED;
        Player blue = Player.BLUE;
        boolean redWin = TurnManager.checkWinCondition(state, red);
        boolean blueWin = TurnManager.checkWinCondition(state, blue);

        // If both could be true (rare edge), prefer current player.
        Player current = state.getCurrentPlayer();
        if (redWin && !blueWin) return (red == current) ? 10_000.0 : -10_000.0;
        if (blueWin && !redWin) return (blue == current) ? 10_000.0 : -10_000.0;
        if (redWin && blueWin) return 0.0; // extremely rare

        if (depth <= 0 || TurnManager.checkWinCondition(state, Player.RED) || TurnManager.checkWinCondition(state, Player.BLUE)) {
            return heuristicWithRisk(state);
        }

        // This is a choice node: STOP vs ROLL
        // - STOP commits and switches player -> deterministic
        // - ROLL is a chance node over all dice outcomes
        double stopScore;
        {
            GameState next = state.copy();
            TurnManager.stop(next);
            stopScore = expectiminimax(next, depth - 1, !maximize);
        }

        double rollScore = expectedValueRoll(state, depth, maximize);
        return maximize ? Math.max(stopScore, rollScore) : Math.min(stopScore, rollScore);
    }

    private double heuristicWithRisk(GameState s) {
        double base = heuristic(s, s.getCurrentPlayer());
        int mask = allowedSumsMask(s);
        double bustProb = BustTable.P[mask];
        return base - LAMBDA_BUST * bustProb;
    }

    // Build allowed-sums bitmask from the current state's legality of singles
    private int allowedSumsMask(GameState s) {
        int mask = 0;
        for (int sum = GameConstants.COL_MIN; sum <= GameConstants.COL_MAX; sum++) {
            if (TurnManager.isSinglePlayable(s, sum)) {
                mask |= (1 << (sum - 2));
            }
        }
        return mask;
    }


    private double expectedValueRoll(GameState state, int depth, boolean maximize) {
        if (depth <= 0 || TurnManager.checkWinCondition(state, Player.RED) || TurnManager.checkWinCondition(state, Player.BLUE)) {
            return heuristicWithRisk(state);
        }

        double weighted = 0.0;
        int total = 0;

        for (var e : RollBucketer.ENTRIES) {
            // optional time guard every N buckets
            if ((total & 63) == 0 && System.nanoTime() >= searchDeadlineNanos) {
                return heuristicWithRisk(state);
            }

            int pa = e.bucket.a, pb = e.bucket.b, pc = e.bucket.c;
            var legal = TurnManager.getLegalMovesFromPairings(state, pa, pb, pc);

            double childValue;
            if (legal.isEmpty()) {
                GameState bust = state.copy();
                TurnManager.bust(bust);
                childValue = expectiminimax(bust, depth - 1, !maximize);
            } else {
                double best = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
                for (Move m : orderMoves(state, maybeKiller(state), legal)) {
                    GameState child = state.copy();
                    TurnManager.applyMove(child, m);
                    double v = expectiminimax(child, depth - 1, !maximize);
                    if (maximize) {
                        if (v > best) best = v;
                    } else {
                        if (v < best) best = v;
                    }
                }
                childValue = best;
            }
            weighted += childValue * e.frequency;
            total += e.frequency;
        }

        // store chance node value in TT
        long key = StateHasher.hash(state);
        tt.put(key, new TTEntry(weighted / total, depth, NODE_CHANCE, (byte) 0, ttEpoch));
        return weighted / total;
    }

    private Move maybeKiller(GameState s) {
        return killer.get(StateHasher.hash(s));
    }

    private void rememberBest(GameState s, Move best) {
        if (best != null) killer.put(StateHasher.hash(s), best);
    }

    private List<Move> orderMoves(GameState state, Move killerMove, List<Move> moves) {
        moves.sort((m1, m2) -> Double.compare(scoreMove(state, m2), scoreMove(state, m1)));
        if (killerMove != null) {
            int i = moves.indexOf(killerMove);
            if (i > 0) {
                Move k = moves.remove(i);
                moves.add(0, k);
            }
        }
        return moves;
    }

    private double scoreMove(GameState s, Move m) {
        // Fast-and-dirty lookahead-free score
        int meLocksBefore = countLocks(s, s.getCurrentPlayer());
        int meNearBefore = nearWinCount(s, s.getCurrentPlayer());

        GameState tmp = s.copy();
        TurnManager.applyMove(tmp, m);

        int meLocksAfter = countLocks(tmp, s.getCurrentPlayer());
        int meNearAfter = nearWinCount(tmp, s.getCurrentPlayer());

        int completesThird = (meLocksAfter >= GameConstants.TO_WIN) ? 1 : 0;
        int deltaLocks = meLocksAfter - meLocksBefore;
        int deltaNear = meNearAfter - meNearBefore;

        // discourage spawning unnecessary new runners if you already have many active
        int activeBefore = s.countActiveColumns();
        int activeAfter = tmp.countActiveColumns();
        int extraRunners = Math.max(0, activeAfter - activeBefore);

        return 1000 * completesThird + 200 * deltaLocks + 80 * deltaNear - 5 * extraRunners;
    }


    // ---------------------------
    // Heuristic
    // ---------------------------

    // Perspective: eval > 0 means it's good for "player".
    // Combines progress to completion, tempo, and positional threats/blocks.
    private double heuristic(GameState state, Player player) {
        Player opp = player.opponent();

        // Sum of progress towards completion across columns
        double myProgress = 0.0;
        double oppProgress = 0.0;

        // Tempo: number of active temp runners (favor some activity)
        int myActive = 0, oppActive = 0; // active temp runners belong to the player whose turn it is
        // Note: temp runners always belong to the player who is currently moving (state.getCurrentPlayer()).
        // In heuristic we compute from perspective of 'player', but temp runners are always for current player.
        // To simplify, we only add tempo when player == current player.
        Player current = state.getCurrentPlayer();

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);

            int myPerm = (player == Player.RED) ? state.redPermAtCol(col) : state.bluePermAtCol(col);
            int oppPerm = (opp == Player.RED) ? state.redPermAtCol(col) : state.bluePermAtCol(col);

            myProgress += ((double) myPerm) / maxH;
            oppProgress += ((double) oppPerm) / maxH;

            int temp = state.tempAtCol(col);
            if (current == player && temp > 0) myActive++;
            if (current == opp && temp > 0) oppActive++;
        }

        // Positional terms
        double progressTerm = 0.6 * (myProgress - oppProgress);
        double tempoTerm = 0.08 * (myActive - oppActive);

        // Strongly prefer near-wins
        double winProximityTerm = 0.0;
        int nearWins = nearWinCount(state, player);
        int oppNearWins = nearWinCount(state, opp);
        winProximityTerm += 0.3 * nearWins - 0.3 * oppNearWins;

        // Slight penalty if opponent has many locked columns and you have none
        int myLocks = countLocks(state, player);
        int oppLocks = countLocks(state, opp);
        double lockTerm = 0.1 * (myLocks - oppLocks);

        // Small noise to break ties deterministically
        double noise = rng.nextDouble() * 1e-6;

        return progressTerm + tempoTerm + winProximityTerm + lockTerm + noise;
    }

    private int nearWinCount(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);
            int h = state.getMarkerHeight(p, col);
            if (h == maxH - 1) c++;
        }
        return c;
    }

    private int countLocks(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxH = GameConstants.maxHeight(sum);
            if (state.getMarkerHeight(p, col) == maxH) c++;
        }
        return c;
    }

    // ---------------------------
    // Actions returned by the AI
    // ---------------------------

    public sealed interface Action permits StopAction, RollAction {
    }

    public enum StopAction implements Action {
        INSTANCE
    }

    public static final class RollAction implements Action {
        private final Move move;

        public RollAction(Move move) {
            this.move = Objects.requireNonNull(move);
        }

        public Move move() {
            return move;
        }

        @Override
        public String toString() {
            return "RollAction{" + move + "}";
        }
    }
}
