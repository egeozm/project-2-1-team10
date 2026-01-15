package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.Hybrid_Model.HybridModel;
import io.github.cantstop.model.ai.RuleBasedPlayer;

import java.util.List;
import java.util.Random;

public final class SimulationDemoHybrid {

    public static void main(String[] args) {
        // Parameters: 1. Games, 2. Seed, 3. Verbose
        int games = argOr(args, 0, 1);
        long seed = argOr(args, 1, System.nanoTime());
        boolean verbose = boolOr(args, 2, true);

        Random diceRng = new Random(seed);

        int hybridWins = 0, ruleWins = 0;
        long totalActions = 0;

        System.out.println("Starting Simulation: hybrid model (RED) vs rule based (BLUE)");
        System.out.println("Loading Neural Network weights...");

        for (int g = 0; g < games; g++) {
            HybridModel redHybrid = new HybridModel();

            RuleBasedPlayer blueRule = new RuleBasedPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)), 5f, 2f);

            GameState state = GameState.initialize(Player.RED);
            int actions = playSingleGame(state, redHybrid, blueRule, diceRng, verbose);

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) hybridWins++; else ruleWins++;
            totalActions += actions;

            System.out.printf("Game %2d winner: %-15s (%d actions)%n", g + 1, (winner == Player.RED ? "HYBRID (RED)" : "RULE (BLUE)"), actions);
        }

        System.out.println("\n=======================================");
        System.out.println("FINAL RESULTS:");
        System.out.printf("Total Games: %d%n", games);
        System.out.printf("HYBRID Wins: %d (%.1f%%)%n", hybridWins, (hybridWins * 100.0 / games));
        System.out.printf("RULE-BASED Wins:             %d (%.1f%%)%n", ruleWins, (ruleWins * 100.0 / games));
        System.out.printf("Avg actions per game:        %.2f%n", (double) totalActions / games);
        System.out.println("=======================================");
    }

    private static int playSingleGame(GameState s,
                                      HybridModel red,
                                      RuleBasedPlayer blue,
                                      Random diceRng,
                                      boolean verbose) {
        int actions = 0;
        final int MAX_ACTIONS = 4000;

        while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {

            boolean isRedTurn = (s.getCurrentPlayer() == Player.RED);

            if (verbose) System.out.printf("%n[%s] turn begins (%s)%n",
                s.getCurrentPlayer(), (isRedTurn ? "Hybrid" : "RuleBased"));

            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                Boolean roll = isRedTurn ? red.rollOrStop(s) : blue.rollOrStop(s);

                if (roll == null || !roll) {
                    if (verbose) System.out.printf("[%s] STOP%n", s.getCurrentPlayer());
                    TurnManager.stop(s);
                    actions++;
                    continue;
                }

                DiceRoll dr = TurnManager.roll(s, diceRng);
                s.setLastRoll(dr);

                if (TurnManager.isBust(s, dr)) {
                    if (verbose) System.out.printf("[%s] BUST with roll %s%n", s.getCurrentPlayer(), dr);
                    TurnManager.bust(s);
                    actions++;
                } else {
                    TurnManager.noBust(s);
                }
                continue;
            }

            if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                List<Move> legal = TurnManager.getLegalMoves(s, s.getLastRoll());

                Move m = isRedTurn ? red.selectMove(s, legal) : blue.selectMove(s, legal);

                if (verbose) System.out.printf("[%s] MOVE %s%n", s.getCurrentPlayer(), m);
                TurnManager.applyMove(s, m);
                actions++;
            }

            if (actions >= MAX_ACTIONS) {
                System.out.println("Safety break: exceeded maximum action count.");
                return actions;
            }
        }
        return actions;
    }

    private static int argOr(String[] args, int idx, int def) {
        if (args.length <= idx) return def;
        try { return Integer.parseInt(args[idx]); } catch (Exception ignored) { return def; }
    }

    private static long argOr(String[] args, int idx, long def) {
        if (args.length <= idx) return def;
        try { return Long.parseLong(args[idx]); } catch (Exception ignored) { return def; }
    }

    private static boolean boolOr(String[] args, int idx, boolean def) {
        if (args.length <= idx) return def;
        String s = args[idx].trim().toLowerCase();
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y");
    }
}
