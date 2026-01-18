package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.RuleBasedPlayer;
import io.github.cantstop.model.ai.Hybrid_Model.HybridModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;

/**
 * Headless batch simulator for any two agents (ANN, MCTS, rule-based, expectiminimax).
 * - Runs 500 games with a fixed list of 250 seeds: first 250 as (A=RED,B=BLUE), next 250 swapped.
 * - Prints win rate, average actions, and busts-per-turn mean/variance over all games.
 *
 * Usage:
 *   java ... SimulationBatchAllAgents [agentA] [agentB] [games=500] [seed=123]
 *       [annWeights=ann_weights_mcts.annw] [annThr=0.45]
 *
 * agent names (case-insensitive): ann, mcts, rule, expecti_depth, expecti_timed
 * "hybrid" is treated as expecti_timed.
 */
public final class SimulationBatchAllAgents {

    private static final long GOLDEN_G = 0x9E3779B97F4A7C15L;
    private static final int DEFAULT_GAMES = 500;
    private static final int SEED_COUNT = 500; // fixed list of 500 distinct seeds

    public static void main(String[] args) {
        RunConfig run;
        if (args == null || args.length == 0) {
            run = promptInteractive();
        } else {
            String agentAName = argOr(args, 0, "ann").toLowerCase(Locale.ROOT);
            String agentBName = argOr(args, 1, "rule").toLowerCase(Locale.ROOT);
            int games = intArgOr(args, 2, DEFAULT_GAMES);
            long baseSeed = longArgOr(args, 3, 123L);
            String annWeights = argOr(args, 4, "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw");
            float annThr = floatArgOr(args, 5, 0.55f);
            run = new RunConfig(games, baseSeed, agentAName, agentBName,
                buildSpec(agentAName, annWeights, annThr),
                buildSpec(agentBName, annWeights, annThr));
        }

        int games = Math.max(2, run.games());
        long[] seeds = buildSeeds(run.baseSeed());

        Stats agg = new Stats();
        int total = Math.min(games, SEED_COUNT);
        int half = total / 2;

        for (int i = 0; i < total; i++) {
            long seed = seeds[i];
            boolean swap = i >= half;
            AgentSpec aSpec = swap ? run.agentB() : run.agentA();
            AgentSpec bSpec = swap ? run.agentA() : run.agentB();

            Random diceRng = new Random(seed);
            GameState state = GameState.initialize(Player.RED);
            GameResult r = playSingleGame(state, aSpec, bSpec, diceRng);
            boolean redIsA = !swap;
            boolean aWon = (r.winner() == Player.RED && redIsA) || (r.winner() == Player.BLUE && !redIsA);
            double bustA = redIsA ? r.redBustsPerTurn() : r.blueBustsPerTurn();
            double bustB = redIsA ? r.blueBustsPerTurn() : r.redBustsPerTurn();
            agg.add(r.actions(), bustA, bustB, aWon);
        }

        printSummary(run, total, agg);
    }

    // ----------------------------------------------------------------------
    // Game simulation
    // ----------------------------------------------------------------------

    private static GameResult playSingleGame(GameState s, AgentSpec aSpec, AgentSpec bSpec, Random diceRng) {
        IPlayerController red = aSpec.controller(Player.RED);
        IPlayerController blue = bSpec.controller(Player.BLUE);
        int actions = 0;
        int bustsA = 0;
        int bustsB = 0;
        int turnsA = 0;
        int turnsB = 0;
        Player lastTurnPlayer = null;
        final int MAX_ACTIONS = 4000;

        while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {
            boolean isRed = s.getCurrentPlayer() == Player.RED;
            IPlayerController ctl = isRed ? red : blue;

            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                if (lastTurnPlayer == null || s.getCurrentPlayer() != lastTurnPlayer) {
                    if (isRed) turnsA++; else turnsB++;
                    lastTurnPlayer = s.getCurrentPlayer();
                }
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
                    if (isRed) bustsA++; else bustsB++;
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
        double redBustPerTurn = turnsA == 0 ? 0.0 : (double) bustsA / turnsA;
        double blueBustPerTurn = turnsB == 0 ? 0.0 : (double) bustsB / turnsB;
        return new GameResult(winner, actions, redBustPerTurn, blueBustPerTurn);
    }

    private record GameResult(Player winner, int actions, double redBustsPerTurn, double blueBustsPerTurn) {}

    // ----------------------------------------------------------------------
    // Agent factory
    // ----------------------------------------------------------------------

    private static AgentSpec buildSpec(String name, String annWeights, float annThr) {
        return switch (name) {
            case "rule" -> AgentSpec.rule();
            case "mcts" -> AgentSpec.mcts(MctsConfig.defaults());
            case "expecti_depth" -> AgentSpec.expectiDepth(3, 4);
            case "expecti_timed" -> AgentSpec.expectiTimed(20, 3, 4);
            case "hybrid" -> AgentSpec.hybrid();
            case "ann" -> AgentSpec.ann(annWeights, annThr);
            default -> AgentSpec.ann(annWeights, annThr);
        };
    }

    private enum Type { ANN, MCTS, RULE, EXPECTI_DEPTH, EXPECTI_TIMED, HYBRID }

    private record MctsConfig(int iterations, double c, int rolloutMax, double dpwK, double dpwAlpha, long timeMs) {
        static MctsConfig defaults() { return new MctsConfig(1_000_000, 0.25, 10, 25.0, 0.3, 200); }
    }

    private record ExpectiConfig(int rollDepth, int stopDepth, int perMoveMs, int maxRollDepth, int maxStopDepth, boolean timed) {
        static ExpectiConfig depth(int roll, int stop) { return new ExpectiConfig(roll, stop, 0, Integer.MAX_VALUE, Integer.MAX_VALUE, false); }
        static ExpectiConfig timed(int perMs, int maxRoll, int maxStop) { return new ExpectiConfig(Integer.MAX_VALUE, Integer.MAX_VALUE, perMs, maxRoll > 0 ? maxRoll : Integer.MAX_VALUE, maxStop > 0 ? maxStop : Integer.MAX_VALUE, true); }
    }

    private record AgentSpec(Type type, String weights, float annThr, MctsConfig mcts, ExpectiConfig expecti) {
        static AgentSpec ann(String weights, float thr) { return new AgentSpec(Type.ANN, weights, thr, null, null); }
        static AgentSpec mcts(MctsConfig cfg) { return new AgentSpec(Type.MCTS, null, 0f, cfg, null); }
        static AgentSpec rule() { return new AgentSpec(Type.RULE, null, 0f, null, null); }
        static AgentSpec expectiDepth(int roll, int stop) { return new AgentSpec(Type.EXPECTI_DEPTH, null, 0f, null, ExpectiConfig.depth(roll, stop)); }
        static AgentSpec expectiTimed(int perMs, int maxRoll, int maxStop) { return new AgentSpec(Type.EXPECTI_TIMED, null, 0f, null, ExpectiConfig.timed(perMs, maxRoll, maxStop)); }
        static AgentSpec hybrid() { return new AgentSpec(Type.HYBRID, null, 0f, null, null); }

        IPlayerController controller(Player p) {
            long seed = System.nanoTime() ^ p.hashCode();
            Random rng = new Random(seed);
            return switch (type) {
                case RULE -> new RuleBasedPlayer(rng, 9f, 1f);
                case MCTS -> {
                    MctsConfig c = (mcts == null) ? MctsConfig.defaults() : mcts;
                    MCTSPlayer m = new MCTSPlayer(rng, c.iterations(), c.c(), c.rolloutMax(), c.dpwK(), c.dpwAlpha(), c.timeMs());
                    yield new MctsControllerAdapter(m);
                }
                case EXPECTI_DEPTH -> {
                    ExpectiConfig ec = expecti == null ? ExpectiConfig.depth(3, 4) : expecti;
                    ExpectiminimaxPlayer ex = new ExpectiminimaxPlayer(rng);
                    yield new ExpectiControllerAdapter(ex, ec.rollDepth, ec.stopDepth, 0, false);
                }
                case EXPECTI_TIMED -> {
                    ExpectiConfig ec = expecti == null ? ExpectiConfig.timed(20, Integer.MAX_VALUE, Integer.MAX_VALUE) : expecti;
                    ExpectiminimaxPlayer ex = new ExpectiminimaxPlayer(rng);
                    yield new ExpectiControllerAdapter(ex, ec.maxRollDepth, ec.maxStopDepth, ec.perMoveMs, true);
                }
                case HYBRID -> new HybridModel();
                case ANN -> new AnnPlayer(new File(weights), annThr);
            };
        }
    }

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

    private static final class ExpectiControllerAdapter implements IPlayerController {
        private final ExpectiminimaxPlayer ex;
        private final int rollDepth;
        private final int stopDepth;
        private final int perMoveMs;
        private final boolean timed;
        private ExpectiControllerAdapter(ExpectiminimaxPlayer ex, int rollDepth, int stopDepth, int perMoveMs, boolean timed) {
            this.ex = ex;
            this.rollDepth = rollDepth;
            this.stopDepth = stopDepth;
            this.perMoveMs = perMoveMs;
            this.timed = timed;
        }
        @Override public Boolean rollOrStop(GameState state) {
            var a = timed
                ? ex.chooseActionWithTime(state, null, perMoveMs, maxOrInf(rollDepth), maxOrInf(stopDepth))
                : ex.chooseAction(state, null, rollDepth, stopDepth);
            return !(a instanceof ExpectiminimaxPlayer.StopAction);
        }
        @Override public Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves == null || legalMoves.isEmpty()) return null;
            DiceRoll lr = state.getLastRoll();
            var a = timed
                ? ex.chooseActionWithTime(state, lr, perMoveMs, maxOrInf(rollDepth), maxOrInf(stopDepth))
                : ex.chooseAction(state, lr, rollDepth, stopDepth);
            if (a instanceof ExpectiminimaxPlayer.RollAction ra && ra.move() != null) return ra.move();
            return legalMoves.get(0);
        }
        private static int maxOrInf(int v) { return v > 0 ? v : Integer.MAX_VALUE; }
    }

    // ----------------------------------------------------------------------
    // Aggregation and stats
    // ----------------------------------------------------------------------

    private static final class Stats {
        int aWins = 0;
        int bWins = 0;
        long totalActions = 0;
        List<Double> bustsPerTurnA = new ArrayList<>();
        List<Double> bustsPerTurnB = new ArrayList<>();

        void add(int actions, double bustsA, double bustsB, boolean aWon) {
            if (aWon) aWins++; else bWins++;
            totalActions += actions;
            bustsPerTurnA.add(bustsA);
            bustsPerTurnB.add(bustsB);
        }

        double winRate() {
            int games = aWins + bWins;
            return games == 0 ? 0.0 : (double) aWins / games;
        }

        double avgActions() {
            int games = aWins + bWins;
            return games == 0 ? 0.0 : (double) totalActions / games;
        }

        double meanBustsPerTurnA() {
            if (bustsPerTurnA.isEmpty()) return 0.0;
            double sum = 0;
            for (double v : bustsPerTurnA) sum += v;
            return sum / bustsPerTurnA.size();
        }

        double varBustsPerTurnA() {
            if (bustsPerTurnA.size() <= 1) return 0.0;
            double m = meanBustsPerTurnA();
            double sum = 0;
            for (double v : bustsPerTurnA) {
                double d = v - m;
                sum += d * d;
            }
            return sum / (bustsPerTurnA.size() - 1);
        }

        double meanBustsPerTurnB() {
            if (bustsPerTurnB.isEmpty()) return 0.0;
            double sum = 0;
            for (double v : bustsPerTurnB) sum += v;
            return sum / bustsPerTurnB.size();
        }

        double varBustsPerTurnB() {
            if (bustsPerTurnB.size() <= 1) return 0.0;
            double m = meanBustsPerTurnB();
            double sum = 0;
            for (double v : bustsPerTurnB) {
                double d = v - m;
                sum += d * d;
            }
            return sum / (bustsPerTurnB.size() - 1);
        }
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------

    private static long[] buildSeeds(long base) {
        long[] arr = new long[SEED_COUNT];
        for (int i = 0; i < SEED_COUNT; i++) {
            arr[i] = base ^ (GOLDEN_G * (i + 1));
        }
        return arr;
    }

    private static void printSummary(RunConfig run, int games, Stats s) {
        System.out.println("=======================================");
        System.out.printf("Games: %d | %s wins: %d | %s wins: %d%n",
            games, run.agentAName(), s.aWins, run.agentBName(), s.bWins);
        System.out.printf("Win rate for %s: %.3f%n", run.agentAName(), s.winRate());
        System.out.printf("Avg actions per game: %.2f%n", s.avgActions());
        System.out.printf("Busts/turn mean (A): %.4f var: %.6f%n", s.meanBustsPerTurnA(), s.varBustsPerTurnA());
        System.out.printf("Busts/turn mean (B): %.4f var: %.6f%n", s.meanBustsPerTurnB(), s.varBustsPerTurnB());
        System.out.printf("Config: agentA=%s agentB=%s seedBase=%d%n",
            run.agentAName(), run.agentBName(), run.baseSeed());
    }

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
    private static float floatArgOr(String[] args, int idx, float def) {
        try { return Float.parseFloat(argOr(args, idx, String.valueOf(def))); } catch (Exception e) { return def; }
    }

    // ----------------------------------------------------------------------
    // Interactive prompt
    // ----------------------------------------------------------------------

    private record RunConfig(int games, long baseSeed, String agentAName, String agentBName, AgentSpec agentA, AgentSpec agentB) {}

    private static RunConfig promptInteractive() {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== SimulationBatchAllAgents Interactive ===");
        int games = promptInt(sc, "Number of games (default 500)", 500, 2, 10_000);
        long baseSeed = promptLong(sc, "Base seed (default 123)", 123L);
        AgentSpec a = promptAgent(sc, "Agent A (RED)");
        AgentSpec b = promptAgent(sc, "Agent B (BLUE)");
        return new RunConfig(games, baseSeed, describeAgent(a), describeAgent(b), a, b);
    }

    private static String describeAgent(AgentSpec a) {
        return a.type().name().toLowerCase(Locale.ROOT);
    }

    private static AgentSpec promptAgent(Scanner sc, String label) {
        System.out.println(label);
        System.out.println("Options: ann, mcts, rule, expecti_depth, expecti_timed, hybrid");
        String name = promptString(sc, "Choose agent", "ann").toLowerCase(Locale.ROOT);
        return switch (name) {
            case "rule" -> AgentSpec.rule();
            case "mcts" -> {
                int it = promptInt(sc, "MCTS iterations (default 1000000)", 1_000_000, 1, 5_000_000);
                double c = promptDouble(sc, "MCTS C (default 0.25)", 0.25);
                int roll = promptInt(sc, "MCTS rolloutMax (default 10)", 10, 1, 100);
                double dpwK = promptDouble(sc, "MCTS DPW k (default 25.0)", 25.0);
                double dpwA = promptDouble(sc, "MCTS DPW alpha (default 0.3)", 0.3);
                long t = promptLong(sc, "MCTS timeMs (default 200)", 200);
                yield AgentSpec.mcts(new MctsConfig(it, c, roll, dpwK, dpwA, t));
            }
            case "expecti_depth" -> {
                int r = promptInt(sc, "Expecti roll depth (default 3)", 3, 1, 10);
                int s = promptInt(sc, "Expecti stop depth (default 4)", 4, 1, 12);
                yield AgentSpec.expectiDepth(r, s);
            }
            case "expecti_timed" -> {
                int per = promptInt(sc, "Expecti per-move ms (default 20)", 20, 1, 1000);
                int mr = promptInt(sc, "Max roll depth (0=unlimited, default 3)", 3, 0, 10);
                int ms = promptInt(sc, "Max stop depth (0=unlimited, default 4)", 4, 0, 12);
                yield AgentSpec.expectiTimed(per, mr, ms);
            }
            case "hybrid" -> AgentSpec.hybrid();
            case "ann" -> {
                String w = promptString(sc, "ANN weights path", "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw");
                float thr = (float) promptDouble(sc, "ANN roll threshold (default 0.55)", 0.55);
                yield AgentSpec.ann(w, thr);
            }
            default -> {
                String w = promptString(sc, "ANN weights path", "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw");
                float thr = (float) promptDouble(sc, "ANN roll threshold (default 0.55)", 0.55);
                yield AgentSpec.ann(w, thr);
            }
        };
    }

    private static int promptInt(Scanner sc, String q, int def, int min, int max) {
        while (true) {
            System.out.printf("%s [%d]: ", q, def);
            String line = sc.nextLine().trim();
            if (line.isEmpty()) return def;
            try {
                int v = Integer.parseInt(line);
                if (v < min || v > max) {
                    System.out.printf("Enter between %d and %d%n", min, max);
                    continue;
                }
                return v;
            } catch (Exception e) {
                System.out.println("Please enter an integer.");
            }
        }
    }

    private static long promptLong(Scanner sc, String q, long def) {
        while (true) {
            System.out.printf("%s [%d]: ", q, def);
            String line = sc.nextLine().trim();
            if (line.isEmpty()) return def;
            try { return Long.parseLong(line); } catch (Exception e) { System.out.println("Please enter a long."); }
        }
    }

    private static double promptDouble(Scanner sc, String q, double def) {
        while (true) {
            System.out.printf("%s [%.3f]: ", q, def);
            String line = sc.nextLine().trim();
            if (line.isEmpty()) return def;
            try { return Double.parseDouble(line); } catch (Exception e) { System.out.println("Please enter a number."); }
        }
    }

    private static String promptString(Scanner sc, String q, String def) {
        System.out.printf("%s [%s]: ", q, def);
        String line = sc.nextLine();
        if (line == null || line.isBlank()) return def;
        return line.trim();
    }
}

