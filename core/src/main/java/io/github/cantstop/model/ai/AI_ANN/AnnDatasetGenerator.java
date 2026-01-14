package io.github.cantstop.model.ai.AI_ANN;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * Generates imitation datasets using an existing teacher (Expectiminimax or MCTS).
 *
 * Outputs two files:
 * - roll_stop.bin: (state -> rollLabel)
 * - move_pairs.bin: (state+move -> chosenLabel) for all legal moves
 */
public final class AnnDatasetGenerator {

    public static void main(String[] args) throws Exception {
        File outDir = new File(argOr(args, 0, "ann_data"));
        int games = intArgOr(args, 1, 5_000);
        long seed = longArgOr(args, 2, System.nanoTime());

        // Teacher config
        boolean timed = intArgOr(args, 3, 1) != 0; // 1 timed, 0 fixed depth
        int perMoveMs = intArgOr(args, 4, 10);
        int rollDepth = intArgOr(args, 5, 3);
        int stopDepth = intArgOr(args, 6, 4);
        int maxRollDepth = intArgOr(args, 7, 0); // 0 unlimited
        int maxStopDepth = intArgOr(args, 8, 0); // 0 unlimited

        String teacher = argOr(args, 9, "expecti").toLowerCase(); // expecti | mcts

        // MCTS params (used only if teacher=mcts)
        int mctsIterations = intArgOr(args, 10, 200_000);
        double mctsC = doubleArgOr(args, 11, 0.35);
        int mctsRolloutMax = intArgOr(args, 12, 10);
        double mctsDpwK = doubleArgOr(args, 13, 25.0);
        double mctsDpwAlpha = doubleArgOr(args, 14, 0.5);
        long mctsTimeMs = longArgOr(args, 15, 200);

        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new IOException("Failed to create output dir: " + outDir.getAbsolutePath());
        }

        File rollFile = new File(outDir, "roll_stop.bin");
        File pairFile = new File(outDir, "move_pairs.bin");

        System.out.println("ANN dataset generation");
        System.out.println("- outDir: " + outDir.getAbsolutePath());
        System.out.println("- games: " + games);
        System.out.println("- seed: " + seed);
        System.out.println("- teacher: " + teacherDescription(teacher, timed, perMoveMs, rollDepth, stopDepth,
            mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs));

        AnnFeatureExtractor fe = new AnnFeatureExtractor();

        try (
            AnnDatasetIO.RollWriter rollW = AnnDatasetIO.openRollWriter(rollFile);
            AnnDatasetIO.MovePairWriter pairW = AnnDatasetIO.openMovePairWriter(pairFile)
        ) {
            generate(
                games, seed, timed, perMoveMs, rollDepth, stopDepth, maxRollDepth, maxStopDepth,
                teacher,
                mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs,
                fe, rollW, pairW
            );
            rollW.flush();
            pairW.flush();

            System.out.println("Done.");
            System.out.println("- roll samples: " + rollW.count());
            System.out.println("- move pair samples: " + pairW.count());
            System.out.println("- files: " + rollFile.getAbsolutePath() + " , " + pairFile.getAbsolutePath());
        }
    }

    private static void generate(
        int games,
        long seed,
        boolean timed,
        int perMoveMs,
        int rollDepth,
        int stopDepth,
        int maxRollDepth,
        int maxStopDepth,
        String teacher,
        int mctsIterations,
        double mctsC,
        int mctsRolloutMax,
        double mctsDpwK,
        double mctsDpwAlpha,
        long mctsTimeMs,
        AnnFeatureExtractor fe,
        AnnDatasetIO.RollWriter rollW,
        AnnDatasetIO.MovePairWriter pairW
    ) throws IOException {

        final Random envRng = new Random(seed);

        for (int g = 0; g < games; g++) {
            // Separate teacher RNG per player for reproducibility.
            Teacher redAI = buildTeacher(teacher, seed ^ (g * 0x9E3779B97F4A7C15L),
                timed, perMoveMs, rollDepth, stopDepth, maxRollDepth, maxStopDepth,
                mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs);
            Teacher blueAI = buildTeacher(teacher, seed ^ (g * 0xC2B2AE3D27D4EB4FL),
                timed, perMoveMs, rollDepth, stopDepth, maxRollDepth, maxStopDepth,
                mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs);

            GameState s = GameState.initialize(Player.RED);

            int safety = 0;
            while (!TurnManager.checkWinCondition(s, Player.RED) && !TurnManager.checkWinCondition(s, Player.BLUE)) {
                if (safety++ > 10_000) break; // guard against any unexpected loops

                if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                    boolean canStop = s.countActiveColumns() > 0;

                    boolean roll;
                    if (!canStop) {
                        roll = true;
                    } else {
                        Teacher t = (s.getCurrentPlayer() == Player.RED) ? redAI : blueAI;
                        TeacherResult a = t.decideRollOrStop(s, null);
                        roll = a.roll;
                    }

                    // Write roll/stop sample
                    float[] stateF = fe.extractState(s);
                    rollW.write(stateF, roll);

                    if (!roll) {
                        TurnManager.stop(s);
                        s.setLastRoll(null);
                        continue;
                    }

                    DiceRoll dr = DiceRoll.roll(envRng);
                    s.setLastRoll(dr);
                    List<Move> legal = TurnManager.getLegalMoves(s, dr);
                    if (legal.isEmpty()) {
                        TurnManager.bust(s);
                        s.setLastRoll(null);
                        continue;
                    }
                    TurnManager.noBust(s); // CHOOSE_MOVE
                }

                if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                    DiceRoll dr = s.getLastRoll();
                    if (dr == null) {
                        // Shouldn't happen; recover by forcing a new roll.
                        s.setTurnPhase(TurnPhase.ROLL_OR_STOP);
                        continue;
                    }

                    List<Move> legal = TurnManager.getLegalMoves(s, dr);
                    if (legal.isEmpty()) {
                        TurnManager.bust(s);
                        s.setLastRoll(null);
                        continue;
                    }

                    Teacher t = (s.getCurrentPlayer() == Player.RED) ? redAI : blueAI;
                    Move chosen = t.pickMove(s, dr, legal);

                    float[] stateF = fe.extractState(s);
                    float[] moveF = new float[AnnConstants.MOVE_DIM];
                    for (Move m : legal) {
                        fe.fillMoveInPlace(s, m, moveF);
                        pairW.write(stateF, moveF, sameMove(m, chosen));
                    }

                    TurnManager.applyMove(s, chosen);
                    s.setLastRoll(null);
                }
            }

            if (games >= 50 && (g + 1) % Math.max(1, games / 10) == 0) {
                System.out.printf("Progress: %d/%d games%n", (g + 1), games);
            }
        }
    }

    private static int maxOrInf(int v) {
        return v > 0 ? v : Integer.MAX_VALUE;
    }

    private static boolean sameMove(Move a, Move b) {
        return a.pairingIndex() == b.pairingIndex() && a.sumA() == b.sumA() && a.sumB() == b.sumB();
    }

    private static String argOr(String[] args, int idx, String def) {
        return (args != null && args.length > idx && args[idx] != null && !args[idx].isBlank()) ? args[idx] : def;
    }

    private static int intArgOr(String[] args, int idx, int def) {
        try {
            return Integer.parseInt(argOr(args, idx, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    private static long longArgOr(String[] args, int idx, long def) {
        try {
            return Long.parseLong(argOr(args, idx, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    private static double doubleArgOr(String[] args, int idx, double def) {
        try {
            return Double.parseDouble(argOr(args, idx, String.valueOf(def)));
        } catch (Exception e) {
            return def;
        }
    }

    // ----------------------------------------------------------------------
    // Teacher abstraction (Expectiminimax or MCTS)
    // ----------------------------------------------------------------------

    private interface Teacher {
        TeacherResult decideRollOrStop(GameState s, DiceRoll dr);
        Move pickMove(GameState s, DiceRoll dr, List<Move> legal);
    }

    private record TeacherResult(boolean roll, Move move) {}

    private static Teacher buildTeacher(
        String teacher,
        long seed,
        boolean timed,
        int perMoveMs,
        int rollDepth,
        int stopDepth,
        int maxRollDepth,
        int maxStopDepth,
        int mctsIterations,
        double mctsC,
        int mctsRolloutMax,
        double mctsDpwK,
        double mctsDpwAlpha,
        long mctsTimeMs
    ) {
        return switch (teacher) {
            case "mcts" -> new MctsTeacher(seed, mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs);
            default -> new ExpectiTeacher(seed, timed, perMoveMs, rollDepth, stopDepth, maxRollDepth, maxStopDepth);
        };
    }

    private static String teacherDescription(
        String teacher,
        boolean timed,
        int perMoveMs,
        int rollDepth,
        int stopDepth,
        int mctsIterations,
        double mctsC,
        int mctsRolloutMax,
        double mctsDpwK,
        double mctsDpwAlpha,
        long mctsTimeMs
    ) {
        if ("mcts".equals(teacher)) {
            return String.format("MCTS (iter=%d C=%.2f rollout=%d dpwK=%.1f dpwA=%.2f timeMs=%d)",
                mctsIterations, mctsC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, mctsTimeMs);
        }
        return "Expectiminimax " + (timed ? ("timed " + perMoveMs + "ms") : ("depth r=" + rollDepth + " s=" + stopDepth));
    }

    private static final class ExpectiTeacher implements Teacher {
        private final ExpectiminimaxPlayer ai;
        private final boolean timed;
        private final int perMoveMs;
        private final int rollDepth;
        private final int stopDepth;
        private final int maxRollDepth;
        private final int maxStopDepth;

        ExpectiTeacher(long seed, boolean timed, int perMoveMs, int rollDepth, int stopDepth, int maxRollDepth, int maxStopDepth) {
            this.ai = new ExpectiminimaxPlayer(new Random(seed));
            this.timed = timed;
            this.perMoveMs = perMoveMs;
            this.rollDepth = rollDepth;
            this.stopDepth = stopDepth;
            this.maxRollDepth = maxRollDepth;
            this.maxStopDepth = maxStopDepth;
        }

        @Override
        public TeacherResult decideRollOrStop(GameState s, DiceRoll dr) {
            ExpectiminimaxPlayer.Action a = timed
                ? ai.chooseActionWithTime(s, dr, perMoveMs, maxOrInf(maxRollDepth), maxOrInf(maxStopDepth))
                : ai.chooseAction(s, dr, rollDepth, stopDepth);
            boolean roll = !(a instanceof ExpectiminimaxPlayer.StopAction);
            Move m = (a instanceof ExpectiminimaxPlayer.RollAction ra) ? ra.move() : null;
            return new TeacherResult(roll, m);
        }

        @Override
        public Move pickMove(GameState s, DiceRoll dr, List<Move> legal) {
            ExpectiminimaxPlayer.Action a = timed
                ? ai.chooseActionWithTime(s, dr, perMoveMs, maxOrInf(maxRollDepth), maxOrInf(maxStopDepth))
                : ai.chooseAction(s, dr, rollDepth, stopDepth);
            if (a instanceof ExpectiminimaxPlayer.RollAction ra && ra.move() != null) return ra.move();
            return legal.isEmpty() ? null : legal.get(0);
        }
    }

    private static final class MctsTeacher implements Teacher {
        private final io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer mcts;

        MctsTeacher(long seed, int iterations, double c, int rolloutMax, double dpwK, double dpwAlpha, long timeMs) {
            this.mcts = new io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer(
                new Random(seed),
                iterations,
                c,
                rolloutMax,
                dpwK,
                dpwAlpha,
                timeMs
            );
        }

        @Override
        public TeacherResult decideRollOrStop(GameState s, DiceRoll dr) {
            // MCTS decide can include roll/stop; we ignore move unless needed.
            io.github.cantstop.model.ai.AI_MCTS.MctsAction a = (dr != null) ? mcts.decide(s, dr) : mcts.decide(s);
            if (a == null) return new TeacherResult(true, null);
            if (a.isStop()) return new TeacherResult(false, null);
            if (a.isMove()) return new TeacherResult(true, a.move);
            return new TeacherResult(true, null); // roll
        }

        @Override
        public Move pickMove(GameState s, DiceRoll dr, List<Move> legal) {
            io.github.cantstop.model.ai.AI_MCTS.MctsAction a = (dr != null) ? mcts.decide(s, dr) : mcts.decide(s);
            if (a != null && a.isMove()) return a.move;
            return legal.isEmpty() ? null : legal.get(0);
        }
    }
}

