package io.github.cantstop.view.gui;

public record AiConfig(AgentType type, int timeMs, String annWeights, float annThreshold) {
    public AiConfig(AgentType type, int timeMs) {
        this(type, timeMs, null, 0.45f);
    }
}
