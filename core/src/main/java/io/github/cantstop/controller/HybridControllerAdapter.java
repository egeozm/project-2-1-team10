package io.github.cantstop.controller;

import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.Hybrid_Model.HybridModel;
import java.util.List;

public final class HybridControllerAdapter implements IPlayerController {

    private final HybridModel agent;

    public HybridControllerAdapter() {
        this.agent = new HybridModel();
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        return agent.rollOrStop(state);
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        return agent.selectMove(state, legalMoves);
    }
}
