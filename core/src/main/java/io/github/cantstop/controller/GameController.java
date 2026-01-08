package io.github.cantstop.controller;

import io.github.cantstop.model.*;

import java.util.List;
import java.util.Random;

public class GameController {

    private GameState gameState;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    private final Random rng = new Random(); // this should probably be moved to gameState

    public GameController(GameState gameState, IPlayerController playerRed, IPlayerController playerBlue) {
        this.gameState = gameState;
        this.playerRed = playerRed;
        this.playerBlue = playerBlue;
    }

    public Action update() {

        IPlayerController currentPlayer =
            (gameState.getCurrentPlayer() == Player.RED)
                ? playerRed
                : playerBlue;

        switch (gameState.getTurnPhase()) {

            case ROLL_OR_STOP -> {
                Boolean decision = currentPlayer.rollOrStop(gameState);
                if (decision == null) return new WaitForInputAction();

                if (decision) {
                    DiceRoll roll = TurnManager.roll(gameState, rng);

                    boolean isBust =
                        TurnManager.getLegalMoves(gameState, roll).isEmpty();

                    return new RollAction(roll, isBust);
                }

                return new StopAction();
            }

            case CHOOSE_MOVE -> {
                List<Move> legalMoves =
                    TurnManager.getLegalMoves(gameState, gameState.getLastRoll());

                Move selectedMove = currentPlayer.selectMove(gameState, legalMoves);
                if (selectedMove == null) return new WaitForInputAction();

                return new MoveAction(selectedMove);
            }
        }

        return null;
    }

}
