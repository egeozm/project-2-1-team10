package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.controller.Event;
import io.github.cantstop.controller.GameController;
import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.controller.MoveEvent;
import io.github.cantstop.controller.RollEvent;
import io.github.cantstop.controller.StopEvent;
import io.github.cantstop.controller.WaitForInputEvent;
import io.github.cantstop.model.DiceRoll;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.Player;
import io.github.cantstop.model.TurnManager;
import io.github.cantstop.model.TurnPhase;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.RuleBasedPlayer;
import io.github.cantstop.model.match_history.AgentSpec;
import io.github.cantstop.model.match_history.GameResult;
import io.github.cantstop.model.match_history.MatchHistoryStorage;
import io.github.cantstop.model.match_history.MatchResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

/**
 * Interactive terminal UI for pitting two configurable agents against each other.
 * Uses the new controller-layer API (GameController + IPlayerController).
 */
public final class SimulationTerminal {

    private static final long GOLDEN_G = 0x9E3779B97F4A7C15L;
    private static final Scanner SCANNER = new Scanner(System.in);

    private SimulationTerminal() {}

    public static void main(String[] args) {
        System.out.println("=======================================");
        System.out.println("   Can't Stop – AI Match Playground    ");
        System.out.println("=======================================\n");

        int games = promptInt("How many games should be played?", 10, 1, 5_000);
        boolean verbose = promptBoolean("Verbose mode (per-move logs)?", false);
        long matchSeed = promptLong("Enter a base match seed (blank for random)", System.nanoTime());

        AgentSpec redSpec = promptAgent(Player.RED);
        AgentSpec blueSpec = promptAgent(Player.BLUE);

        System.out.printf("%nStarting match: %d game(s)%n", games);
        System.out.printf("RED  -> %s%n", redSpec.summary());
        System.out.printf("BLUE -> %s%n%n", blueSpec.summary());

        int redWins = 0;
        int blueWins = 0;
        long totalActions = 0;
        List<GameResult> gameResults = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            GameState state = GameState.initialize(Player.RED);

            IPlayerController red = buildController(redSpec, matchSeed, g, GOLDEN_G);
            IPlayerController blue = buildController(blueSpec, matchSeed, g, GOLDEN_G >>> 1);

            // IMPORTANT: GameController needs RNG for real dice rolls.
            Random matchRng = new Random(matchSeed ^ (GOLDEN_G * (g + 1L)));
            GameController engine = new GameController(state, red, blue, matchRng);

            int actions = playSingleGame(state, engine, verbose);
            totalActions += actions;

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++; else blueWins++;

            System.out.printf("Game %2d -> %s in %d actions%n", g + 1, winner, actions);
            gameResults.add(new GameResult(g + 1, winner, actions));
        }

        System.out.println("\n============= FINAL SUMMARY =============");
        System.out.printf("Games played: %d%n", games);
        System.out.printf("RED wins    : %d%n", redWins);
        System.out.printf("BLUE wins   : %d%n", blueWins);
        System.out.printf("Avg actions : %.2f%n", games > 0 ? (double) totalActions / games : 0.0);
        System.out.printf("Match seed  : %d%n", matchSeed);

        MatchResult matchResult = new MatchResult(
            Instant.now().toString(),
            matchSeed,
            games,
            redWins,
            blueWins,
            totalActions,
            redSpec,
            blueSpec,
            gameResults
        );

        if (promptBoolean("Save results to file?", true)) {
            String filename = MatchHistoryStorage.saveMatchResult(matchResult);
            if (filename != null) {
                System.out.printf("Results saved to: %s%n", filename);
            }
        }
    }

    // ----------------------------------------------------------------------
    // Engine loop
    // ----------------------------------------------------------------------

    private static int playSingleGame(GameState state, GameController engine, boolean verbose) {
        final int MAX_ACTIONS = 4000;
        int actions = 0;

        while (!TurnManager.checkWinCondition(state, Player.RED)
            && !TurnManager.checkWinCondition(state, Player.BLUE)
            && actions < MAX_ACTIONS) {

            Player current = state.getCurrentPlayer();
            TurnPhase phase = state.getTurnPhase();

            Event a = engine.update();
            if (a == null) {
                System.out.println("Engine returned null action; stopping for safety.");
                break;
            }

            if (verbose) {
                System.out.printf("[%s][%s] %s%n", current, phase, formatAction(a));
            }

            a.apply(state);
            actions++;

            if (a instanceof WaitForInputEvent) {
                System.out.println("Waiting for human input (not supported here). Stopping.");
                break;
            }
        }

        if (actions >= MAX_ACTIONS) {
            System.out.println("Safety break: exceeded maximum action count for a single game.");
        }

        return actions;
    }

    private static String formatAction(Event a) {
        if (a instanceof StopEvent) return "STOP";
        if (a instanceof RollEvent ra) return "ROLL " + ra.getRoll() + (ra.isBust() ? " -> BUST" : "");
        if (a instanceof MoveEvent ma) return "MOVE " + ma.getMove();
        return a.getClass().getSimpleName();
    }

    // ----------------------------------------------------------------------
    // Controller building
    // ----------------------------------------------------------------------

    private static IPlayerController buildController(AgentSpec spec, long baseSeed, int gameIndex, long salt) {
        long combined = spec.seed() ^ baseSeed ^ (salt * (gameIndex + 1L));
        Random rng = new Random(combined);

        return switch (spec.type()) {
            case RULE_BASED -> new RuleBasedPlayer(rng, 5f, 2f);

            case MCTS -> new MctsControllerAdapter(
                new MCTSPlayer(rng, spec.mctsIterations(), spec.mctsExplorationC(), spec.mctsRolloutMax())
            );

            case EXPECTIMINIMAX_DEPTH -> new ExpectiminimaxControllerAdapter(
                new ExpectiminimaxPlayer(rng),
                spec.rollDepth(),
                spec.stopDepth(),
                0,
                false
            );

            case EXPECTIMINIMAX_TIMED -> new ExpectiminimaxControllerAdapter(
                new ExpectiminimaxPlayer(rng),
                spec.rollDepth(),
                spec.stopDepth(),
                spec.perMoveMillis(),
                true
            );
        };
    }

    /** Adapts MCTSPlayer (STOP/ROLL/MOVE API) to IPlayerController (rollOrStop/selectMove). */
    private static final class MctsControllerAdapter implements IPlayerController {
        private final MCTSPlayer mcts;

        private MctsControllerAdapter(MCTSPlayer mcts) {
            this.mcts = Objects.requireNonNull(mcts);
        }

        @Override
        public Boolean rollOrStop(GameState state) {
            MctsAction a = mcts.decide(state);
            if (a == null) return true; // default roll
            return a.isRoll();
        }

        @Override
        public Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves == null || legalMoves.isEmpty()) return null;

            DiceRoll lr = state.getLastRoll();
            MctsAction a = (state.getTurnPhase() == io.github.cantstop.model.TurnPhase.CHOOSE_MOVE && lr != null)
                ? mcts.decide(state, lr)
                : mcts.decide(state);

            if (a != null && a.isMove()) return a.move;
            return legalMoves.get(0);
        }
    }

    /** Adapts ExpectiminimaxPlayer to IPlayerController. */
    private static final class ExpectiminimaxControllerAdapter implements IPlayerController {
        private final ExpectiminimaxPlayer expecti;
        private final int rollDepth;
        private final int stopDepth;
        private final int perMoveMs;
        private final boolean timed;

        private ExpectiminimaxControllerAdapter(
            ExpectiminimaxPlayer expecti,
            int rollDepth,
            int stopDepth,
            int perMoveMs,
            boolean timed
        ) {
            this.expecti = Objects.requireNonNull(expecti);
            this.rollDepth = rollDepth;
            this.stopDepth = stopDepth;
            this.perMoveMs = perMoveMs;
            this.timed = timed;
        }

        @Override
        public Boolean rollOrStop(GameState state) {
            ExpectiminimaxPlayer.Action a = timed
                ? expecti.chooseActionWithTime(state, null, perMoveMs, maxOrInf(rollDepth), maxOrInf(stopDepth))
                : expecti.chooseAction(state, null, rollDepth, stopDepth);

            return !(a instanceof ExpectiminimaxPlayer.StopAction);
        }

        @Override
        public Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves == null || legalMoves.isEmpty()) return null;

            DiceRoll lr = state.getLastRoll();
            ExpectiminimaxPlayer.Action a = timed
                ? expecti.chooseActionWithTime(state, lr, perMoveMs, maxOrInf(rollDepth), maxOrInf(stopDepth))
                : expecti.chooseAction(state, lr, rollDepth, stopDepth);

            if (a instanceof ExpectiminimaxPlayer.RollAction ra && ra.move() != null) {
                return ra.move();
            }
            return legalMoves.get(0);
        }

        private static int maxOrInf(int v) {
            return v > 0 ? v : Integer.MAX_VALUE;
        }
    }

    // ----------------------------------------------------------------------
    // Prompt helpers
    // ----------------------------------------------------------------------

    private static AgentSpec promptAgent(Player player) {
        System.out.printf("%nConfigure %s agent%n", player);
        System.out.println("1) Expectiminimax - fixed depth search");
        System.out.println("2) Expectiminimax - iterative deepening (per-move deadline)");
        System.out.println("3) MCTS - open loop search");
        System.out.println("4) Rule-based baseline");

        int choice = promptInt("Select option", 4, 1, 4);
        long seed = promptLong("Agent RNG seed (blank for random)", System.nanoTime());

        return switch (choice) {
            case 1 -> {
                int rollDepth = promptInt("Roll depth", 3, 1, 8);
                int stopDepth = promptInt("Stop depth", 4, 1, 10);
                yield AgentSpec.depth(player, seed, rollDepth, stopDepth);
            }
            case 2 -> {
                int perMove = promptInt("Per-move time budget (ms)", 100, 10, 5_000);
                System.out.println("\nOptional: Set maximum depth limits (0 = no limit)");
                int maxRollDepth = promptInt("Max roll depth (0 for unlimited)", 0, 0, 10);
                int maxStopDepth = promptInt("Max stop depth (0 for unlimited)", 0, 0, 12);
                if (maxRollDepth > 0 || maxStopDepth > 0) {
                    yield AgentSpec.timed(player, seed, perMove, maxRollDepth, maxStopDepth);
                } else {
                    yield AgentSpec.timed(player, seed, perMove);
                }
            }
            case 3 -> {
                int iterations = promptInt("MCTS iterations per decision", 20_000, 100, 2_000_000);
                double exploration = promptDouble("Exploration constant (C)", 1.414, 0.1, 5.0);
                int rollout = promptInt("Rollout max rolls", 10, 1, 50);
                yield AgentSpec.mcts(player, seed, iterations, exploration, rollout);
            }
            case 4 -> AgentSpec.ruleBased(player, seed);
            default -> throw new IllegalStateException("Unexpected value: " + choice);
        };
    }

    private static int promptInt(String question, int def, int min, int max) {
        while (true) {
            System.out.printf("%s [%d]: ", question, def);
            String line = readLine();
            if (line.isEmpty()) return def;
            try {
                int value = Integer.parseInt(line);
                if (value < min || value > max) {
                    System.out.printf("Enter a value between %d and %d.%n", min, max);
                    continue;
                }
                return value;
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid integer.");
            }
        }
    }

    private static double promptDouble(String question, double def, double min, double max) {
        while (true) {
            System.out.printf("%s [%.3f]: ", question, def);
            String line = readLine();
            if (line.isEmpty()) return def;
            try {
                double value = Double.parseDouble(line);
                if (value < min || value > max) {
                    System.out.printf("Enter a value between %.3f and %.3f.%n", min, max);
                    continue;
                }
                return value;
            } catch (NumberFormatException ex) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    private static long promptLong(String question, long def) {
        while (true) {
            System.out.printf("%s [%d]: ", question, def);
            String line = readLine();
            if (line.isEmpty()) return def;
            try {
                return Long.parseLong(line);
            } catch (NumberFormatException ex) {
                if (line.equalsIgnoreCase("random") || line.equalsIgnoreCase("rand")) {
                    return System.nanoTime();
                }
                System.out.println("Please enter a valid long integer.");
            }
        }
    }

    private static boolean promptBoolean(String question, boolean def) {
        String defLabel = def ? "Y/n" : "y/N";
        while (true) {
            System.out.printf("%s (%s): ", question, defLabel);
            String line = readLine();
            if (line.isEmpty()) return def;

            String lowered = line.toLowerCase(Locale.ROOT);
            if (lowered.equals("y") || lowered.equals("yes")) return true;
            if (lowered.equals("n") || lowered.equals("no")) return false;
            System.out.println("Please answer with y or n.");
        }
    }

    private static String readLine() {
        String line = SCANNER.nextLine();
        return line == null ? "" : line.trim();
    }

    public static MatchResult playMatchup(
        AgentSpec redSpec,
        AgentSpec blueSpec,
        int games,
        long matchSeed,
        boolean verbose
    ) {
        int redWins = 0;
        int blueWins = 0;
        long totalActions = 0;
        List<GameResult> gameResults = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            GameState state = GameState.initialize(Player.RED);

            IPlayerController red = buildController(redSpec, matchSeed, g, GOLDEN_G);
            IPlayerController blue = buildController(blueSpec, matchSeed, g, GOLDEN_G >>> 1);

            Random matchRng = new Random(matchSeed ^ (GOLDEN_G * (g + 1L)));
            GameController engine = new GameController(state, red, blue, matchRng);

            int actions = playSingleGame(state, engine, verbose);
            totalActions += actions;

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++; else blueWins++;

            gameResults.add(new GameResult(g + 1, winner, actions));
        }

        return new MatchResult(
            Instant.now().toString(),
            matchSeed,
            games,
            redWins,
            blueWins,
            totalActions,
            redSpec,
            blueSpec,
            gameResults
        );
    }
}
