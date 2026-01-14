package io.github.cantstop.controller;

import io.github.cantstop.model.*;

import javax.swing.*;
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
        this.rng = rng;
    }


    public Event update() {

        IPlayerController currentPlayer =
            (gameState.getCurrentPlayer() == Player.RED)
                ? playerRed
                : playerBlue;

        switch (gameState.getTurnPhase()) {

            case ROLL_OR_STOP -> {

                // check if game has been won
                Player opponent = gameState.getCurrentPlayer().opponent();

                if (TurnManager.checkWinCondition(gameState, opponent)) {
                    return new GameOverEvent(opponent);
                }

                boolean canStop = gameState.countActiveColumns() > 0;

                Boolean decision = currentPlayer.rollOrStop(gameState);


                if (decision == null) {
                    if (canStop) return new WaitForInputEvent();
                    decision = true; // force ROLL
                }


                if (!decision && !canStop) {
                    decision = true;
                }

                if (decision) {
                    DiceRoll roll = TurnManager.roll(gameState, rng);
                    boolean isBust = TurnManager.getLegalMoves(gameState, roll).isEmpty();
                    return new RollEvent(roll, isBust);
                }

                return new StopEvent();
            }

            case CHOOSE_MOVE -> {
                DiceRoll lastRoll = gameState.getLastRoll();
                if (lastRoll == null) {

                    return new WaitForInputEvent();
                }

                List<Move> legalMoves = TurnManager.getLegalMoves(gameState, lastRoll);

                Move selectedMove = currentPlayer.selectMove(gameState, legalMoves);
                if (selectedMove == null) return new WaitForInputEvent();

                return new MoveEvent(selectedMove);
            }
        }

        return null;
    }


}
