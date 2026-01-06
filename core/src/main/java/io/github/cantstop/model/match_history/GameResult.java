package io.github.cantstop.model.match_history;

import io.github.cantstop.model.Player;

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

