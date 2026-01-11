package io.github.cantstop.controller;

import io.github.cantstop.model.*;

import java.util.List;
import java.util.Random;

public class GameController {

    private GameState gameState;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    private final Random rng;

    public GameController(GameState gameState, IPlayerController playerRed, IPlayerController playerBlue, Random rng) {
        this.gameState = gameState;
        this.playerRed = playerRed;
        this.playerBlue = playerBlue;
        this.rng = (rng != null) ? rng : new Random();
    }


    public Event update() {

        Player opponent = gameState.getCurrentPlayer().opponent();

        if (TurnManager.checkWinCondition(gameState, opponent)) {
            return new GameOverEvent(opponent);
        }

        IPlayerController currentPlayer =
            (gameState.getCurrentPlayer() == Player.RED)
                ? playerRed
                : playerBlue;

        switch (gameState.getTurnPhase()) {

            case ROLL_OR_STOP -> {
                Boolean decision = currentPlayer.rollOrStop(gameState);
                if (decision == null) return new WaitForInputEvent();

                if (decision) {
                    DiceRoll roll = TurnManager.roll(gameState, rng);

                    boolean isBust =
                        TurnManager.getLegalMoves(gameState, roll).isEmpty();

                    return new RollEvent(roll, isBust);
                }

                return new StopEvent();
            }

            case CHOOSE_MOVE -> {
                List<Move> legalMoves =
                    TurnManager.getLegalMoves(gameState, gameState.getLastRoll());

                Move selectedMove = currentPlayer.selectMove(gameState, legalMoves);
                if (selectedMove == null) return new WaitForInputEvent();

                return new MoveEvent(selectedMove);
            }
        }

        return null;
    }

}
