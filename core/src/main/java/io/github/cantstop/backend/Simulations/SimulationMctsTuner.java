package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.AI_MCTS.MctsConfig;
import io.github.cantstop.backend.MatchHistory.AgentSpec;
import io.github.cantstop.backend.MatchHistory.MatchResult;
import io.github.cantstop.backend.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SimulationMctsTuner {

    // Base seed for reproducibility
    private static final long BASE_SEED = 12345L;

    // For overnight / fast debugging set to 1; for stability set to 3
    private static final int RUNS = 1;

    public static void main(String[] args) {

        for (int run = 1; run <= RUNS; run++) {
            long seed = BASE_SEED + run * 9999L;
            MctsConfig best = runTournament(seed);
            System.out.println("\nRun " + run + " best: " + best);
        }
    }

    private static MctsConfig runTournament(long seed) {

        List<MctsConfig> configs = makeConfigGrid();

        // Simple knockout tournament
        List<MctsConfig> pool = new ArrayList<>(configs);
        int round = 1;

        while (pool.size() > 1) {

            // --- staged (fast -> accurate) ---
            int gamesThisRound = switch (round) {
                case 1 -> 60;    // screening
                case 2 -> 120;   // mid
                default -> 300;  // quick final; later you can do 1000 only for top2
            };

            long timeThisRoundMs = switch (round) {
                case 1 -> 50L;   // screening budget
                case 2 -> 100L;  // mid budget
                default -> 200L; // paper-like budget
            };

            System.out.println("\n=== TOURNAMENT ROUND " + round +
                " | configs=" + pool.size() +
                " | gamesPerMatchup=" + gamesThisRound +
                " | timeBudgetMs=" + timeThisRoundMs +
                " | seed=" + seed + " ===");

            List<MctsConfig> next = new ArrayList<>();
            for (int i = 0; i < pool.size(); i += 2) {
                if (i + 1 >= pool.size()) {
                    next.add(pool.get(i));
                    break;
                }

                MctsConfig a = pool.get(i);
                MctsConfig b = pool.get(i + 1);

                double aWinRate = playMatchup(a, b, gamesThisRound, seed, timeThisRoundMs);
                MctsConfig winner = (aWinRate >= 0.5) ? a : b;

                System.out.println("Match: " + a + " vs " + b +
                    " | aWinRate=" + String.format("%.3f", aWinRate) +
                    " | winner=" + winner);

                next.add(winner);
            }

            pool = next;
            round++;
        }

        System.out.println("\n=== BEST CONFIG (seed=" + seed + ") ===");
        System.out.println(pool.get(0));
        return pool.get(0);
    }

    private static List<MctsConfig> makeConfigGrid() {
        List<MctsConfig> out = new ArrayList<>();

        // Keep these in config for reporting; actual per-round budget is overridden in runTournament
        long timeBudgetMs = 200;
        int maxIterations = 1_000_000; // safety cap (time budget is the real limit)

        // Paper-like small grid (18 configs)
        int[] drs = {10};
        double[] cs = {0.15, 0.25, 0.35};
        double[] dpwKs = {16.0, 25.0, 36.0};
        double[] dpwAlphas = {0.3, 0.5};

        for (int dr : drs) {
            for (double c : cs) {
                for (double k : dpwKs) {
                    for (double a : dpwAlphas) {
                        out.add(new MctsConfig(maxIterations, timeBudgetMs, dr, c, k, a));
                    }
                }
            }
        }

        // Shuffle so the bracket isn't biased by grid ordering
        out.sort(Comparator.comparingInt(Object::hashCode));
        return out;
    }

    private static double playMatchup(MctsConfig a, MctsConfig b, int gamesTotal, long baseSeed, long timeBudgetMs) {
        Random rng = new Random(baseSeed ^ a.hashCode() ^ b.hashCode() ^ gamesTotal ^ timeBudgetMs);

        int aWins = 0;
        int half = gamesTotal / 2;

        // Half: A as RED
        aWins += playBatchGames(a, b, half, rng, true, timeBudgetMs);

        // Half: A as BLUE
        aWins += playBatchGames(a, b, gamesTotal - half, rng, false, timeBudgetMs);

        return (double) aWins / gamesTotal;
    }

    /**
     * Plays `games` games in ONE call to SimulationTerminal.playMatchup(...).
     * This avoids huge overhead of calling playMatchup(..., 1, ...) many times.
     */
    private static int playBatchGames(MctsConfig a, MctsConfig b, int games, Random rng, boolean aIsRed, long timeBudgetMs) {
        if (games <= 0) return 0;

        long matchSeed = rng.nextLong();

        AgentSpec red = aIsRed
            ? specFrom(Player.RED, a, matchSeed, timeBudgetMs)
            : specFrom(Player.RED, b, matchSeed, timeBudgetMs);

        AgentSpec blue = aIsRed
            ? specFrom(Player.BLUE, b, matchSeed, timeBudgetMs)
            : specFrom(Player.BLUE, a, matchSeed, timeBudgetMs);

        // play `games` games at once
        MatchResult result = SimulationTerminal.playMatchup(red, blue, games, matchSeed, false);

        // Count A wins depending on which side A was on
        return aIsRed ? result.redWins() : result.blueWins();
    }

    private static AgentSpec specFrom(Player player, MctsConfig cfg, long seed, long timeBudgetMs) {
        // NOTE: this requires AgentSpec.mcts(..., timeBudgetMs) to exist
        return AgentSpec.mcts(
            player,
            seed,
            cfg.maxIterations(),
            cfg.c(),
            cfg.rolloutMaxRolls(),
            cfg.dpwK(),
            cfg.dpwAlpha(),
            timeBudgetMs
        );
    }
}
