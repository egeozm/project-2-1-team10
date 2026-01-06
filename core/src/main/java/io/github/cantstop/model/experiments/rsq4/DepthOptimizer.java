package io.github.cantstop.model.experiments.rsq4;

import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.Player;
import io.github.cantstop.model.TurnManager;

import java.util.*;

/**
 * Tool to systematically find optimal depth parameters for ExpectiminimaxPlayer.
 *
 * Usage:
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer [gamesPerConfig] [minRollDepth] [maxRollDepth] [minStopDepth] [maxStopDepth] [seed] [timeBudgetMs] [quickMode]
 *
 * Examples:
 *   # Quick test: 5 games each, depths 2-4 for roll, 3-5 for stop (recommended for initial testing)
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 5 2 4 3 5
 *
 *   # Quick mode: skip problematic configs automatically
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 5 2 4 3 5 42 0 1
 *
 *   # Time-based search (often faster): 50ms per move
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 5 2 4 3 5 42 50
 *
 *   # More comprehensive: 10 games each
 *   java io.github.cantstop.backend.Simulations.DepthOptimizer 10 2 5 3 6
 */
public final class DepthOptimizer {

    public static void main(String[] args) {
        int gamesPerConfig = argOr(args, 0, 5); // Reduced default from 20 to 5 for faster testing
        int minRollDepth = argOr(args, 1, 2); // Start from 2, depth 1 is often problematic
        int maxRollDepth = argOr(args, 2, 4);
        int minStopDepth = argOr(args, 3, 3); // Start from 3, depth 1-2 are often problematic
        int maxStopDepth = argOr(args, 4, 5);
        long seed = argOr(args, 5, System.nanoTime());
        int timeBudgetMs = argOr(args, 6, 0); // 0 = use fixed depth, >0 = use timed search
        boolean quickMode = argOr(args, 7, 0) == 1; // Quick mode: fewer games, skip problematic configs

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
        if (quickMode) {
            System.out.println("QUICK MODE: Skipping problematic configurations");
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

                // Skip problematic configs in quick mode
                if (quickMode && result.avgMoves >= 290) {
                    System.out.printf("SKIPPED (hits limit)%n");
                    continue;
                }

                results.add(result);

                String status = result.avgMoves >= 290 ? " (HIT LIMIT)" : "";
                System.out.printf("Win rate: %.1f%% (avg moves: %.1f, avg time: %.1f ms)%s%n",
                    result.winRate * 100, result.avgMoves, result.avgTimeMs, status);
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
        int gamesThatHitLimit = 0;
        final int MAX_ACTIONS_PER_GAME = 300; // Early termination for testing

        for (int g = 0; g < games; g++) {
            long gameStartNs = System.nanoTime();

            // Create two AIs with same configuration but different seeds
            ExpectiminimaxPlayer redAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0x9E3779B97F4A7C15L)));
            ExpectiminimaxPlayer blueAI = new ExpectiminimaxPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)));

            GameState state = GameState.initialize(Player.RED);
            int moves = playGame(state, redAI, blueAI, rollDepth, stopDepth, timeBudgetMs, MAX_ACTIONS_PER_GAME);

            if (moves >= MAX_ACTIONS_PER_GAME) {
                gamesThatHitLimit++;
            }

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) wins++;

            totalMoves += moves;
            totalTimeNs += System.nanoTime() - gameStartNs;

            // Show progress for long-running tests
            if (games > 5 && (g + 1) % Math.max(1, games / 5) == 0) {
                System.out.print(".");
                System.out.flush();
            }
        }

        // If more than half the games hit the limit, this config is problematic
        double winRate = gamesThatHitLimit >= games / 2 ? 0.0 : (double) wins / games;

        return new DepthResult(
            rollDepth, stopDepth,
            winRate,
            (double) totalMoves / games,
            totalTimeNs / (games * 1_000_000.0) // convert to ms
        );
    }

    private static int playGame(GameState state, ExpectiminimaxPlayer redAI, ExpectiminimaxPlayer blueAI,
                                 int rollDepth, int stopDepth, int timeBudgetMs, int maxActions) {
        int actions = 0;

        while (!TurnManager.checkWinCondition(state, Player.RED) &&
               !TurnManager.checkWinCondition(state, Player.BLUE) &&
               actions < maxActions) {

            Player current = state.getCurrentPlayer();
            ExpectiminimaxPlayer ai = (current == Player.RED) ? redAI : blueAI;

            boolean turnOver = false;
            while (!turnOver && actions < maxActions) {
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

