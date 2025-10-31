package io.github.cantstop.backend;

import java.util.*;

/**
 * AI for Can't Stop using expectiminimax with heuristic evaluation.
 * - If a dice roll is provided, it evaluates STOP vs ROLL+Move.
 * - ROLL branch is a chance node over all 1296 possible dice outcomes (weighted equally).
 * - If no dice roll is provided, it simulates a ROLL (using the provided RNG) and then evaluates ROLL+Move.
 *
 * The AI returns either:
 *   - StopAction (commit current temp runners)
 *   - RollAction(Move m) (apply a legal move for the given dice roll)
 */
public final class AIPlayer {

    // Small epsilon to break evaluation ties
    private static final double EPS = 1e-9;

    // Depth defaults; you can tune these
    public static final int DEFAULT_DEPTH_ROLL_PHASE = 4; // expectiminimax depth for ROLL (chance) nodes
    public static final int DEFAULT_DEPTH_AFTER_STOP = 5; // deterministic depth after STOP

    private final Random rng;

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
        if (diceRoll == null) {
            diceRoll = DiceRoll.roll(rng);
        }

        // Evaluate STOP now
        double stopScore = evalStop(state, stopDepth);

        // Evaluate ROLL + best Move via expectiminimax
        double rollScore = evalRoll(state, diceRoll, rollDepth);

        if (stopScore + EPS >= rollScore) {
            return StopAction.INSTANCE;
        } else {
            Move bestMove = bestMoveForRoll(state, diceRoll, rollDepth);
            return new RollAction(bestMove);
        }
    }

    // ---------------------------
    // Evaluation helpers
    // ---------------------------

    // Evaluate STOP action from this state (deterministic)
    private double evalStop(GameState state, int depth) {
        // Simulate STOP
        GameState next = state.copy();
        TurnManager.stop(next);
        double val = expectiminimax(next, depth, true); // maximize from next player

        // Victory bonus/penalty at this node
        Player me = state.getCurrentPlayer();
        if (TurnManager.checkWinCondition(next, me)) return 1_000_000 + val;
        if (TurnManager.checkWinCondition(next, me.opponent())) return -1_000_000 + val;
        return val;
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
        if (best == bestScore) {
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

        if (depth <= 0) {
            // Heuristic evaluation from the perspective of the player to move
            return heuristic(state, current);
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
        return Math.max(stopScore, rollScore);
    }

    private double expectedValueRoll(GameState state, int depth, boolean maximize) {
        // Chance node: average over all 6^4 dice outcomes
        // For performance, if depth is small we can still enumerate all 1296 outcomes.
        double sum = 0.0;
        int count = 0;

        // Enumerate all dice outcomes
        // dice indexes: 0..3
        for (int d0 = 1; d0 <= 6; d0++) {
            for (int d1 = 1; d1 <= 6; d1++) {
                for (int d2 = 1; d2 <= 6; d2++) {
                    for (int d3 = 1; d3 <= 6; d3++) {
                        DiceRoll roll = new DiceRoll(d0, d1, d2, d3);
                        List<Move> legal = TurnManager.getLegalMoves(state, roll);
                        if (legal.isEmpty()) {
                            // Bust: switch player without committing temp runners
                            GameState bust = state.copy();
                            TurnManager.bust(bust);
                            double v = expectiminimax(bust, depth - 1, !maximize);
                            sum += v;
                            count++;
                            continue;
                        }

                        // For each legal move, take the best (max or min) and then average
                        double best = (maximize ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
                        for (Move m : legal) {
                            GameState child = state.copy();
                            TurnManager.applyMove(child, m);
                            double v = expectiminimax(child, depth - 1, !maximize);
                            if (maximize) {
                                if (v > best) best = v;
                            } else {
                                if (v < best) best = v;
                            }
                        }
                        sum += best;
                        count++;
                    }
                }
            }
        }

        return sum / Math.max(1, count);
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
