package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.MatchHistory.AgentSpec;
import io.github.cantstop.backend.MatchHistory.AgentType;
import io.github.cantstop.backend.MatchHistory.MatchResult;
import io.github.cantstop.backend.MatchHistory.MatchHistoryStorage;
import io.github.cantstop.backend.AI_MCTS.MctsConfig;
import io.github.cantstop.backend.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class SimulationMctsTuner {

    // Number of games per matchup (use 200 for quick, 1000 for paper-like)
    private static final int GAMES_PER_MATCHUP = 200;

    // Seed for reproducibility
    private static final long SEED = 12345L;

    public static void main(String[] args) {

        List<MctsConfig> configs = makeConfigGrid();

        // Simple knockout tournament
        List<MctsConfig> pool = new ArrayList<>(configs);
        int round = 1;

        while (pool.size() > 1) {
            System.out.println("\n=== TOURNAMENT ROUND " + round + " | configs=" + pool.size() + " ===");

            List<MctsConfig> next = new ArrayList<>();
            for (int i = 0; i < pool.size(); i += 2) {
                if (i + 1 >= pool.size()) {
                    next.add(pool.get(i));
                    break;
                }
                MctsConfig a = pool.get(i);
                MctsConfig b = pool.get(i + 1);

                double aWinRate = playMatchup(a, b);
                MctsConfig winner = (aWinRate >= 0.5) ? a : b;

                System.out.println("Match: " + a + " vs " + b + " | aWinRate=" + String.format("%.3f", aWinRate)
                    + " | winner=" + winner);

                next.add(winner);
            }

            pool = next;
            round++;
        }

        System.out.println("\n=== BEST CONFIG ===");
        System.out.println(pool.get(0));
    }

    private static List<MctsConfig> makeConfigGrid() {
        List<MctsConfig> out = new ArrayList<>();

        int iterations = 700;

        int[] drs = {4, 8};                 // 2
        double[] cs = {1.0, 1.414};         // 2
        double[] dpwKs = {2.0, 4.0};        // 2
        double[] dpwAlphas = {0.5, 0.7};    // 2



        for (int dr : drs) {
            for (double c : cs) {
                for (double k : dpwKs) {
                    for (double a : dpwAlphas) {
                        out.add(new MctsConfig(iterations, dr, c, k, a));
                    }
                }
            }
        }

        // Shuffle so the bracket isn't biased by grid ordering
        out.sort(Comparator.comparingInt(Object::hashCode));
        return out;
    }

    private static double playMatchup(MctsConfig a, MctsConfig b) {
        Random rng = new Random(SEED ^ a.hashCode() ^ b.hashCode());

        int aWins = 0;
        int total = GAMES_PER_MATCHUP;

        // Half games: A as RED, B as BLUE
        aWins += playGames(a, b, total / 2, rng, true);

        // Half games: swap sides (A as BLUE, B as RED)
        // We still count A wins, so when swapped we interpret result accordingly.
        aWins += playGames(a, b, total - total / 2, rng, false);

        return (double) aWins / total;
    }

    private static int playGames(MctsConfig a, MctsConfig b, int games, Random rng, boolean aIsRed) {
        int aWins = 0;

        for (int g = 0; g < games; g++) {
            long seed = rng.nextLong();

            AgentSpec red  = aIsRed ? specFrom(Player.RED, a, seed) : specFrom(Player.RED, b, seed);
            AgentSpec blue = aIsRed ? specFrom(Player.BLUE, b, seed) : specFrom(Player.BLUE, a, seed);


            // Use your existing match runner infrastructure:
            // This assumes you already have some "play one match" function used in demos.
            // If your project uses SimulationTerminal/SimulationDemo*, adapt this call to the same API.
            MatchResult result = SimulationTerminal.playMatchup(red, blue, 1, seed, false);


            boolean redWon = (result.redWins() > 0);
            boolean aWon = aIsRed ? redWon : !redWon;

            if (aWon) aWins++;
        }

        return aWins;
    }

    private static AgentSpec specFrom(Player player, MctsConfig cfg, long seed) {
        return AgentSpec.mcts(
            player,
            seed,
            cfg.iterations(),
            cfg.c(),
            cfg.rolloutMaxRolls(),
            cfg.dpwK(),
            cfg.dpwAlpha()
        );
    }



}
