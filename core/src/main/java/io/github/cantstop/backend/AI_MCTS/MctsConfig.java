package io.github.cantstop.backend.AI_MCTS;

public record MctsConfig(
    int maxIterations,     // safety cap
    long timeBudgetMs,     // e.g. 200
    int rolloutMaxRolls,   // dr
    double c,              // exploration
    double dpwK,           // C2 analogue
    double dpwAlpha        // alpha
) {
    @Override
    public String toString() {
        return "MctsConfig{" +
            "iters=" + maxIterations +
            ", t=" + timeBudgetMs +
            "ms, dr=" + rolloutMaxRolls +
            ", c=" + c +
            ", dpwK=" + dpwK +
            ", dpwAlpha=" + dpwAlpha +
            '}';
    }
}
