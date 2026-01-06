package io.github.cantstop.backend.MatchHistory;

import io.github.cantstop.backend.Player;

/**
 * Configuration specification for an AI agent.
 * Captures all parameters needed to recreate an agent for reproducibility.
 */
public record AgentSpec(
    Player player,
    AgentType type,
    long seed,
    int rollDepth,
    int stopDepth,
    int perMoveMillis,
    int mctsIterations,
    double mctsExplorationC,
    int mctsRolloutMax,
    double mctsDpwK,
    double mctsDpwAlpha
) {
    // DPW defaults (keep in sync with MCTSPlayer defaults)
    private static final double DEFAULT_DPW_K = 4.0;
    private static final double DEFAULT_DPW_ALPHA = 0.5;

    public static AgentSpec depth(Player player, long seed, int rollDepth, int stopDepth) {
        return new AgentSpec(player, AgentType.EXPECTIMINIMAX_DEPTH, seed, rollDepth, stopDepth, 0, 0, 0.0, 0,
            0.0, 0.0);
    }

    public static AgentSpec timed(Player player, long seed, int perMoveMillis) {
        return new AgentSpec(player, AgentType.EXPECTIMINIMAX_TIMED, seed, 0, 0, perMoveMillis, 0, 0.0, 0,
            0.0, 0.0);
    }

    public static AgentSpec timed(Player player, long seed, int perMoveMillis, int maxRollDepth, int maxStopDepth) {
        return new AgentSpec(player, AgentType.EXPECTIMINIMAX_TIMED, seed, maxRollDepth, maxStopDepth, perMoveMillis, 0, 0.0, 0,
            0.0, 0.0);
    }

    // Backward-compatible MCTS factory (defaults DPW)
    public static AgentSpec mcts(Player player, long seed, int iterations, double explorationC, int rolloutMax) {
        return mcts(player, seed, iterations, explorationC, rolloutMax, DEFAULT_DPW_K, DEFAULT_DPW_ALPHA);
    }

    // New MCTS factory (tunable DPW)
    public static AgentSpec mcts(Player player, long seed, int iterations, double explorationC, int rolloutMax,
                                 double dpwK, double dpwAlpha) {
        return new AgentSpec(player, AgentType.MCTS, seed, 0, 0, 0, iterations, explorationC, rolloutMax,
            dpwK, dpwAlpha);
    }

    public static AgentSpec ruleBased(Player player, long seed) {
        return new AgentSpec(player, AgentType.RULE_BASED, seed, 0, 0, 0, 0, 0.0, 0,
            0.0, 0.0);
    }

    public String summary() {
        return switch (type) {
            case EXPECTIMINIMAX_DEPTH ->
                String.format("Expectiminimax(depth roll=%d, stop=%d, seed=%d)", rollDepth, stopDepth, seed);

            case EXPECTIMINIMAX_TIMED -> {
                if (rollDepth > 0 || stopDepth > 0) {
                    yield String.format("Expectiminimax(timed %d ms, maxRoll=%d, maxStop=%d, seed=%d)",
                        perMoveMillis, rollDepth, stopDepth, seed);
                } else {
                    yield String.format("Expectiminimax(timed %d ms, seed=%d)", perMoveMillis, seed);
                }
            }

            case MCTS ->
                String.format("MCTS(iter=%d, C=%.2f, rollout=%d, dpwK=%.2f, dpwA=%.2f, seed=%d)",
                    mctsIterations, mctsExplorationC, mctsRolloutMax, mctsDpwK, mctsDpwAlpha, seed);

            case RULE_BASED ->
                String.format("RuleBased(seed=%d)", seed);
        };
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("      \"player\": \"").append(player).append("\",\n");
        json.append("      \"type\": \"").append(type).append("\",\n");
        json.append("      \"seed\": ").append(seed);

        switch (type) {
            case EXPECTIMINIMAX_DEPTH -> {
                json.append(",\n      \"rollDepth\": ").append(rollDepth);
                json.append(",\n      \"stopDepth\": ").append(stopDepth);
            }
            case EXPECTIMINIMAX_TIMED -> {
                json.append(",\n      \"perMoveMillis\": ").append(perMoveMillis);
                if (rollDepth > 0 || stopDepth > 0) {
                    json.append(",\n      \"maxRollDepth\": ").append(rollDepth);
                    json.append(",\n      \"maxStopDepth\": ").append(stopDepth);
                }
            }
            case MCTS -> {
                json.append(",\n      \"mctsIterations\": ").append(mctsIterations);
                json.append(",\n      \"mctsExplorationC\": ").append(mctsExplorationC);
                json.append(",\n      \"mctsRolloutMax\": ").append(mctsRolloutMax);
                json.append(",\n      \"mctsDpwK\": ").append(mctsDpwK);
                json.append(",\n      \"mctsDpwAlpha\": ").append(mctsDpwAlpha);
            }
            case RULE_BASED -> {
                // No additional parameters
            }
        }

        json.append("\n    }");
        return json.toString();
    }
}
