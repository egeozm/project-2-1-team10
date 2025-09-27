package io.github.cantStop;

import io.github.cantStop.model.*;
import io.github.cantStop.rules.Rules;
import io.github.cantStop.utensils.ConstantsBE;

import java.util.List;
import java.util.Random;

public class SimulationDemo {
    public static void main(String[] args) {
        GameState state = GameState.initial(Player.RED);
        Random rng = new Random(42);

        while (true) {
            System.out.println("--- " + state.toMove() + " to move --");

            DiceRoll roll = DiceRoll.roll(rng);
            int[] dice = roll.dice();
            System.out.printf("Rolled: %d %d %d %d%n", dice[0], dice[1], dice[2], dice[3]);

            List<Move> moves = Rules.getLegalMoves(state, roll);
            if (moves.isEmpty()) {
                System.out.println("Bust!");
                Rules.stop(state);
            } else {
                // For simulation just take the first legal move
                Move m = moves.get(0);
                System.out.println("Move: " + m);
                Rules.applyMove(state, m);

                // Stop policy - stop 50% of the time
                if (rng.nextBoolean()) {
                    System.out.println("Decision: STOP");
                    Rules.stop(state);
                } else {
                    System.out.println("Decision: CONTINUE");
                    Rules.continueTurn(state);
                }
            }

            // Check win
            if (state.isWin(Player.RED) || state.isWin(Player.BLUE)) {
                if (state.isWin(Player.RED)) {
                    System.out.println("Winner: RED");
                } else {
                    System.out.println("Winner: BLUE");
                }
                break;
            }

        }
    }
}
