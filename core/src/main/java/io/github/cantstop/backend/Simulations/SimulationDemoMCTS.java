package io.github.cantstop.backend.Simulations;

import io.github.cantstop.backend.AI_MCTS.MCTSController;
import io.github.cantstop.backend.AI_MCTS.MCTSPlayer;
import io.github.cantstop.backend.AI_MCTS.MctsAction;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.Player;
import io.github.cantstop.backend.TurnManager;

import java.util.Locale;
import java.util.Random;

/**
 * Simple simulator that pits two MCTSPlayers against each other.
 *
 * Usage (all args optional):
 *   java io.github.cantstop.backend.Simulations.SimulationDemoMCTS [games] [iterations] [rolloutMaxRolls] [seed] [verbose]
 */
public final class SimulationDemoMCTS {

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);

        int games           = argOr(args, 0, 1);
        int iterations      = argOr(args, 1, 8000);
        int rolloutMaxRolls = argOr(args, 2, 4);
        long seed           = argOr(args, 3, System.nanoTime());
        boolean verbose     = boolOr(args, 4, false);

        double c = Math.sqrt(2.0); // UCT exploration constant

        int redWins = 0, blueWins = 0;
        long totalActions = 0;

        for (int g = 0; g < games; g++) {
            // Fresh RNGs (lightly decorrelated per game index)
            Random redRngMcts  = new Random(seed ^ (g * 0x9E3779B97F4A7C15L));
            Random blueRngMcts = new Random(seed ^ (g * 0xC2B2AE3D27D4EB4FL));
            Random redRngDice  = new Random(seed ^ (g * 0x94D049BB133111EBL));
            Random blueRngDice = new Random(seed ^ (g * 0x2545F4914F6CDD1DL));

            MCTSPlayer redMcts  = new MCTSPlayer(redRngMcts,  iterations, c, rolloutMaxRolls);
            MCTSPlayer blueMcts = new MCTSPlayer(blueRngMcts, iterations, c, rolloutMaxRolls);
            MCTSController redCtrl  = new MCTSController(redMcts,  redRngDice);
            MCTSController blueCtrl = new MCTSController(blueMcts, blueRngDice);

            // In your codebase: GameState has a private constructor → use factory method
            GameState state = GameState.initialize(Player.RED);

            System.out.printf("\n=========== GAME %d START ===========\n", g + 1);

            int actionsThisGame = playSingleGame(state, redCtrl, blueCtrl, verbose);

            Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
            if (winner == Player.RED) redWins++; else blueWins++;
            totalActions += actionsThisGame;

            System.out.printf("=========== GAME %d END | winner: %s (%d actions) ===========\n",
                g + 1, winner, actionsThisGame);
        }

        System.out.println("=======================================");
        System.out.printf("Games: %d | RED wins: %d | BLUE wins: %d\n", games, redWins, blueWins);
        System.out.printf("Avg actions per game: %.2f\n", (games > 0 ? (double) totalActions / games : 0.0));
        System.out.printf("Config: iterations=%d, rolloutMaxRolls=%d, seed=%d, c=%.3f\n",
            iterations, rolloutMaxRolls, seed, c);
    }

    /**
     * Plays one full game until a player satisfies the win condition.
     *
     * We count "actions" as STOPs and MOVEs applied (ROLL does not mutate the state by itself).
     *
     * @return number of applied actions in this game
     */
    private static int playSingleGame(GameState state, MCTSController redCtrl, MCTSController blueCtrl, boolean verbose) {
        int actions = 0;

        // Safety valve against accidental infinite loops
        final int MAX_ACTIONS_SAFETY = 4000;

        long stepNo = 0; // <- NEW: counts every controller step (decision cycle)

        while (!TurnManager.checkWinCondition(state, Player.RED)
            && !TurnManager.checkWinCondition(state, Player.BLUE)) {

            Player turnOwner = state.getCurrentPlayer();
            System.out.printf("\n[%s] turn starts\n", turnOwner);

            // One turn: keep deciding until STOP/BUST or terminal (player changes or game ends)
            while (!TurnManager.checkWinCondition(state, Player.RED)
                && !TurnManager.checkWinCondition(state, Player.BLUE)
                && state.getCurrentPlayer() == turnOwner) {

                stepNo++;

                MCTSController.ExecLog log = (turnOwner == Player.RED)
                    ? redCtrl.decideAndApply(state)
                    : blueCtrl.decideAndApply(state);

                // ALWAYS print each step (each "ruch" w Twoim sensie)
                printExecLog(stepNo, turnOwner, log, verbose);

                // Count applied actions: STOP or MOVE.
                if (log.firstAction != null && log.firstAction.isStop()) actions++;
                if (log.chosenMove != null) actions++;

                if (actions > MAX_ACTIONS_SAFETY) {
                    System.out.println("Safety break: too many actions, aborting game.");
                    return actions;
                }
            }
        }

        Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;
        System.out.printf("\n[%s] wins!\n", winner);
        return actions;
    }

    // ----------------- logging helpers -----------------

    /**
     * Pretty-prints one controller step.
     * Prints every step always; when verbose=true adds a little extra framing.
     */
    private static void printExecLog(long stepNo, Player p, MCTSController.ExecLog log, boolean verbose) {
        StringBuilder sb = new StringBuilder();

        // Step number
        sb.append(String.format("#%05d ", stepNo));

        sb.append("[").append(p).append("] ");

        if (log.firstAction != null) {
            MctsAction a = log.firstAction;
            if (a.isStop()) sb.append("STOP");
            else if (a.isRoll()) sb.append("ROLL");
            else if (a.isMove()) sb.append("MOVE?");
        } else {
            sb.append("NO_ACTION?");
        }

        if (log.roll != null) {
            int[][] pairs = log.roll.pairings();
            sb.append("  roll=[")
                .append(pairs[0][0]).append("+").append(pairs[0][1]).append(" | ")
                .append(pairs[1][0]).append("+").append(pairs[1][1]).append(" | ")
                .append(pairs[2][0]).append("+").append(pairs[2][1]).append("]");
        }

        if (log.chosenMove != null) {
            sb.append("  move=pair ").append(log.chosenMove.pairingIndex())
                .append(" (").append(log.chosenMove.sumA()).append("+").append(log.chosenMove.sumB()).append(")");
        }

        if (log.bust) sb.append("  [BUST]");
        if (log.terminal) sb.append("  [TERMINAL]");

        if (verbose) {
            // Optional: show turn phase maybe (if you have it in log; if not, ignore)
            // sb.append("  phase=").append(...);
        }

        System.out.println(sb.toString());
    }

    // ----------------- small arg helpers -----------------

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
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y")
            || (def == false && !(s.equals("false") || s.equals("0") || s.equals("no") || s.equals("n")));
    }
}
