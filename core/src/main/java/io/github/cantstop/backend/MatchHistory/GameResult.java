package io.github.cantstop.backend.MatchHistory;

import io.github.cantstop.backend.Player;

/**
 * Result of a single game within a match.
 */
public record GameResult(int gameNumber, Player winner, int actionCount) {
    public String toJson() {
        return String.format(
            "{\"gameNumber\":%d,\"winner\":\"%s\",\"actionCount\":%d}",
            gameNumber, winner, actionCount
        );
    }
}

