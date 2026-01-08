package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.Player;
import io.github.cantstop.model.match_history.AgentSpec;
import io.github.cantstop.model.match_history.MatchResult;


public final class SimulationBestVsBaselines {

    // === BEST CONFIG from your tuning (seed=22344) ===
    private static final int    MAX_ITERS   = 1_000_000; // safety cap
    private static final long   TIME_MS     = 200;       // paper-like
    private static final int    ROLLOUT_MAX = 10;        // dr
    private static final double C           = 0.35;
    private static final double DPW_K       = 25.0;
    private static final double DPW_ALPHA   = 0.5;

    // Evaluation settings
    private static final int  GAMES_PER_SIDE_DEFAULT = 300; // total games = 2 * this (swap seats)
    private static final long MATCH_SEED_DEFAULT     = 7777777L;
    private static final boolean VERBOSE_DEFAULT     = false;

    private SimulationBestVsBaselines() {}

    public static void main(String[] args) {

        int gamesPerSide = (args.length >= 1) ? Integer.parseInt(args[0]) : GAMES_PER_SIDE_DEFAULT;
        long matchSeed   = (args.length >= 2) ? Long.parseLong(args[1]) : MATCH_SEED_DEFAULT;
        boolean verbose  = (args.length >= 3) ? Boolean.parseBoolean(args[2]) : VERBOSE_DEFAULT;

        System.out.println("=== BEST MCTS vs RULE-BASED (swap seats) ===");
        System.out.println("BEST MCTS: itersCap=" + MAX_ITERS +
            ", timeMs=" + TIME_MS +
            ", rolloutMax=" + ROLLOUT_MAX +
            ", C=" + C +
            ", dpwK=" + DPW_K +
            ", dpwAlpha=" + DPW_ALPHA);
        System.out.println("gamesPerSide=" + gamesPerSide + " => totalGames=" + (2 * gamesPerSide));
        System.out.println("matchSeed=" + matchSeed);
        System.out.println();

        // 1) MCTS as RED vs RuleBased as BLUE
        AgentSpec red1  = mctsSpec(Player.RED,  1111L);
        AgentSpec blue1 = AgentSpec.ruleBased(Player.BLUE, 2222L);

        MatchResult r1 = SimulationTerminal.playMatchup(red1, blue1, gamesPerSide, matchSeed, verbose);

        // 2) Swap seats: RuleBased as RED vs MCTS as BLUE
        AgentSpec red2  = AgentSpec.ruleBased(Player.RED, 3333L);
        AgentSpec blue2 = mctsSpec(Player.BLUE, 4444L);

        MatchResult r2 = SimulationTerminal.playMatchup(red2, blue2, gamesPerSide, matchSeed, verbose);

        // MCTS wins:
        // - in r1 MCTS is RED -> redWins
        // - in r2 MCTS is BLUE -> blueWins
        int mctsWins = r1.redWins() + r2.blueWins();
        int totalGames = r1.totalGames() + r2.totalGames();

        double winRate = (totalGames > 0) ? ((double) mctsWins / totalGames) : 0.0;

        System.out.println("\n=== SUMMARY ===");
        System.out.println("Match 1 (MCTS as RED):  redWins=" + r1.redWins() + ", blueWins=" + r1.blueWins());
        System.out.println("Match 2 (MCTS as BLUE): redWins=" + r2.redWins() + ", blueWins=" + r2.blueWins());
        System.out.println("TOTAL: MCTS wins=" + mctsWins + " / " + totalGames +
            " => winRate=" + String.format("%.3f", winRate));

        System.out.println("\nTip: for a paper-grade number run e.g. 1000 per side (2000 total).");
    }

    private static AgentSpec mctsSpec(Player player, long seed) {
        // IMPORTANT:
        // Adjust ONLY THIS call if your AgentSpec.mcts(...) signature differs.
        // Expected: (player, seed, maxIterations, C, rolloutMaxRolls, dpwK, dpwAlpha, timeBudgetMs)
        return AgentSpec.mcts(
            player,
            seed,
            MAX_ITERS,
            C,
            ROLLOUT_MAX,
            DPW_K,
            DPW_ALPHA,
            TIME_MS
        );
    }
}
