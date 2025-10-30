package io.github.cantstop.backend;

import java.util.List;
import java.util.Random;



// technically should be main file, but i didnt check yet how to change to avoid dependency issues
public class SimulationDemoAI {
    public static void main(String[] args) {
        // AI plays as BLUE; RED is random for demo. Switch if you want AI vs AI.
        GameState state = GameState.initialize(Player.BLUE);
        Random rng = new Random(42);
        AIPlayer ai = new AIPlayer(rng);

        while (true) {
            System.out.println("--- " + state.getCurrentPlayer() + " to move ---");

            // 1) Roll dice (the roll must be known before AI decides)
            DiceRoll roll = DiceRoll.roll(rng);
            int[] dice = roll.dice();
            System.out.printf("Rolled: %d %d %d %d%n", dice[0], dice[1], dice[2], dice[3]);

            // 2) Ask AI to choose STOP or ROLL+Move
            AIPlayer.Action action = ai.chooseAction(state, roll);

            if (action instanceof AIPlayer.StopAction) {
                System.out.println("Decision: STOP");
                TurnManager.stop(state);
            } else if (action instanceof AIPlayer.RollAction rm) {
                System.out.println("Decision: ROLL -> " + rm.move());
                TurnManager.applyMove(state, rm.move());
            } else {
                throw new IllegalStateException("Unknown action");
            }

            // 3) Check win
            if (TurnManager.checkWinCondition(state, Player.RED) || TurnManager.checkWinCondition(state, Player.BLUE)) {
                if (TurnManager.checkWinCondition(state, Player.RED)) {
                    System.out.println("Winner: RED");
                } else {
                    System.out.println("Winner: BLUE");
                }
                break;
            }
        }
    }
}
