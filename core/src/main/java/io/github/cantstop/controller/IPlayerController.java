package io.github.cantstop.controller;

import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;

import java.util.List;

public interface IPlayerController {

    Boolean rollOrStop(GameState state);

    Move selectMove(GameState state, List<Move> legalMoves);
}
