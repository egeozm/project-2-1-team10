package io.github.cantstop.model.match_history;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;

/**
 * Utility class for saving and loading match history results.
 * Saves results in organized folders: results/{agent1}_vs_{agent2}/
 */
public final class MatchHistoryStorage {

    private static final String BASE_DIR = "core/src/main/java/io/github/cantstop";
    private static final String RESULTS_DIR = "results";

    private MatchHistoryStorage() {
    } // utility class

    /**
     * Saves a match result to a JSON file in an organized folder structure.
     * Files are saved in: cantstop/results/{agent1}_vs_{agent2}/timestamp.json
     *
     * @param result the match result to save
     * @return the absolute path of the saved file, or null if saving failed
     */
    public static String saveMatchResult(MatchResult result) {
        try {
            // Create folder name based on agent types
            String agent1Name = getAgentTypeName(result.redAgent().type());
            String agent2Name = getAgentTypeName(result.blueAgent().type());
            String matchupFolder = agent1Name + "_vs_" + agent2Name;

            // Create directory structure: cantstop/results/{agent1}_vs_{agent2}/
            Path cantstopDir = Paths.get(BASE_DIR);
            Path resultsDir = cantstopDir.resolve(RESULTS_DIR);
            Path matchupDir = resultsDir.resolve(matchupFolder);
            Files.createDirectories(matchupDir);

            // Create filename with timestamp
            String timestamp = Instant.now().toString().replace(":", "-").replace(".", "-");
            String filename = timestamp + ".json";
            Path filePath = matchupDir.resolve(filename);

            // Write the file
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(filePath))) {
                writer.print(result.toJson());
            }

            return filePath.toAbsolutePath().toString();
        } catch (IOException e) {
            System.err.printf("Failed to save match result: %s%n", e.getMessage());
            return null;
        }
    }

    private static String getAgentTypeName(AgentType type) {
        return switch (type) {
            case EXPECTIMINIMAX_DEPTH -> "Expectiminimax";
            case EXPECTIMINIMAX_TIMED -> "ExpectiminimaxTimed";
            case MCTS -> "MCTS";
            case RULE_BASED -> "RuleBased";
        };
    }

    /**
     * Escapes special characters in a string for JSON serialization.
     */
    static String escapeJson(String s) {
        if (s == null) return "null";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

