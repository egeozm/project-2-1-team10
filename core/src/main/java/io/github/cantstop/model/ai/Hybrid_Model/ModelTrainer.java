package io.github.cantstop.model.ai.Hybrid_Model;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

public class ModelTrainer {
    public static void main(String[] args) {
        // the input here needs to be 35 ..... 1
        // 35 for input and 1 for output but then however many hidden layers you want with x amount of neurons
        NeuralNetwork network = new NeuralNetwork(35, 256, 256, 128, 64, 32, 1);

        List<CSVRow> fullDataset = CSVLoader.load("core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/ann_training_data.csv");

        Collections.shuffle(fullDataset);
        int trainSize = (int) (fullDataset.size() * 0.8);

        List<CSVRow> trainingSet = new ArrayList<>(fullDataset.subList(0, trainSize));
        List<CSVRow> validationSet = new ArrayList<>(fullDataset.subList(trainSize, fullDataset.size()));

        System.out.println("Training on: " + trainingSet.size() + " rows");
        System.out.println("Validating on: " + validationSet.size() + " rows");

        double learningRate = 0.01;
        int epochs = 10;

        for (int epoch = 0; epoch < epochs; epoch++) {
            Collections.shuffle(trainingSet);

            double totalTrainError = 0;
            for (CSVRow row : trainingSet) {
                double[] inputs = row.getFeatures();
                double[] target = row.getLabel();

                network.train(inputs, target, learningRate);

                double[] prediction = network.predict(inputs);
                totalTrainError += MatrixMath.calculateErrorMean(prediction, target);
            }

            double totalValError = 0;
            for (CSVRow row : validationSet) {
                double[] prediction = network.predict(row.getFeatures());
                totalValError += MatrixMath.calculateErrorMean(prediction, row.getLabel());
            }

            double avgTrainError = totalTrainError / trainingSet.size();
            double avgValError = totalValError / validationSet.size();

            System.out.println("Epoch: " + (epoch + 1) + ", Train Error: " + avgTrainError + ", Val Error: " + avgValError);

            if (epoch == 30) learningRate = 0.001;
        }
        network.saveModel("core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/model.weights");
    }
}

