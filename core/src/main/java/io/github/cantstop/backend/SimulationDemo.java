package io.github.cantstop.backend;

import java.util.List;
import java.util.Random;

public class SimulationDemo {
    public static void main(String[] args) {
        GameState state = GameState.initialize(Player.BLUE);
        Random rng = new Random(42);

        while (true) {
            System.out.println("--- " + state.getCurrentPlayer() + " to move --");

            DiceRoll roll = DiceRoll.roll(rng);
            int[] dice = roll.dice();
            System.out.printf("Rolled: %d %d %d %d%n", dice[0], dice[1], dice[2], dice[3]);

            List<Move> moves = TurnManager.getLegalMoves(state, roll);
            if (moves.isEmpty()) {
                System.out.println("Bust!");
                TurnManager.stop(state);
            } else {
                // For simulation just take the first legal move
                Move m = moves.get(0);
                System.out.println("Move: " + m);
                TurnManager.applyMove(state, m);

                // Stop policy - stop 50% of the time
                if (rng.nextBoolean()) {
                    System.out.println("Decision: STOP");
                    TurnManager.stop(state);
                } else {
                    System.out.println("Decision: CONTINUE");
                }
            }

            // Check win
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
