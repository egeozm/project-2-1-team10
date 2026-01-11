package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.RuleBasedPlayer;

import java.util.List;
import java.util.Random;

public final class SimulationDemoRuleBased {

    public static void main(String[] args) {
        int games = argOr(args, 0, 20);
        long seed = argOr(args, 1, System.nanoTime());
        boolean verbose = boolOr(args, 2, false);

        Random diceRng = new Random(seed);

        int redWins = 0, blueWins = 0;
        long totalActions = 0;

        for (int g = 0; g < games; g++) {
            RuleBasedPlayer red = new RuleBasedPlayer(new Random(seed ^ (g * 0x9E3779B97F4A7C15L)), 5f, 2f);
            RuleBasedPlayer blue = new RuleBasedPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)), 5f, 2f);

            GameState state = GameState.initialize(Player.RED);
            int actions = playSingleGame(state, red, blue, diceRng, verbose);

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++; else blueWins++;
            totalActions += actions;

            if (!verbose) {
                System.out.printf("Game %2d winner: %s (%d actions)%n", g + 1, winner, actions);
            }
        }

        System.out.println("=======================================");
        System.out.printf("Games: %d | RED wins: %d | BLUE wins: %d%n", games, redWins, blueWins);
        System.out.printf("Avg actions per game: %.2f%n", games > 0 ? (double) totalActions / games : 0.0);
        System.out.printf("Config: seed=%d%n", seed);
    }

    private static int playSingleGame(GameState s,
                                      RuleBasedPlayer red,
                                      RuleBasedPlayer blue,
                                      Random diceRng,
                                      boolean verbose) {
        int actions = 0;
        final int MAX_ACTIONS = 4000;

        while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {

            RuleBasedPlayer agent = (s.getCurrentPlayer() == Player.RED) ? red : blue;

            if (verbose) System.out.printf("%n[%s] turn begins%n", s.getCurrentPlayer());

            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                Boolean roll = agent.rollOrStop(s);
                if (roll == null || !roll) {
                    if (verbose) System.out.printf("[%s] STOP%n", s.getCurrentPlayer());
                    TurnManager.stop(s);
                    actions++;
                    continue;
                }

                DiceRoll dr = TurnManager.roll(s, diceRng);
                s.setLastRoll(dr);

                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    if (verbose) System.out.printf("[%s] BUST%n", s.getCurrentPlayer());
                    TurnManager.bust(s);
                    actions++;
                    continue;
                }

                TurnManager.noBust(s);
                continue;
            }

            if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                List<Move> legal = TurnManager.getLegalMoves(s, s.getLastRoll());
                Move m = agent.selectMove(s, legal);
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
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        return def;
    }
}
