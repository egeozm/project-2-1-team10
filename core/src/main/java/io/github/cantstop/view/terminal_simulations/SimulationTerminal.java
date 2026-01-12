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
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.RuleBasedPlayer;
import io.github.cantstop.model.match_history.AgentSpec;
import io.github.cantstop.model.match_history.GameResult;
import io.github.cantstop.model.match_history.MatchHistoryStorage;
import io.github.cantstop.model.match_history.MatchResult;

import java.io.File;
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

    // MCTS PRESETS
// BEST
    private static final int    MCTS_BEST_MAX_ITERS   = 1_000_000;
    private static final long   MCTS_BEST_TIME_MS     = 200;
    private static final int    MCTS_BEST_ROLLOUT_MAX = 10;
    private static final double MCTS_BEST_C           = 0.35;
    private static final double MCTS_BEST_DPW_K       = 25.0;
    private static final double MCTS_BEST_DPW_ALPHA   = 0.5;

    // RESEARCH (C=50 [-100,100])
    private static final int    MCTS_RESEARCH_MAX_ITERS   = 1_000_000;
    private static final long   MCTS_RESEARCH_TIME_MS     = 200;
    private static final int    MCTS_RESEARCH_ROLLOUT_MAX = 10;
    private static final double MCTS_RESEARCH_C_RAW       = 50.0;
    private static final double MCTS_RESEARCH_C_SCALED    = 0.25; //~[0,1]
    private static final double MCTS_RESEARCH_DPW_K       = 25.0;
    private static final double MCTS_RESEARCH_DPW_ALPHA   = 0.3;


    private SimulationTerminal() {}

    public static void main(String[] args) {
        System.out.println("=======================================");
        System.out.println("   Can't Stop – AI Match Playground    ");
        System.out.println("=======================================\n");

        boolean swapSeats = promptBoolean("Evaluation mode (swap seats, same seeds)?", true);

        boolean verbose = promptBoolean("Verbose mode (per-move logs)?", false);
        long matchSeed = promptLong("Enter a base match seed (blank for random)", System.nanoTime());

        if (swapSeats) {
            int gamesPerSide = promptInt("Games per side (total games = 2x)", 100, 1, 5_000);

            System.out.println("\nIn swap-seats mode, the first configured agent is treated as 'Agent A'.");
            System.out.println("Game #1..N  : A plays as RED");
            System.out.println("Game #N+1..2N: A plays as BLUE\n");

            AgentSpec agentA = promptAgent(Player.RED);   // A starts as RED
            AgentSpec agentB = promptAgent(Player.BLUE);  // B starts as BLUE

            System.out.printf("%nStarting SWAP evaluation: %d game(s) per side => total=%d%n", gamesPerSide, 2 * gamesPerSide);
            System.out.printf("Agent A (initially RED)  -> %s%n", agentA.summary());
            System.out.printf("Agent B (initially BLUE) -> %s%n", agentB.summary());
            System.out.printf("Match seed               : %d%n%n", matchSeed);

            System.out.println("=== MATCH 1: as configured (A=RED, B=BLUE) ===");
            MatchResult m1 = playMatchup(agentA, agentB, gamesPerSide, matchSeed, verbose);

            System.out.println("\n=== MATCH 2: swapped seats (A=BLUE, B=RED) ===");
            MatchResult m2 = playMatchup(
                withPlayer(agentB, Player.RED),   // B now plays RED
                withPlayer(agentA, Player.BLUE),  // A now plays BLUE
                gamesPerSide,
                matchSeed,
                verbose
            );

            // Combine results from the perspective of agent identity:
            // In MATCH 1, A=RED => A wins are redWins
            // In MATCH 2, A=BLUE => A wins are blueWins
            int aWins = m1.redWins() + m2.blueWins();
            int totalGames = m1.totalGames() + m2.totalGames();
            int bWins = totalGames - aWins;

            long totalActions = m1.totalActions() + m2.totalActions();
            double aWinRate = totalGames > 0 ? (double) aWins / totalGames : 0.0;

            // Build a combined MatchResult where:
            // - redAgent = Agent A, blueAgent = Agent B
            // - winner field in GameResult is mapped to (RED=A, BLUE=B) for BOTH halves
            List<GameResult> combinedGames = new ArrayList<>(totalGames);

            // First half: winner already matches identity mapping (A=RED, B=BLUE)
            combinedGames.addAll(m1.games());

            // Second half: winner must be remapped because A=BLUE, B=RED in that run
            // If winner was BLUE in match2 => A won => map to RED
            // If winner was RED in match2  => B won => map to BLUE
            for (GameResult gr : m2.games()) {
                int newNum = gr.gameNumber() + gamesPerSide;
                Player mappedWinner = (gr.winner() == Player.BLUE) ? Player.RED : Player.BLUE;
                combinedGames.add(new GameResult(newNum, mappedWinner, gr.actionCount()));
            }

            MatchResult combined = new MatchResult(
                Instant.now().toString(),
                matchSeed,
                totalGames,
                aWins,      // treat as "RED wins" = Agent A wins
                bWins,      // treat as "BLUE wins" = Agent B wins
                totalActions,
                agentA,     // RED agent = Agent A
                agentB,     // BLUE agent = Agent B
                combinedGames
            );

            System.out.println("\n============= FINAL SUMMARY (SWAP-SEATS) =============");
            System.out.printf("Total games : %d (2 x %d)%n", totalGames, gamesPerSide);
            System.out.printf("Agent A wins: %d%n", aWins);
            System.out.printf("Agent B wins: %d%n", bWins);
            System.out.printf("A win rate  : %.3f%n", aWinRate);
            System.out.printf("Avg actions : %.2f%n", totalGames > 0 ? (double) totalActions / totalGames : 0.0);
            System.out.printf("Match seed  : %d%n", matchSeed);
            System.out.println("Note: In the COMBINED saved file, winner=RED means Agent A won; winner=BLUE means Agent B won.");

            if (promptBoolean("Save results to file? (will save match1, match2 and combined)", true)) {
                String f1 = MatchHistoryStorage.saveMatchResult(m1);
                String f2 = MatchHistoryStorage.saveMatchResult(m2);
                String fc = MatchHistoryStorage.saveMatchResult(combined);

                if (f1 != null) System.out.printf("Saved match1 to: %s%n", f1);
                if (f2 != null) System.out.printf("Saved match2 to: %s%n", f2);
                if (fc != null) System.out.printf("Saved combined to: %s%n", fc);
            }

            return;
        }

        // Non-swap, simple mode:
        int games = promptInt("How many games should be played?", 10, 1, 5_000);

        AgentSpec redSpec = promptAgent(Player.RED);
        AgentSpec blueSpec = promptAgent(Player.BLUE);

        System.out.printf("%nStarting match: %d game(s)%n", games);
        System.out.printf("RED  -> %s%n", redSpec.summary());
        System.out.printf("BLUE -> %s%n", blueSpec.summary());
        System.out.printf("Match seed  : %d%n%n", matchSeed);

        MatchResult matchResult = playMatchup(redSpec, blueSpec, games, matchSeed, verbose);

        System.out.println("\n============= FINAL SUMMARY =============");
        System.out.printf("Games played: %d%n", matchResult.totalGames());
        System.out.printf("RED wins    : %d%n", matchResult.redWins());
        System.out.printf("BLUE wins   : %d%n", matchResult.blueWins());
        System.out.printf("Avg actions : %.2f%n", matchResult.totalGames() > 0 ? (double) matchResult.totalActions() / matchResult.totalGames() : 0.0);
        System.out.printf("Match seed  : %d%n", matchSeed);

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

    @SuppressWarnings("unused")
    private static int playSingleGame(GameState state, GameController engine, boolean verbose) {
        final int MAX_ACTIONS = 4000;
        int actions = 0;
        int rolls = 0;
        int moves = 0;
        int stops = 0;

        while (!TurnManager.checkWinCondition(state, Player.RED)
            && !TurnManager.checkWinCondition(state, Player.BLUE)
            && actions < MAX_ACTIONS) {

            Player current = state.getCurrentPlayer();
            TurnPhase phase = state.getTurnPhase();

            Event a = engine.update();
            if (a instanceof RollEvent) rolls++;
            else if (a instanceof MoveEvent) moves++;
            else if (a instanceof StopEvent) stops++;

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

        System.out.printf("rolls=%d moves=%d stops=%d total=%d%n", rolls, moves, stops, actions);

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
            case RULE_BASED -> new RuleBasedPlayer(rng, 9f, 1f);

            case MCTS -> new MctsControllerAdapter(
                new MCTSPlayer(
                    rng,
                    spec.mctsIterations(),
                    spec.mctsExplorationC(),
                    spec.mctsRolloutMax(),
                    spec.mctsDpwK(),
                    spec.mctsDpwAlpha(),
                    spec.mctsTimeBudgetMs()
                )

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

            case ANN -> new AnnPlayer(
                spec.annWeightsPath() == null || spec.annWeightsPath().isBlank() ? null : new File(spec.annWeightsPath()),
                spec.annRollThreshold()
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
            if (a == null) return true;
            if (a.isRoll()) return true;
            if (a.isStop()) return false;
            return true;
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
        System.out.println("5) ANN (imitation-learned)");

        int choice = promptInt("Select option", 5, 1, 5);
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
                System.out.println("\nMCTS preset:");
                System.out.println("1) BEST (your tuned)");
                System.out.println("2) RESEARCH (paper RAW: C=50, assumes utility ~[-100,100])");
                System.out.println("3) RESEARCH (scaled: C≈0.25 for utility ~[0,1])");
                System.out.println("4) Custom");

                int preset = promptInt("Select preset", 1, 1, 4);

                int    defIters;
                long   defTimeMs;
                int    defRollout;
                double defC;
                double defDpwK;
                double defDpwAlpha;

                switch (preset) {
                    case 1 -> { // BEST
                        defIters   = MCTS_BEST_MAX_ITERS;
                        defTimeMs  = MCTS_BEST_TIME_MS;
                        defRollout = MCTS_BEST_ROLLOUT_MAX;
                        defC       = MCTS_BEST_C;
                        defDpwK    = MCTS_BEST_DPW_K;
                        defDpwAlpha= MCTS_BEST_DPW_ALPHA;
                    }
                    case 2 -> { // RESEARCH raw
                        defIters   = MCTS_RESEARCH_MAX_ITERS;
                        defTimeMs  = MCTS_RESEARCH_TIME_MS;
                        defRollout = MCTS_RESEARCH_ROLLOUT_MAX;
                        defC       = MCTS_RESEARCH_C_RAW;
                        defDpwK    = MCTS_RESEARCH_DPW_K;
                        defDpwAlpha= MCTS_RESEARCH_DPW_ALPHA;
                    }
                    case 3 -> { // RESEARCH scaled
                        defIters   = MCTS_RESEARCH_MAX_ITERS;
                        defTimeMs  = MCTS_RESEARCH_TIME_MS;
                        defRollout = MCTS_RESEARCH_ROLLOUT_MAX;
                        defC       = MCTS_RESEARCH_C_SCALED;
                        defDpwK    = MCTS_RESEARCH_DPW_K;
                        defDpwAlpha= MCTS_RESEARCH_DPW_ALPHA;
                    }
                    default -> { // Custom
                        defIters   = 200_000;
                        defTimeMs  = 200;
                        defRollout = 10;
                        defC       = 0.35;
                        defDpwK    = 25.0;
                        defDpwAlpha= 0.5;
                    }
                }

                int iterations = promptInt("MCTS max iterations (safety cap)", defIters, 100, 2_000_000);
                long timeMs    = promptLong("MCTS time budget per decision in ms (0 = disabled)", defTimeMs);

                double exploration = promptDouble("Exploration constant (C)", defC, 0.0, 200.0);

                int rollout   = promptInt("Rollout max rolls", defRollout, 1, 200);

                double dpwK     = promptDouble("DPW k (0 disables DPW)", defDpwK, 0.0, 500.0);
                double dpwAlpha = promptDouble("DPW alpha (0..1)", defDpwAlpha, 0.0, 1.0);

                yield AgentSpec.mcts(player, seed, iterations, exploration, rollout, dpwK, dpwAlpha, timeMs);
            }

            case 4 -> AgentSpec.ruleBased(player, seed);
            case 5 -> {
                String defPath = "ann_weights.annw";
                String path = promptString("ANN weights file path", defPath);
                float thr = (float) promptDouble("Roll threshold (sigmoid>=thr => roll)", 0.50, 0.0, 1.0);
                yield AgentSpec.ann(player, seed, path, thr);
            }
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

    private static AgentSpec withPlayer(AgentSpec s, Player p) {
        return new AgentSpec(
            p, s.type(), s.seed(),
            s.rollDepth(), s.stopDepth(), s.perMoveMillis(),
            s.mctsIterations(), s.mctsExplorationC(), s.mctsRolloutMax(),
            s.mctsDpwK(), s.mctsDpwAlpha(),
            s.mctsTimeBudgetMs(),
            s.annWeightsPath(),
            s.annRollThreshold()
        );
    }

    private static String promptString(String question, String def) {
        while (true) {
            System.out.printf("%s [%s]: ", question, def);
            String line = readLine();
            if (line.isEmpty()) return def;
            return line;
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

            long combined = matchSeed ^ (GOLDEN_G * (g + 1L));
            Random gameRng = new Random(combined);
            GameController engine = new GameController(state, red, blue, gameRng);

            GameStats st = playSingleGameStats(state, engine, verbose);
            totalActions += st.actions();

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++; else blueWins++;

            System.out.printf(
                "Game %3d -> %s | actions=%d turns=%d | rolls=%d moves=%d stops=%d busts=%d | %d ms%n",
                g + 1, winner,
                st.actions(), st.turns(),
                st.rolls(), st.moves(), st.stops(), st.busts(),
                st.elapsedMs()
            );

            gameResults.add(new GameResult(g + 1, winner, st.actions()));
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


    private record GameStats(
        int actions,
        int rolls,
        int moves,
        int stops,
        int busts,
        int turns,
        long elapsedMs
    ) {}

    private static GameStats playSingleGameStats(GameState state, GameController engine, boolean verbose) {
        final int MAX_ACTIONS = 4000;

        int actions = 0;
        int rolls = 0;
        int moves = 0;
        int stops = 0;
        int busts = 0;
        int turns = 0;

        long t0 = System.nanoTime();

        Player prevPlayer = state.getCurrentPlayer();

        while (!TurnManager.checkWinCondition(state, Player.RED)
            && !TurnManager.checkWinCondition(state, Player.BLUE)
            && actions < MAX_ACTIONS) {

            Event a = engine.update();
            if (a == null) break;

            if (a instanceof RollEvent ra) {
                rolls++;
                if (ra.isBust()) busts++;
            } else if (a instanceof MoveEvent) {
                moves++;
            } else if (a instanceof StopEvent) {
                stops++;
            }

            if (verbose) {
                Player current = state.getCurrentPlayer();
                TurnPhase phase = state.getTurnPhase();
                System.out.printf("[%s][%s] %s%n", current, phase, formatAction(a));
            }

            a.apply(state);
            actions++;

            Player now = state.getCurrentPlayer();
            if (now != prevPlayer) {
                turns++;
                prevPlayer = now;
            }
        }

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        return new GameStats(actions, rolls, moves, stops, busts, turns, elapsedMs);
    }

}
