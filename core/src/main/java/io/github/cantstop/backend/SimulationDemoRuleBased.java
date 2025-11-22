package io.github.cantstop.backend;

import io.github.cantstop.backend.AI.RuleBasedAgent;

import java.util.Random;

public final class SimulationDemoRuleBased {

    public static void main(String[] args) {
        int games = argOr(args, 0, 20);
        long seed = argOr(args, 1, System.nanoTime());
        boolean verbose = boolOr(args, 2, false);

        Random matchRng = new Random(seed);

        int redWins = 0, blueWins = 0;
        long totalMoves = 0;

        for (int g = 0; g < games; g++) {
            RuleBasedAgent redAgent = new RuleBasedAgent(new Random(seed ^ (g * 0x9E3779B97F4A7C15L)), 9f);
            RuleBasedAgent blueAgent = new RuleBasedAgent(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)), 9f);

            GameState state = GameState.initialize(Player.RED);
            int movesThisGame = playSingleGame(state, redAgent, blueAgent, verbose);

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
        System.out.printf("Config: seed=%d\n", seed);
    }

    private static int playSingleGame(GameState state, RuleBasedAgent redAgent, RuleBasedAgent blueAgent, boolean verbose) {

        int actions = 0;

        while (!TurnManager.checkWinCondition(state, Player.RED) && !TurnManager.checkWinCondition(state, Player.BLUE)) {

            Player current = state.getCurrentPlayer();
            RuleBasedAgent agent = (current == Player.RED) ? redAgent : blueAgent;

            boolean turnOver = false;
            if (verbose) {
                System.out.printf("\n[%s] turn starts\n", current);
            }

            while (!turnOver && !TurnManager.checkWinCondition(state, Player.RED) && !TurnManager.checkWinCondition(state, Player.BLUE)) {
                RuleBasedAgent.Action action = agent.rollOrStop(state);
                actions++;

                if (action instanceof RuleBasedAgent.StopAction) {
                    if (verbose) System.out.printf("[%s] chooses STOP\n", current);
                    TurnManager.stop(state);
                    turnOver = true;
                } else if (action instanceof RuleBasedAgent.RollAction) {
                    Move m = ((RuleBasedAgent.RollAction) action).move();
                    if (verbose) System.out.printf("[%s] applies %s\n", current, m);

                    TurnManager.applyMove(state, m);

                    if (TurnManager.checkWinCondition(state, current)) {
                        if (verbose) System.out.printf("[%s] wins!\n", current);
                        break;
                    }
                } else {
                    if (verbose) System.out.printf("[%s] unknown action, forcing STOP\n", current);
                    TurnManager.stop(state);
                    turnOver = true;
                }
            }
        }

        return actions;
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

    private static boolean boolOr(String[] args, int idx, boolean def) {
        if (args.length <= idx) return def;
        String s = args[idx].trim().toLowerCase();
        if (s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")) return true;
        if (s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")) return false;
        return def;
    }
}
