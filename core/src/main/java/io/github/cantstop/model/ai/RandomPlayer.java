package io.github.cantstop.model.ai;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;

import java.util.List;
import java.util.Random;

public class RandomPlayer implements IPlayerController {

    private final Random rng = new Random();

    @Override
    public Boolean rollOrStop(GameState state) {
        return rng.nextBoolean(); // roll or stop
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        return legalMoves.get(rng.nextInt(legalMoves.size()));
    }
}

