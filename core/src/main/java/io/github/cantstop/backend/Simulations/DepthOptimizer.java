package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.Move;
import io.github.cantstop.backend.Player;
import io.github.cantstop.backend.TurnManager;

import java.util.*;

/**
 * Tool to systematically find optimal depth parameters for ExpectiminimaxPlayer.
 * 
 * Usage:
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer [gamesPerConfig] [minRollDepth] [maxRollDepth] [minStopDepth] [maxStopDepth] [seed] [timeBudgetMs]
 * 
 * Examples:
 *   # Test depths 2-5 for roll and 3-6 for stop, 20 games each, default time budget
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 20 2 5 3 6
 * 
 *   # Test depths 1-4 for both, 50 games each, 200ms per move
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 50 1 4 1 4 42 200
 */
public final class DepthOptimizer {

    public static void main(String[] args) {
        int gamesPerConfig = argOr(args, 0, 20);
        int minRollDepth = argOr(args, 1, 1);
        int maxRollDepth = argOr(args, 2, 5);
        int minStopDepth = argOr(args, 3, 1);
        int maxStopDepth = argOr(args, 4, 6);
        long seed = argOr(args, 5, System.nanoTime());
        int timeBudgetMs = argOr(args, 6, 0); // 0 = use fixed depth, >0 = use timed search

        System.out.println("=======================================");
        System.out.println("  Expectiminimax Depth Optimization   ");
        System.out.println("=======================================");
        System.out.printf("Games per configuration: %d%n", gamesPerConfig);
        System.out.printf("Roll depth range: %d-%d%n", minRollDepth, maxRollDepth);
        System.out.printf("Stop depth range: %d-%d%n", minStopDepth, maxStopDepth);
        System.out.printf("Seed: %d%n", seed);
        if (timeBudgetMs > 0) {
            System.out.printf("Time budget: %d ms per move (iterative deepening)%n", timeBudgetMs);
        } else {
            System.out.println("Using fixed depth search");
        }
        System.out.println("=======================================\n");

        List<DepthResult> results = new ArrayList<>();

        // Test all combinations
        int totalConfigs = (maxRollDepth - minRollDepth + 1) * (maxStopDepth - minStopDepth + 1);
        int configNum = 0;

        for (int rollDepth = minRollDepth; rollDepth <= maxRollDepth; rollDepth++) {
            for (int stopDepth = minStopDepth; stopDepth <= maxStopDepth; stopDepth++) {
                configNum++;
                System.out.printf("[%d/%d] Testing rollDepth=%d, stopDepth=%d... ", 
                    configNum, totalConfigs, rollDepth, stopDepth);
                System.out.flush();

                DepthResult result = testConfiguration(rollDepth, stopDepth, gamesPerConfig, seed, timeBudgetMs);
                results.add(result);

                System.out.printf("Win rate: %.1f%% (avg moves: %.1f, avg time: %.1f ms)%n",
                    result.winRate * 100, result.avgMoves, result.avgTimeMs);
            }
        }

        // Sort by win rate (descending)
        results.sort((a, b) -> Double.compare(b.winRate, a.winRate));

        // Print summary
        System.out.println("\n=======================================");
        System.out.println("           OPTIMIZATION RESULTS         ");
        System.out.println("=======================================");
        System.out.printf("%-6s %-6s %-10s %-12s %-12s%n", "Roll", "Stop", "Win Rate", "Avg Moves", "Avg Time");
        System.out.println("---------------------------------------");

        for (DepthResult r : results) {
            System.out.printf("%-6d %-6d %-10.1f%% %-12.1f %-12.1f ms%n",
                r.rollDepth, r.stopDepth, r.winRate * 100, r.avgMoves, r.avgTimeMs);
        }

        // Highlight top 3
        System.out.println("\n--- Top 3 Configurations ---");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            DepthResult r = results.get(i);
            System.out.printf("%d. rollDepth=%d, stopDepth=%d: %.1f%% win rate%n",
                i + 1, r.rollDepth, r.stopDepth, r.winRate * 100);
        }

        // Find best balance (high win rate, reasonable time)
        if (timeBudgetMs > 0) {
            DepthResult bestBalanced = findBestBalanced(results);
            System.out.printf("\n--- Best Balanced (performance/time) ---%n");
            System.out.printf("rollDepth=%d, stopDepth=%d: %.1f%% win rate, %.1f ms avg%n",
                bestBalanced.rollDepth, bestBalanced.stopDepth,
                bestBalanced.winRate * 100, bestBalanced.avgTimeMs);
        }
    }

    private static DepthResult testConfiguration(int rollDepth, int stopDepth, int games, long seed, int timeBudgetMs) {
        int wins = 0;
        long totalMoves = 0;
        long totalTimeNs = 0;

        for (int g = 0; g < games; g++) {
            long gameStartNs = System.nanoTime();

            // Create two AIs with same configuration but different seeds
            ExpectiminimaxPlayer redAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0x9E3779B97F4A7C15L)));
            ExpectiminimaxPlayer blueAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)));

            GameState state = GameState.initialize(Player.RED);
            int moves = playGame(state, redAI, blueAI, rollDepth, stopDepth, timeBudgetMs);

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) wins++;

            totalMoves += moves;
            totalTimeNs += System.nanoTime() - gameStartNs;
        }

        return new DepthResult(
            rollDepth, stopDepth,
            (double) wins / games,
            (double) totalMoves / games,
            totalTimeNs / (games * 1_000_000.0) // convert to ms
        );
    }

    private static int playGame(GameState state, ExpectiminimaxPlayer redAI, ExpectiminimaxPlayer blueAI,
                                 int rollDepth, int stopDepth, int timeBudgetMs) {
        int actions = 0;
        final int MAX_ACTIONS = 2000;

        while (!TurnManager.checkWinCondition(state, Player.RED) && 
               !TurnManager.checkWinCondition(state, Player.BLUE) && 
               actions < MAX_ACTIONS) {

            Player current = state.getCurrentPlayer();
            ExpectiminimaxPlayer ai = (current == Player.RED) ? redAI : blueAI;

            boolean turnOver = false;
            while (!turnOver && actions < MAX_ACTIONS) {
                ExpectiminimaxPlayer.Action action;
                if (timeBudgetMs > 0) {
                    action = ai.chooseActionWithTime(state, null, timeBudgetMs);
                } else {
                    action = ai.chooseAction(state, null, rollDepth, stopDepth);
                }
                actions++;

                if (action instanceof ExpectiminimaxPlayer.StopAction) {
                    TurnManager.stop(state);
                    turnOver = true;
                } else if (action instanceof ExpectiminimaxPlayer.RollAction) {
                    Move m = ((ExpectiminimaxPlayer.RollAction) action).move();
                    if (m != null) {
                        TurnManager.applyMove(state, m);
                        if (TurnManager.checkWinCondition(state, current)) {
                            return actions;
                        }
                    } else {
                        TurnManager.bust(state);
                        turnOver = true;
                    }
                } else {
                    TurnManager.stop(state);
                    turnOver = true;
                }
            }
        }

        return actions;
    }

    private static DepthResult findBestBalanced(List<DepthResult> results) {
        // Find configuration with best win rate that doesn't take too long
        // Prefer configurations with win rate > 50% and reasonable time
        DepthResult best = results.get(0);
        double bestScore = -1;

        for (DepthResult r : results) {
            // Score = win rate - penalty for excessive time
            // Penalty: if time > 2x average, reduce score
            double avgTime = results.stream().mapToDouble(x -> x.avgTimeMs).average().orElse(0);
            double timePenalty = r.avgTimeMs > 2 * avgTime ? (r.avgTimeMs - 2 * avgTime) / 1000.0 : 0;
            double score = r.winRate - timePenalty;

            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }

        return best;
    }

    private static class DepthResult {
        final int rollDepth;
        final int stopDepth;
        final double winRate;      // win rate for RED player (0.0 to 1.0)
        final double avgMoves;
        final double avgTimeMs;

        DepthResult(int rollDepth, int stopDepth, double winRate, double avgMoves, double avgTimeMs) {
            this.rollDepth = rollDepth;
            this.stopDepth = stopDepth;
            this.winRate = winRate;
            this.avgMoves = avgMoves;
            this.avgTimeMs = avgTimeMs;
        }
    }

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
}

