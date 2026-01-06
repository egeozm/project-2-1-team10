package io.github.cantstop.backend.AI_MCTS;

public record MctsConfig(
    int iterations,
    int rolloutMaxRolls,
    double c,
    double dpwK,
    double dpwAlpha
) {
    @Override
    public String toString() {
        return "MctsConfig{" +
            "iter=" + iterations +
            ", dr=" + rolloutMaxRolls +
            ", c=" + c +
            ", dpwK=" + dpwK +
            ", dpwAlpha=" + dpwAlpha +
            '}';
    }
}
