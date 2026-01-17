package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.Player;
import io.github.cantstop.model.TurnManager;
import io.github.cantstop.model.TurnPhase;
import io.github.cantstop.model.DiceRoll;
import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.RuleBasedPlayer;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Sweeps several roll-thresholds for the ANN and reports win rates vs a chosen opponent.
 *
 * Args:
 *   0 weightsPath (default: core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw)
 *   1 opponent (rule|mcts) default rule
 *   2 gamesPerThreshold (default 200)
 *   3 seed (default 123)
 *   4 comma-separated thresholds (default: 0.35,0.40,0.45,0.50,0.55,0.60)
 */
public final class SimulationAnnThresholdSearch {

    public static void main(String[] args) {
        String weights = argOr(args, 0, "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw");
        String opp = argOr(args, 1, "rule").toLowerCase(Locale.ROOT);
        int games = intArgOr(args, 2, 500);
        long seed = longArgOr(args, 3, 123L);
        String thrList = argOr(args, 4, "0.20,0.25,0.30,0.35,0.40,0.45,0.50,0.55,0.60,0.65,0.70,0.75,0.80,0.85,0.90,0.95");
        double[] thresholds = parseThresholds(thrList);

        System.out.printf("ANN threshold sweep: weights=%s opponent=%s games/threshold=%d seed=%d%n",
            weights, opp, games, seed);

        for (double thr : thresholds) {
            Stats s = runOnce(weights, (float) thr, opp, games, seed);
            System.out.printf("thr=%.3f | winRate=%.3f | avgActions=%.2f | busts/turn mean=%.4f var=%.6f%n",
                thr, s.winRate(), s.avgActions(), s.meanBustsPerTurn(), s.varBustsPerTurn());
        }
    }

    private static Stats runOnce(String weights, float thr, String opp, int games, long baseSeed) {
        Stats stats = new Stats();
        for (int g = 0; g < games; g++) {
            long seed = baseSeed ^ (0x9E3779B97F4A7C15L * (g + 1));
            Random rng = new Random(seed);
            GameState s = GameState.initialize(Player.RED);
            IPlayerController ann = new AnnPlayer(new File(weights), thr);
            IPlayerController enemy = buildOpponent(opp, rng);
            GameResult r = playSingleGame(s, ann, enemy, rng);
            stats.add(r);
        }
        return stats;
    }

    private static IPlayerController buildOpponent(String opp, Random rng) {
        if ("mcts".equals(opp)) {
            MCTSPlayer mcts = new MCTSPlayer(rng, 1_000_000, 0.25, 10, 25.0, 0.3, 200);
            return new MctsControllerAdapter(mcts);
        }
        return new RuleBasedPlayer(rng, 9f, 1f);
    }

    private static GameResult playSingleGame(GameState s, IPlayerController red, IPlayerController blue, Random diceRng) {
        int actions = 0;
        int busts = 0;
        int rollDecisions = 0;
        final int MAX_ACTIONS = 4000;

        while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {
            boolean isRed = s.getCurrentPlayer() == Player.RED;
            IPlayerController ctl = isRed ? red : blue;

            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                rollDecisions++;
                Boolean roll = ctl.rollOrStop(s);
                if (roll == null || !roll) {
                    TurnManager.stop(s);
                    s.setLastRoll(null);
                    actions++;
                    continue;
                }
                DiceRoll dr = TurnManager.roll(s, diceRng);
                s.setLastRoll(dr);
                List<Move> legal = TurnManager.getLegalMoves(s, dr);
                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    s.setLastRoll(null);
                    actions++;
                    busts++;
                    continue;
                }
                TurnManager.noBust(s);
                continue;
            }

            if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                List<Move> legal = TurnManager.getLegalMoves(s, s.getLastRoll());
                Move m = ctl.selectMove(s, legal);
                if (m == null && !legal.isEmpty()) m = legal.get(0);
                TurnManager.applyMove(s, m);
                actions++;
            }

            if (actions >= MAX_ACTIONS) break;
        }

        Player winner = TurnManager.checkWinCondition(s, Player.RED) ? Player.RED : Player.BLUE;
        double bustsPerTurn = rollDecisions == 0 ? 0.0 : (double) busts / rollDecisions;
        return new GameResult(winner, actions, bustsPerTurn);
    }

    private record GameResult(Player winner, int actions, double bustsPerTurn) {}

    private static final class Stats {
        int redWins = 0;
        int blueWins = 0;
        long totalActions = 0;
        double sumBusts = 0;
        double sumSqBusts = 0;
        int bustCount = 0;

        void add(GameResult r) {
            if (r.winner() == Player.RED) redWins++; else blueWins++;
            totalActions += r.actions();
            bustCount++;
            sumBusts += r.bustsPerTurn();
            sumSqBusts += r.bustsPerTurn() * r.bustsPerTurn();
        }

        double winRate() {
            int games = redWins + blueWins;
            return games == 0 ? 0.0 : (double) redWins / games;
        }

        double avgActions() {
            int games = redWins + blueWins;
            return games == 0 ? 0.0 : (double) totalActions / games;
        }

        double meanBustsPerTurn() {
            return bustCount == 0 ? 0.0 : sumBusts / bustCount;
        }

        double varBustsPerTurn() {
            if (bustCount <= 1) return 0.0;
            double mean = meanBustsPerTurn();
            return (sumSqBusts - bustCount * mean * mean) / (bustCount - 1);
        }
    }

    // ----------------------------------------------------------------------
    // Utility helpers
    // ----------------------------------------------------------------------

    private static String argOr(String[] args, int idx, String def) {
        if (args.length <= idx || args[idx] == null || args[idx].isBlank()) return def;
        return args[idx];
    }
    private static int intArgOr(String[] args, int idx, int def) {
        try { return Integer.parseInt(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }
    private static long longArgOr(String[] args, int idx, long def) {
        try { return Long.parseLong(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }
    private static double[] parseThresholds(String csv) {
        String[] parts = csv.split(",");
        double[] arr = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { arr[i] = Double.parseDouble(parts[i]); } catch (Exception e) { arr[i] = 0.45; }
        }
        return arr;
    }

    // MCTS adapter
    private static final class MctsControllerAdapter implements IPlayerController {
        private final MCTSPlayer mcts;
        private MctsControllerAdapter(MCTSPlayer mcts) { this.mcts = mcts; }
        @Override public Boolean rollOrStop(GameState state) {
            MctsAction a = mcts.decide(state);
            if (a == null) return true;
            if (a.isStop()) return false;
            return true;
        }
        @Override public Move selectMove(GameState state, List<Move> legalMoves) {
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

