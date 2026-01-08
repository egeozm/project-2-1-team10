package io.github.cantstop.model.match_history;

import java.util.List;

/**
 * Complete match result with configuration and all game outcomes.
 * Can be serialized to JSON for later verification and analysis.
 */
public record MatchResult(
    String timestamp,
    long matchSeed,
    int totalGames,
    int redWins,
    int blueWins,
    long totalActions,
    AgentSpec redAgent,
    AgentSpec blueAgent,
    List<GameResult> games
) {
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"timestamp\": \"").append(MatchHistoryStorage.escapeJson(timestamp)).append("\",\n");
        json.append("  \"matchSeed\": ").append(matchSeed).append(",\n");
        json.append("  \"totalGames\": ").append(totalGames).append(",\n");
        json.append("  \"redWins\": ").append(redWins).append(",\n");
        json.append("  \"blueWins\": ").append(blueWins).append(",\n");
        json.append("  \"totalActions\": ").append(totalActions).append(",\n");
        json.append("  \"avgActionsPerGame\": ").append(
            totalGames > 0 ? String.format("%.2f", (double) totalActions / totalGames) : "0.0"
        ).append(",\n");
        json.append("  \"checksum\": ").append(computeChecksum()).append(",\n");
        json.append("  \"redAgent\": ").append(redAgent.toJson()).append(",\n");
        json.append("  \"blueAgent\": ").append(blueAgent.toJson()).append(",\n");
        json.append("  \"games\": [\n");
        for (int i = 0; i < games.size(); i++) {
            json.append("    ").append(games.get(i).toJson());
            if (i < games.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}");
        return json.toString();
    }

    /**
     * Computes a simple checksum for verification purposes.
     * This allows detecting if results were modified.
     */
    public long computeChecksum() {
        long hash = matchSeed;
        hash = 31 * hash + totalGames;
        hash = 31 * hash + redWins;
        hash = 31 * hash + blueWins;
        hash = 31 * hash + totalActions;
        for (GameResult game : games) {
            hash = 31 * hash + game.gameNumber();
            hash = 31 * hash + game.winner().hashCode();
            hash = 31 * hash + game.actionCount();
        }
        return hash;
    }
}

