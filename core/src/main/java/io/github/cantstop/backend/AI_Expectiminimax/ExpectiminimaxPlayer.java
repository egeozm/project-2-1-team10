package io.github.cantstop.backend.AI_Expectiminimax;

import io.github.cantstop.backend.*;

import java.util.*;

/**
 * AI for Can't Stop using expectiminimax with heuristic evaluation.
 * - If a dice roll is provided, it evaluates STOP vs ROLL+Move.
 * - ROLL branch is a chance node over all 6^4 = 1296 ordered dice outcomes,
 * but we iterate over symmetry-buckets (RollBucketer.ENTRIES), each weighted
 * by how many of the 1296 rolls map to that bucket.
 * - If no dice roll is provided, it simulates a ROLL (using the provided RNG) and then evaluates ROLL+Move.
 * <p>
 * The AI returns either:
 * - StopAction (commit current temp runners)
 * - RollAction(Move m) (apply a legal move for the given dice roll)
 */
public final class ExpectiminimaxPlayer {

    // Small epsilon to break evaluation ties
    private static final double EPS = 1e-9;

    // Depth defaults; you can tune these
    public static final int DEFAULT_DEPTH_ROLL_PHASE = 3; // expectiminimax depth for ROLL (chance) nodes
    public static final int DEFAULT_DEPTH_AFTER_STOP = 4; // deterministic depth after STOP

    private final Random rng;

    // TT + node types
    private final TranspositionTable tt = new TranspositionTable(20); // ~1M slots
    private int ttEpoch = 0;
    private static final byte NODE_MAX = 0, NODE_MIN = 1, NODE_CHANCE = 2;

    // killer move tiny cache (optional but helpful)
    private final Map<Long, Move> killer = new HashMap<>();

    // Heuristic weights (consistent across evaluation)
    private static final double W_PROGRESS = 0.6;
    private static final double W_TEMPO = 0.08;
    private static final double W_NEARWIN = 0.3;
    private static final double W_LOCKS = 0.1;

    // Calculate maximum possible heuristic value
    // Max progress: all columns at max height = NUM_COLS
    // Max tempo: MAX_TEMP_RUNNERS active runners
    // Max near wins: TO_WIN (3)
    // Max locks: TO_WIN (3)
    // Maximum heuristic difference (one player has everything, opponent has nothing):
    private static final double MAX_HEURISTIC_VALUE = 
        W_PROGRESS * GameConstants.NUM_COLS +  // max progress difference
        W_TEMPO * GameConstants.MAX_TEMP_RUNNERS +  // max tempo difference
        W_NEARWIN * GameConstants.TO_WIN +  // max near win difference
        W_LOCKS * GameConstants.TO_WIN;  // max lock difference

    // Win value should be significantly larger than any possible heuristic
    // Using 100x the maximum heuristic ensures wins are always preferred
    private static final double WIN_VALUE_MULTIPLIER = 100.0;
    private static final double WIN_VALUE = MAX_HEURISTIC_VALUE * WIN_VALUE_MULTIPLIER;
    private static final double LOSS_VALUE = -WIN_VALUE;

    // Bust penalty: calculated relative to maximum heuristic
    // Original value was 80.0, which is approximately 10x the max heuristic
    // This ensures bust penalty is significant but not overwhelming
    private static final double BUST_PENALTY_MULTIPLIER = 10.0;
    private static final double LAMBDA_BUST = MAX_HEURISTIC_VALUE * BUST_PENALTY_MULTIPLIER;

    // Move scoring weights: derived from heuristic weights for consistency
    // These should be proportional to the heuristic weights
    private static final double MOVE_SCORE_WIN = WIN_VALUE / 10.0; // Immediate win bonus
    private static final double MOVE_SCORE_LOCK = W_LOCKS * 100.0; // Proportional to lock weight
    private static final double MOVE_SCORE_NEAR = W_NEARWIN * 100.0; // Proportional to near-win weight
    private static final double MOVE_SCORE_RUNNER_PENALTY = 5.0; // Small penalty for extra runners

    // optional: deadline for timed search
    private long searchDeadlineNanos = Long.MAX_VALUE;

    public ExpectiminimaxPlayer() {
        this(new Random());
    }

    public ExpectiminimaxPlayer(Random rng) {
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
            // We already rolled: according to rules, we MUST play this roll (or bust),
            // we are NOT allowed to choose STOP here.

            List<Move> legal = TurnManager.getLegalMoves(state, diceRoll);
            if (legal.isEmpty()) {
                // No legal move → bust. The engine should handle bust when it sees there
                // are no moves for this roll; we just signal "end of turn".
                return StopAction.INSTANCE;
            }

            Move bestMove = bestMoveForRoll(state, diceRoll, rollDepth);
            return new RollAction(bestMove);
        } else {
            // No roll yet: decide between STOP and the expected value of ROLL.
            double stopScore = evalStop(state, stopDepth);
            double rollScore = expectedValueRoll(state, rollDepth, true); // current player is maximizing

            if (stopScore + EPS >= rollScore) {
                return StopAction.INSTANCE;
            }

            // We chose to roll; now actually roll and pick the move for that real outcome
            DiceRoll realRoll = DiceRoll.roll(rng);
            List<Move> legal = TurnManager.getLegalMoves(state, realRoll);
            if (legal.isEmpty()) {
                // instant bust on real roll → end turn
                return StopAction.INSTANCE;
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

        Player me = state.getCurrentPlayer();
        if (TurnManager.checkWinCondition(next, me)) return WIN_VALUE;
        if (TurnManager.checkWinCondition(next, me.opponent())) return LOSS_VALUE;

        return expectiminimax(next, depth - 1, /*maximize=*/false);
    }


    public Action chooseActionWithTime(GameState state, DiceRoll diceRoll, long millis) {
        return chooseActionWithTime(state, diceRoll, millis, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Time-limited iterative deepening with optional maximum depth limits.
     * @param state Current game state
     * @param diceRoll Current dice roll (null if not rolled yet)
     * @param millis Time budget in milliseconds
     * @param maxRollDepth Maximum roll depth to search (Integer.MAX_VALUE for no limit)
     * @param maxStopDepth Maximum stop depth to search (Integer.MAX_VALUE for no limit)
     * @return Best action found within time budget
     */
    public Action chooseActionWithTime(GameState state, DiceRoll diceRoll, long millis, int maxRollDepth, int maxStopDepth) {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        this.searchDeadlineNanos = deadline;
        Action best = null;
        int maxDepth = Math.max(maxRollDepth, maxStopDepth - 1);
        for (int d = 1; ; d++) {
            // Respect max depth limits
            if (d > maxDepth) {
                return best != null ? best : chooseAction(state, diceRoll, maxRollDepth, maxStopDepth);
            }
            ttEpoch++;
            int rollDepth = Math.min(d, maxRollDepth);
            int stopDepth = Math.min(d + 1, maxStopDepth);
            Action a = chooseAction(state, diceRoll, rollDepth, stopDepth);
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
            GameState bust = state.copy();
            TurnManager.bust(bust);
            // After bust, turn switches to opponent, so flip maximize
            double val = expectiminimax(bust, depth - 1, /*maximize=*/false);
            Player me = state.getCurrentPlayer();
            // After bust, current player is the opponent, so check win conditions correctly
            if (TurnManager.checkWinCondition(bust, me.opponent())) return WIN_VALUE + val;
            if (TurnManager.checkWinCondition(bust, me)) return LOSS_VALUE + val;
            return val;
        }

        double best = Double.NEGATIVE_INFINITY;
        for (Move m : legal) {
            GameState child = state.copy();
            TurnManager.applyMove(child, m);
            // After applyMove, same player continues, so keep maximize the same (true)
            double v = expectiminimax(child, depth - 1, /*maximize=*/true);
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
            // After applyMove, same player continues, so keep maximize the same (true)
            double v = expectiminimax(child, depth - 1, /*maximize=*/true);
            if (v > bestScore - EPS) {
                bestScore = v;
                best = m;
            }
        }

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
        return expectiminimax(state, depth, maximize,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

    }

    // Core expectiminimax with alpha-beta pruning on decision nodes
    private double expectiminimax(GameState state,
                                  int depth,
                                  boolean maximize,
                                  double alpha,
                                  double beta) {

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

        Player current = state.getCurrentPlayer();
        // maximize=true means current player is the root (maximizing) player
        // maximize=false means current player is the opponent (minimizing) player
        if (redWin && !blueWin) {
            // Red wins
            if (maximize) {
                return (red == current) ? WIN_VALUE : LOSS_VALUE;
            } else {
                return (red == current) ? LOSS_VALUE : WIN_VALUE;
            }
        }
        if (blueWin && !redWin) {
            // Blue wins
            if (maximize) {
                return (blue == current) ? WIN_VALUE : LOSS_VALUE;
            } else {
                return (blue == current) ? LOSS_VALUE : WIN_VALUE;
            }
        }
        if (redWin && blueWin) return 0.0;

        if (depth <= 0) {
            // Heuristic should be from the perspective of the root player
            // If maximize=true, current player is root, so evaluate from current player's perspective
            // If maximize=false, opponent is root, so negate the evaluation from current player's perspective
            double h = heuristicWithRisk(state);
            return maximize ? h : -h;
        }

        double result;

        if (maximize) {
            // MAX node: choose between STOP and ROLL, prune with alpha-beta
            double best = Double.NEGATIVE_INFINITY;

            // 1) STOP branch
            {
                GameState next = state.copy();
                TurnManager.stop(next);
                double stopScore = expectiminimax(next, depth - 1, /*maximize=*/false, alpha, beta);
                best = Math.max(best, stopScore);
                alpha = Math.max(alpha, best);
                if (alpha >= beta) {
                    result = best;
                    tt.put(key, new TTEntry(result, depth, nodeType, (byte) 0, ttEpoch));
                    return result;
                }
            }

            // 2) ROLL branch (chance over dice)
            double rollScore = expectedValueRoll(state, depth, /*maximize=*/true, alpha, beta);
            best = Math.max(best, rollScore);

            result = best;
        } else {
            // MIN node
            double best = Double.POSITIVE_INFINITY;

            // 1) STOP branch
            {
                GameState next = state.copy();
                TurnManager.stop(next);
                double stopScore = expectiminimax(next, depth - 1, /*maximize=*/true, alpha, beta);
                best = Math.min(best, stopScore);
                beta = Math.min(beta, best);
                if (alpha >= beta) {
                    result = best;
                    tt.put(key, new TTEntry(result, depth, nodeType, (byte) 0, ttEpoch));
                    return result;
                }
            }

            // 2) ROLL branch
            double rollScore = expectedValueRoll(state, depth, /*maximize=*/false, alpha, beta);
            best = Math.min(best, rollScore);

            result = best;
        }

        tt.put(key, new TTEntry(result, depth, nodeType, (byte) 0, ttEpoch));
        return result;
    }

    private double heuristicWithRisk(GameState s) {
        // Evaluate from current player's perspective
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
        return expectedValueRoll(state, depth, maximize,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
    }

    private double expectedValueRoll(GameState state,
                                     int depth,
                                     boolean maximize,
                                     double alpha,
                                     double beta) {
        if (depth <= 0 || TurnManager.checkWinCondition(state, Player.RED)
            || TurnManager.checkWinCondition(state, Player.BLUE)) {
            // Heuristic should be from the perspective of the root player
            double h = heuristicWithRisk(state);
            return maximize ? h : -h;
        }

        double weighted = 0.0;
        int total = 0;

        for (var e : RollBucketer.ENTRIES) {
            // Time guard
            if ((total & 63) == 0 && System.nanoTime() >= searchDeadlineNanos) {
                // Heuristic should be from the perspective of the root player
                double h = heuristicWithRisk(state);
                return maximize ? h : -h;
            }

            int pa = e.bucket.a, pb = e.bucket.b, pc = e.bucket.c;
            var legal = TurnManager.getLegalMovesFromPairings(state, pa, pb, pc);

            double childValue;
            if (legal.isEmpty()) {
                GameState bust = state.copy();
                TurnManager.bust(bust);
                // After bust, turn switches to opponent, so flip maximize
                childValue = expectiminimax(bust, depth - 1, !maximize, alpha, beta);
            } else {
                double best = maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

                for (Move m : orderMoves(state, maybeKiller(state), legal)) {
                    GameState child = state.copy();
                    TurnManager.applyMove(child, m);
                    // After applyMove, same player continues, so keep maximize the same
                    double v = expectiminimax(child, depth - 1, maximize, alpha, beta);

                    if (maximize) {
                        if (v > best) best = v;
                        alpha = Math.max(alpha, best);
                    } else {
                        if (v < best) best = v;
                        beta = Math.min(beta, best);
                    }
                    if (alpha >= beta) break; // alpha-beta pruning
                }
                childValue = best;
            }

            weighted += childValue * e.frequency;
            total += e.frequency;
        }

        double value = weighted / total;

        long key = StateHasher.hash(state);
        tt.put(key, new TTEntry(value, depth, NODE_CHANCE, (byte) 0, ttEpoch));
        return value;
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

        // Use consistent weights derived from heuristic weights
        return MOVE_SCORE_WIN * completesThird + 
               MOVE_SCORE_LOCK * deltaLocks + 
               MOVE_SCORE_NEAR * deltaNear - 
               MOVE_SCORE_RUNNER_PENALTY * extraRunners;
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

        // Positional terms (using consistent weights)
        double progressTerm = W_PROGRESS * (myProgress - oppProgress);
        double tempoTerm = W_TEMPO * (myActive - oppActive);

        // Strongly prefer near-wins
        double winProximityTerm = 0.0;
        int nearWins = nearWinCount(state, player);
        int oppNearWins = nearWinCount(state, opp);
        winProximityTerm += W_NEARWIN * nearWins - W_NEARWIN * oppNearWins;

        // Slight penalty if opponent has many locked columns and you have none
        int myLocks = countLocks(state, player);
        int oppLocks = countLocks(state, opp);
        double lockTerm = W_LOCKS * (myLocks - oppLocks);

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
