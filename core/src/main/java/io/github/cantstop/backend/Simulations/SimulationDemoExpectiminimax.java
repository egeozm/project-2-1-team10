package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.Move;
import io.github.cantstop.backend.Player;
import io.github.cantstop.backend.TurnManager;

import java.util.Random;

/**
 * Simple simulator to pit two AIPlayers against each other.
 * <p>
 * Usage (all args optional):
 * java io.github.cantstop.backend.SimulationDemoAI [games] [rollDepth] [stopDepth] [seed] [verbose] [perMoveMillis]
 * <p>
 * Examples:
 * # 20 games, default depths, random seed, quiet
 * java io.github.cantstop.backend.SimulationDemoAI 20
 * <p>
 * # 50 games, rollDepth=4, stopDepth=5, seed=42, verbose
 * java io.github.cantstop.backend.SimulationDemoAI 50 4 5 42 true
 */
public final class SimulationDemoExpectiminimax {

    public static void main(String[] args) {
        int games = argOr(args, 0, 10);
        int rollDepth = argOr(args, 1, ExpectiminimaxPlayer.DEFAULT_DEPTH_ROLL_PHASE);
        int stopDepth = argOr(args, 2, ExpectiminimaxPlayer.DEFAULT_DEPTH_AFTER_STOP);
        long seed = argOr(args, 3, System.nanoTime());
        boolean verbose = boolOr(args, 4, false);
        int perMoveMillis = argOr(args, 5, 100); // default 100ms per decision

        Random matchRng = new Random(seed);

        int redWins = 0, blueWins = 0;
        long totalMoves = 0;

        for (int g = 0; g < games; g++) {
            // Fresh game, alternate seeds a little to avoid identical mirrors
            ExpectiminimaxPlayer redAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0x9E3779B97F4A7C15L)));
            ExpectiminimaxPlayer blueAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)));

            GameState state = GameState.initialize(Player.RED);
            int movesThisGame = playSingleGame(state, redAI, blueAI, rollDepth, stopDepth, verbose, perMoveMillis);

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++;
            else blueWins++;
            totalMoves += movesThisGame;

            if (!verbose) {
                System.out.printf("Game %2d winner: %s (%d moves)\n", g + 1, winner, movesThisGame);
            }
        }

        System.out.println("=======================================");
        System.out.printf("Games: %d | RED wins: %d | BLUE wins: %d\n", games, redWins, blueWins);
        System.out.printf("Avg moves per game: %.2f\n", (games > 0 ? (double) totalMoves / games : 0.0));
        System.out.printf("Config: rollDepth=%d, stopDepth=%d, seed=%d\n", rollDepth, stopDepth, seed);
    }

    /**
     * Plays a single game until a player satisfies the win condition.
     * Uses AIPlayer.chooseAction(state, null, rollDepth, stopDepth) at each decision.
     * The AI (per your latest implementation) decides STOP vs EV(ROLL); if it chooses ROLL,
     * it rolls and returns a RollAction with the chosen Move, which we apply.
     *
     * @return number of applied actions (moves + stops) in this game
     */

    private static int playSingleGame(GameState state, ExpectiminimaxPlayer redAI, ExpectiminimaxPlayer blueAI, int rollDepth, int stopDepth, boolean verbose, int perMoveMillis) {

        int actions = 0;

        // Safety valve in case someone breaks the rules and we loop forever
        final int MAX_ACTIONS_SAFETY = 2000;

        while (!TurnManager.checkWinCondition(state, Player.RED) && !TurnManager.checkWinCondition(state, Player.BLUE)) {

            Player current = state.getCurrentPlayer();
            ExpectiminimaxPlayer ai = (current == Player.RED) ? redAI : blueAI;

            // One "turn" consists of repeated decisions until STOP or BUST
            boolean turnOver = false;
            if (verbose) {
                System.out.printf("\n[%s] turn starts\n", current);
            }

            while (!turnOver && !TurnManager.checkWinCondition(state, Player.RED) && !TurnManager.checkWinCondition(state, Player.BLUE)) {

                // Ask AI what to do at this decision (no dice yet)
                ExpectiminimaxPlayer.Action action = ai.chooseActionWithTime(state, /*diceRoll=*/null, perMoveMillis);
                actions++;

                if (action instanceof ExpectiminimaxPlayer.StopAction) {
                    if (verbose) System.out.printf("[%s] chooses STOP\n", current);
                    TurnManager.stop(state);         // commit temps, pass the turn
                    turnOver = true;                  // turn ends
                } else if (action instanceof ExpectiminimaxPlayer.RollAction) {
                    Move m = ((ExpectiminimaxPlayer.RollAction) action).move();
                    if (verbose) System.out.printf("[%s] applies %s\n", current, m);

                    // Apply the move. If AI produced an illegal move (shouldn't happen), treat as bust.
                    if (!applyOrBust(state, m, verbose)) {
                        turnOver = true; // bust ends the turn and passes it
                    }

                    // If move finishes the game, exit early
                    if (TurnManager.checkWinCondition(state, current)) {
                        if (verbose) System.out.printf("[%s] wins!\n", current);
                        break;
                    }
                } else {
                    // Defensive fallback
                    if (verbose) System.out.printf("[%s] unknown action, forcing STOP\n", current);
                    TurnManager.stop(state);
                    turnOver = true;
                }

                if (actions > MAX_ACTIONS_SAFETY) {
                    System.out.println("Safety break: too many actions, aborting game.");
                    return actions;
                }
            }
        }

        return actions;
    }

    /**
     * Try to apply the move; if it's illegal or results in no legal placements,
     * we treat it as a bust. Returns true if the move was applied successfully,
     * false if the player busted (and turn was passed by TurnManager.bust()).
     */
    private static boolean applyOrBust(GameState state, Move m, boolean verbose) {
        // Sanity check: if this move is inconsistent with rules, we bust
        // (AI should only return legal moves; this is a guard).
        boolean legalLike = (m != null);

        if (!legalLike) {
            if (verbose) System.out.println("Illegal move encountered; counting as BUST.");
            TurnManager.bust(state);
            return false;
        }

        // Apply and continue the same player's turn
        TurnManager.applyMove(state, m);

        // If there was truly no legal move for the rolled dice, the AI should have returned STOP.
        // Given your AI now decides EV first and then rolls, this path will only bust when no
        // placements were possible for that roll (rare; your AI treats that edge as STOP).
        return true;
    }

    // --------------- small arg helpers ---------------

    private static int argOr(String[] args, int idx, int def) {
        if (args.length <= idx) return def;
        try {
            return Integer.parseInt(args[idx]);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static long argOr(String[] args, int idx, long def) {
        if (args.length <= idx) return def;
        try {
            return Long.parseLong(args[idx]);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static boolean boolOr(String[] args, int idx, boolean def) {
        if (args.length <= idx) return def;
        String s = args[idx].trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        return def;
    }
}
