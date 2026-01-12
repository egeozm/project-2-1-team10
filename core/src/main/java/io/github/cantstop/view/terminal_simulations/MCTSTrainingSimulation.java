package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.controller.Event;
import io.github.cantstop.controller.GameController;
import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Player;
import io.github.cantstop.model.TurnManager;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.view.terminal_simulations.TrainingDataLoader;
import io.github.cantstop.model.Move;
import io.github.cantstop.model.DiceRoll;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MCTSTrainingSimulation {

    private static final int    MCTS_MAX_ITERS   = 1_000_000;
    private static final long   MCTS_TIME_MS     = 100;
    private static final int    MCTS_ROLLOUT_MAX = 10;
    private static final double MCTS_C           = 0.35;
    private static final double MCTS_DPW_K       = 25.0;
    private static final double MCTS_DPW_ALPHA   = 0.5;

    public static void main(String[] args) {
        int totalGames = 1000;
        long baseSeed = System.nanoTime();

        for (int i = 0; i < totalGames; i++) {
            long gameSeed = baseSeed ^ (i * 0x9E3779B97F4A7C15L);
            runSingleTrainingGame(gameSeed);

            System.out.println("Simlated game " + (i + 1));
        }

        System.out.println("Simulation complete");
    }

    private static void runSingleTrainingGame(long seed) {
        Random rng = new Random(seed);
        GameState state = GameState.initialize(Player.RED);

        List<GameState> gameHistory = new ArrayList<>();

        IPlayerController redMCTS = new MctsAdapter(new MCTSPlayer(rng, MCTS_MAX_ITERS, MCTS_C, MCTS_ROLLOUT_MAX, MCTS_DPW_K, MCTS_DPW_ALPHA, MCTS_TIME_MS));
        IPlayerController blueMCTS = new MctsAdapter(new MCTSPlayer(rng, MCTS_MAX_ITERS, MCTS_C, MCTS_ROLLOUT_MAX, MCTS_DPW_K, MCTS_DPW_ALPHA, MCTS_TIME_MS));

        GameController engine = new GameController(state, redMCTS, blueMCTS, rng);

        while (!TurnManager.checkWinCondition(state, Player.RED) &&
            !TurnManager.checkWinCondition(state, Player.BLUE)) {

            gameHistory.add(state.copy());

            Event action = engine.update();
            if (action == null) break;
            action.apply(state);
        }

        Player winner = TurnManager.checkWinCondition(state, Player.RED) ? Player.RED : Player.BLUE;

        for (GameState recordedState : gameHistory) {
            TrainingDataLoader.logState(recordedState, winner);
        }
    }

    private static class MctsAdapter implements IPlayerController {
        private final MCTSPlayer mcts;
        public MctsAdapter(MCTSPlayer mcts) { this.mcts = mcts; }

        @Override
        public Boolean rollOrStop(GameState state) {
            MctsAction a = mcts.decide(state);
            return (a != null && a.isRoll());
        }

        @Override
        public Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves.isEmpty()) return null;
            DiceRoll lr = state.getLastRoll();
            MctsAction a = mcts.decide(state, lr);
            return (a != null && a.isMove()) ? a.move : legalMoves.get(0);
        }
    }
}
