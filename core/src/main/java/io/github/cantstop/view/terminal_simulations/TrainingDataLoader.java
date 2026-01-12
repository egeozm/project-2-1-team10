package io.github.cantstop.view.terminal_simulations;

import io.github.cantstop.model.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TrainingDataLoader {
    private static final String FILE_PATH = "core/src/main/java/io/github/cantstop/results/mcts_training_data.csv";

    public static void logState(GameState state, Player winner) {
        ensureHeader();

        float[] vector = new float[36];

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            float maxH = (float) GameConstants.maxHeight(sum);

            // 1-11 columns are red's permanent progress
            vector[col] = state.redPermAtCol(col) / maxH;
            // 12-22 columns are blue's permanent progress
            vector[col + 11] = state.bluePermAtCol(col) / maxH;
            // 23-33 columns are temp marker's progress
            vector[col + 22] = state.tempAtCol(col) / maxH;
        }

        // column 34 is the current player 0=RED, 1=BLUE
        Player current = state.getCurrentPlayer();
        vector[33] = (current == Player.RED) ? 0.0f : 1.0f;

        // column 35 is the remaining runners between 0 and 3
        int activeCount = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            if (state.tempAtCol(col) > 0) activeCount++;
        }
        vector[34] = (float) (GameConstants.MAX_TEMP_RUNNERS - activeCount);

        // column 36 is the win label so 1.0 if the current player eventually won
        // we are gonna use it as the answer key for the neural network
        vector[35] = (current == winner) ? 1.0f : 0.0f;

        saveToCsv(vector);
    }

    private static void ensureHeader() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            List<String> headers = new ArrayList<>();
            // columns 2-12 for RED
            for (int i = 2; i <= 12; i++) headers.add("red_col_" + i);
            // columns 2-12 for BLUE
            for (int i = 2; i <= 12; i++) headers.add("blue_col_" + i);
            // columns 2-12 for temp runners
            for (int i = 2; i <= 12; i++) headers.add("temp_col_" + i);

            headers.add("current_player");
            headers.add("runners_remaining");
            headers.add("did_win_label");

            String headerRow = String.join(",", headers);
            try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FILE_PATH, true)))) {
                out.println(headerRow);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveToCsv(float[] vector) {
        String row = IntStream.range(0, vector.length)
            .mapToObj(i -> String.format("%.4f", vector[i]))
            .collect(Collectors.joining(","));

        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(FILE_PATH, true)))) {
            out.println(row);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
