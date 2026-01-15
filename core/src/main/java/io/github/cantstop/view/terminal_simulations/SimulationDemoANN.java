package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.RuleBasedPlayer;

import java.io.File;
import java.util.List;
import java.util.Random;

/**
 * Non-interactive demo for evaluating ANN agent vs baselines.
 *
 * Args:
 * 0 games (default 50)
 * 1 seed (default nanoTime)
 * 2 annWeightsPath (default ann_weights.annw)
 * 3 annRollThreshold (default 0.5)
 * 4 opponent (rule|ann|mcts) (default rule)
 */
public final class SimulationDemoANN {

    public static void main(String[] args) {
        int games = argOr(args, 0, 50);
        long seed = argOr(args, 1, System.nanoTime());
        String weightsPath = strOr(args, 2, "ann_weights_mcts.annw");
        float thr = (float) doubleOr(args, 3, 0.5);
        String opponent = strOr(args, 4, "rule").toLowerCase();

        Random diceRng = new Random(seed);

        int annWins = 0, oppWins = 0;
        long totalActions = 0;

        for (int g = 0; g < games; g++) {
            // ANN is always RED for this demo
            AnnPlayer ann = new AnnPlayer(new File(weightsPath), thr);

            Object blue;
            if (opponent.equals("ann")) {
                blue = new AnnPlayer(new File(weightsPath), thr);
            } else if (opponent.equals("mcts")) {
                blue = new MctsOpponent(new Random(seed ^ (g * 0xABCDEF1234L)));
            } else {
                blue = new RuleBasedPlayer(new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL)), 9f, 1f);
            }

            GameState s = GameState.initialize(Player.RED);
            int actions = playSingleGame(s, ann, blue, diceRng);

            Player winner = TurnManager.checkWinCondition(s, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) annWins++; else oppWins++;
            totalActions += actions;
        }

        System.out.println("=======================================");
        System.out.printf("Games: %d | ANN(RED) wins: %d | Opponent(BLUE) wins: %d%n", games, annWins, oppWins);
        System.out.printf("ANN win rate: %.3f%n", games > 0 ? (double) annWins / games : 0.0);
        System.out.printf("Avg actions per game: %.2f%n", games > 0 ? (double) totalActions / games : 0.0);
        System.out.printf("Config: seed=%d weights=%s thr=%.2f opp=%s%n", seed, weightsPath, thr, opponent);
    }

    private static int playSingleGame(GameState s, AnnPlayer redAnn, Object blueAgent, Random diceRng) {
        int actions = 0;
        final int MAX_ACTIONS = 4000;

        while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {
            boolean isRed = s.getCurrentPlayer() == Player.RED;

            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                boolean roll;
                if (isRed) {
                    roll = Boolean.TRUE.equals(redAnn.rollOrStop(s));
                } else if (blueAgent instanceof AnnPlayer ann) {
                    roll = Boolean.TRUE.equals(ann.rollOrStop(s));
                } else if (blueAgent instanceof MctsOpponent mo) {
                    roll = Boolean.TRUE.equals(mo.rollOrStop(s));
                } else {
                    roll = Boolean.TRUE.equals(((RuleBasedPlayer) blueAgent).rollOrStop(s));
                }

                if (!roll) {
                    TurnManager.stop(s);
                    actions++;
                    continue;
                }

                DiceRoll dr = TurnManager.roll(s, diceRng);
                s.setLastRoll(dr);

                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    actions++;
                    continue;
                }

                TurnManager.noBust(s);
                continue;
            }

            if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                List<Move> legal = TurnManager.getLegalMoves(s, s.getLastRoll());
                Move m;
                if (isRed) {
                    m = redAnn.selectMove(s, legal);
                } else if (blueAgent instanceof AnnPlayer ann) {
                    m = ann.selectMove(s, legal);
                } else if (blueAgent instanceof MctsOpponent mo) {
                    m = mo.selectMove(s, legal);
                } else {
                    m = ((RuleBasedPlayer) blueAgent).selectMove(s, legal);
                }

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

    private static double doubleOr(String[] args, int idx, double def) {
        if (args.length <= idx) return def;
        try { return Double.parseDouble(args[idx]); } catch (Exception ignored) { return def; }
    }

    private static String strOr(String[] args, int idx, String def) {
        if (args.length <= idx) return def;
        String s = args[idx];
        return (s == null || s.isBlank()) ? def : s;
    }

    /**
     * Minimal MCTS wrapper for this demo (open-loop MCTSPlayer).
     * Parameters chosen to be reasonable defaults: iterations/time cap + DPW.
     */
    private static final class MctsOpponent {
        private final MCTSPlayer mcts;

        private MctsOpponent(Random rng) {
            // Defaults roughly aligned with SimulationTerminal presets
            this.mcts = new MCTSPlayer(
                rng,
                1_000_000,   // maxIterations cap
                0.25,      // exploration C
                10,        // rollout max
                25.0,      // dpwK
                0.3,       // dpwAlpha
                200        // time budget ms (soft cap)
            );
        }

        Boolean rollOrStop(GameState state) {
            MctsAction a = mcts.decide(state);
            if (a == null) return true;
            if (a.isRoll()) return true;
            if (a.isStop()) return false;
            return true;
        }

        Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves == null || legalMoves.isEmpty()) return null;

            DiceRoll lr = state.getLastRoll();
            MctsAction a = (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE && lr != null)
                ? mcts.decide(state, lr)
                : mcts.decide(state);

            if (a != null && a.isMove()) return a.move;
            return legalMoves.get(0);
        }
    }
}

