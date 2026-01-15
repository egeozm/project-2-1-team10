package io.github.cantstop.model.ai.Hybrid_Model;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class CSVLoader {
    public static List<CSVRow> load(String fileName) {
        List<CSVRow> data = new ArrayList<>();

        File file = new File(fileName);
        System.out.println("Looking for file at: " + file.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("red_col_2")){
                    continue;
                }

                String[] values = line.split(",");
                if (values.length < 36) continue;

                double[] features = new double[35];
                for (int i = 0; i < 35; i++) {
                    features[i] = Double.parseDouble(values[i]);
                }
                double[] target = new double[]{ Double.parseDouble(values[35]) };
                data.add(new CSVRow(features, target));
            }
        } catch (Exception e) {
            System.out.println("Error loading CSV: " + e.getMessage());
        }

        if (data.isEmpty()) {
            System.out.println("Dataset is empty");
        } else {
            System.out.println("loaded " + data.size() + " rows");
        }
        return data;
    }
}
