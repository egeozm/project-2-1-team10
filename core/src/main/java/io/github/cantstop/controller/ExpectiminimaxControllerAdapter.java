package io.github.cantstop.controller;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer.Action;
import io.github.cantstop.model.ai.AI_Expectiminimax.ExpectiminimaxPlayer.RollAction;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Adapter to make the ExpectiminimaxPlayer compatible with the IPlayerController interface.
 * This class also handles different AI difficulty levels by adjusting the search parameters
 * of the ExpectiminimaxPlayer.
 */
public final class ExpectiminimaxControllerAdapter implements IPlayerController {

    private final ExpectiminimaxPlayer player;
    private final AiDifficulty difficulty;

    /**
     * Constructs an adapter for the Expectiminimax AI.
     * The difficulty level determines the search depth or time budget used by the AI.
     *
     * @param difficulty The desired difficulty level (EASY, NORMAL, HARD).
     */
    public ExpectiminimaxControllerAdapter(AiDifficulty difficulty) {
        // A new Random instance is created for the AI.
        // For reproducible tests, a seeded Random object could be passed here.
        this.player = new ExpectiminimaxPlayer(new Random());
        this.difficulty = Objects.requireNonNull(difficulty);
    }

    /**
     * Returns the difficulty level of this AI instance.
     * @return The AiDifficulty enum.
     */
    public AiDifficulty getDifficulty() {
        return difficulty;
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        // For the roll-or-stop decision, the diceRoll is null.
        // The AI will evaluate the expected value of rolling versus stopping.
        Action action = chooseActionForDifficulty(state, null);

        // The AI returns RollAction to roll, and StopAction to stop.
        // This evaluates to true if the AI chose to roll.
        return action instanceof RollAction;
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) {
            return null; // No moves are possible (bust).
        }

        DiceRoll lastRoll = state.getLastRoll();
        if (lastRoll == null) {
            // This case should not be reached if the game state is CHOOSE_MOVE.
            // As a fallback, return the first available move.
            return legalMoves.get(0);
        }

        // The AI is called with the actual dice roll to choose the best move.
        Action action = chooseActionForDifficulty(state, lastRoll);

        if (action instanceof RollAction rollAction) {
            Move chosenMove = rollAction.move();
            // Validate that the AI's move is one of the legal options.
            if (chosenMove != null && legalMoves.contains(chosenMove)) {
                return chosenMove;
            }
        }

        // If the AI's response was unexpected or a bust (null move),
        // fallback to the first legal move.
        return legalMoves.get(0);
    }

    /**
     * A helper method to call the ExpectiminimaxPlayer with parameters
     * corresponding to the selected difficulty level.
     *
     * @param state The current GameState.
     * @param diceRoll The current dice roll, or null if in the ROLL_OR_STOP phase.
     * @return The Action chosen by the AI.
     */
    private Action chooseActionForDifficulty(GameState state, DiceRoll diceRoll) {
        return switch (difficulty) {
            // EASY: Uses a very shallow search depth for quick but suboptimal play.
            case EASY -> player.chooseAction(state, diceRoll, 1, 2);

            // NORMAL: Uses the default search depths defined in the ExpectiminimaxPlayer.
            case NORMAL -> player.chooseAction(state, diceRoll);

            // HARD: Uses iterative deepening with a time budget for a stronger opponent.
            case HARD -> player.chooseActionWithTime(state, diceRoll, 500L); // 500ms time budget
        };
    }
}

/**
 * Defines the difficulty levels for the AI players.
 */
enum AiDifficulty {
    EASY,
    NORMAL,
    HARD
}