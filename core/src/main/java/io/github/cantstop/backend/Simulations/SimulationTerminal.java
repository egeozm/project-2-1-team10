package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.AI_MCTS.MCTSPlayer;
import io.github.cantstop.backend.AI_MCTS.MctsAction;
import io.github.cantstop.backend.AI_RuleBased.RuleBasedPlayer;
import io.github.cantstop.backend.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.backend.DiceRoll;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.MatchHistory.AgentSpec;
import io.github.cantstop.backend.MatchHistory.GameResult;
import io.github.cantstop.backend.MatchHistory.MatchHistoryStorage;
import io.github.cantstop.backend.MatchHistory.MatchResult;
import io.github.cantstop.backend.Move;
import io.github.cantstop.backend.Player;
import io.github.cantstop.backend.TurnManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.Scanner;

/**
 * Interactive terminal UI for pitting two configurable agents against each other.
 * Supports expectiminimax (depth or timed), MCTS, and a simple rule-based baseline.
 */
public final class SimulationTerminal {

    private static final long GOLDEN_G = 0x9E3779B97F4A7C15L;
    private static final Scanner SCANNER = new Scanner(System.in);

    private SimulationTerminal() {
    }

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
        long totalMoves = 0;
        List<GameResult> gameResults = new ArrayList<>();

        for (int g = 0; g < games; g++) {
            AgentRuntime redRuntime = buildRuntime(redSpec, matchSeed, g, GOLDEN_G);
            AgentRuntime blueRuntime = buildRuntime(blueSpec, matchSeed, g, GOLDEN_G >>> 1);

            GameState state = GameState.initialize(Player.RED);
            int actions = playSingleGame(state, redRuntime, blueRuntime, verbose);
            totalMoves += actions;

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) {
                redWins++;
            } else {
                blueWins++;
            }
            System.out.printf("Game %2d -> %s in %d actions%n", g + 1, winner, actions);
            gameResults.add(new GameResult(g + 1, winner, actions));
        }

        System.out.println("\n============= FINAL SUMMARY =============");
        System.out.printf("Games played: %d%n", games);
        System.out.printf("RED wins    : %d%n", redWins);
        System.out.printf("BLUE wins   : %d%n", blueWins);
        System.out.printf("Avg actions : %.2f%n", games > 0 ? (double) totalMoves / games : 0.0);
        System.out.printf("Match seed  : %d%n", matchSeed);

        // Save results
        MatchResult matchResult = new MatchResult(
            Instant.now().toString(),
            matchSeed,
            games,
            redWins,
            blueWins,
            totalMoves,
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

    private static AgentRuntime buildRuntime(AgentSpec spec, long baseSeed, int gameIndex, long salt) {
        long combined = spec.seed() ^ baseSeed ^ (salt * (gameIndex + 1L));
        Random rng = new Random(combined);

        return switch (spec.type()) {
            case EXPECTIMINIMAX_DEPTH -> {
                ExpectiminimaxPlayer player = new ExpectiminimaxPlayer(rng);
                AgentController controller = state -> toDecision(
                    player.chooseAction(state, null, spec.rollDepth(), spec.stopDepth())
                );
                yield new AgentRuntime(spec, controller);
            }
            case EXPECTIMINIMAX_TIMED -> {
                ExpectiminimaxPlayer player = new ExpectiminimaxPlayer(rng);
                int maxRoll = spec.rollDepth() > 0 ? spec.rollDepth() : Integer.MAX_VALUE;
                int maxStop = spec.stopDepth() > 0 ? spec.stopDepth() : Integer.MAX_VALUE;
                AgentController controller = state -> toDecision(
                    player.chooseActionWithTime(state, null, spec.perMoveMillis(), maxRoll, maxStop)
                );
                yield new AgentRuntime(spec, controller);
            }
            case MCTS -> {
                MCTSPlayer player = new MCTSPlayer(rng, spec.mctsIterations(), spec.mctsExplorationC(), spec.mctsRolloutMax());
                AgentController controller = new MctsAgentController(player, rng);
                yield new AgentRuntime(spec, controller);
            }
            case RULE_BASED -> {
                RuleBasedPlayer player = new RuleBasedPlayer(rng, 9f);
                AgentController controller = state -> toDecision(player.chooseAction(state, null));
                yield new AgentRuntime(spec, controller);
            }
        };
    }

    private static Decision toDecision(ExpectiminimaxPlayer.Action action) {
        if (action instanceof ExpectiminimaxPlayer.StopAction) {
            return Decision.stop();
        } else if (action instanceof ExpectiminimaxPlayer.RollAction roll) {
            return Decision.move(roll.move());
        }
        return Decision.stop();
    }

    private static Decision toDecision(RuleBasedPlayer.Action action) {
        if (action instanceof RuleBasedPlayer.StopAction) {
            return Decision.stop();
        } else if (action instanceof RuleBasedPlayer.RollAction roll) {
            return Decision.move(roll.move());
        }
        return Decision.stop();
    }

    private static int playSingleGame(GameState state,
                                      AgentRuntime red,
                                      AgentRuntime blue,
                                      boolean verbose) {
        final int MAX_ACTIONS = 300;
        int actions = 0;

        while (!TurnManager.checkWinCondition(state, Player.RED)
            && !TurnManager.checkWinCondition(state, Player.BLUE)) {

            Player current = state.getCurrentPlayer();
            AgentRuntime runtime = current == Player.RED ? red : blue;

            if (verbose) {
                System.out.printf("%n[%s] turn begins (%s)%n", current, runtime.spec().summary());
            }

            boolean turnOver = false;
            while (!turnOver
                && !TurnManager.checkWinCondition(state, Player.RED)
                && !TurnManager.checkWinCondition(state, Player.BLUE)) {

                Decision decision = runtime.controller().decide(state);
                actions++;

                if (decision.kind() == Decision.Kind.STOP) {
                    if (verbose) System.out.printf("[%s] chooses STOP%n", current);
                    TurnManager.stop(state);
                    turnOver = true;
                } else if (decision.kind() == Decision.Kind.MOVE) {
                    Move move = decision.move();
                    if (verbose) {
                        System.out.printf("[%s] applies %s%n", current, move != null ? move : "BUST");
                    }
                    if (!applyOrBust(state, move, verbose)) {
                        turnOver = true;
                    }
                } else {
                    if (verbose) System.out.printf("[%s] produced unknown action; forcing STOP%n", current);
                    TurnManager.stop(state);
                    turnOver = true;
                }

                if (actions >= MAX_ACTIONS) {
                    System.out.println("Safety break: exceeded maximum action count for a single game.");
                    return actions;
                }
            }
        }

        return actions;
    }

    private static boolean applyOrBust(GameState state, Move move, boolean verbose) {
        boolean valid = move != null;
        if (!valid) {
            if (verbose) System.out.println("Encountered illegal move; counting as BUST.");
            TurnManager.bust(state);
            return false;
        }

        TurnManager.applyMove(state, move);
        return true;
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
        int choice = promptInt("Select option", 1, 1, 4);
        long seed = promptLong("Agent RNG seed (blank for random)", System.nanoTime());

        return switch (choice) {
            case 1 -> {
                int rollDepth = promptInt("Roll depth", ExpectiminimaxPlayer.DEFAULT_DEPTH_ROLL_PHASE, 1, 8);
                int stopDepth = promptInt("Stop depth", ExpectiminimaxPlayer.DEFAULT_DEPTH_AFTER_STOP, 1, 10);
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
                int iterations = promptInt("MCTS iterations per decision", 2_000, 100, 100_000);
                double exploration = promptDouble("Exploration constant (C)", 1.414, 0.1, 5.0);
                int rollout = promptInt("Rollout max rolls", 8, 1, 50);
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

    // ----------------------------------------------------------------------
    // Helper types
    // ----------------------------------------------------------------------

    private record AgentRuntime(AgentSpec spec, AgentController controller) {
        AgentRuntime {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(controller, "controller");
        }
    }

    private interface AgentController {
        Decision decide(GameState state);
    }

    private record Decision(Kind kind, Move move) {
        enum Kind { STOP, MOVE }
        static Decision stop() { return new Decision(Kind.STOP, null); }
        static Decision move(Move move) { return new Decision(Kind.MOVE, move); }
    }

    private static final class MctsAgentController implements AgentController {
        private final MCTSPlayer player;
        private final Random rng;

        MctsAgentController(MCTSPlayer player, Random rng) {
            this.player = Objects.requireNonNull(player, "player");
            this.rng = Objects.requireNonNull(rng, "rng");
        }

        @Override
        public Decision decide(GameState state) {
            MctsAction primary = player.decide(state);
            if (primary == null || primary.isStop()) {
                return Decision.stop();
            }
            if (!primary.isRoll()) {
                // Unexpected at roll/stop phase; default to STOP to stay safe.
                return Decision.stop();
            }

            DiceRoll realRoll = DiceRoll.roll(rng);
            List<Move> legal = TurnManager.getLegalMoves(state, realRoll);
            if (legal.isEmpty()) {
                return Decision.move(null);
            }

            GameState chooseRoot = state.copy();
            TurnManager.noBust(chooseRoot);
            chooseRoot.setLastRoll(realRoll);
            MctsAction moveAction = player.decide(chooseRoot, realRoll);
            Move chosen = (moveAction != null && moveAction.isMove())
                ? moveAction.move
                : legal.get(0);

            return Decision.move(chosen);
        }
    }

}

