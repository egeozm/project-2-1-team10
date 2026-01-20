package io.github.cantstop.model.ai.Hybrid_Model;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

// This class carries out the training process for the ANN
// Loads data, performs stochastic gradient descent and evaluates the current model's accuracy
public class ModelTrainer {
    public static void main(String[] args) {
        // the input here needs to be 35 ..... 1
        // 35 for input and 1 for output but then however many hidden layers you want with x amount of neurons
        NeuralNetwork network = new NeuralNetwork(35, 256, 128, 1);

        List<CSVRow> fullDataset = CSVLoader.load("core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/ann_training_data.csv");

        // Shuffle and split data: 80% for learning and 20% unseen for testing
        Collections.shuffle(fullDataset);
        int trainSize = (int) (fullDataset.size() * 0.8);

        List<CSVRow> trainingSet = new ArrayList<>(fullDataset.subList(0, trainSize));
        List<CSVRow> validationSet = new ArrayList<>(fullDataset.subList(trainSize, fullDataset.size()));

        System.out.println("Training on: " + trainingSet.size() + " rows");
        System.out.println("Validating on: " + validationSet.size() + " rows");

        // Hyper parameters: learningRate controls the step size and epochs is the number of full passes
        double learningRate = 0.1;
        int epochs = 100;

        for (int epoch = 0; epoch < epochs; epoch++) {
            // Shuffle training data during each epoch to prevent the model from learning the sequence
            Collections.shuffle(trainingSet);

            double totalTrainError = 0;
            // Training Phase: feed data through and update weights via backpropagation
            for (CSVRow row : trainingSet) {
                double[] inputs = row.getFeatures();
                double[] target = row.getLabel();

                network.train(inputs, target, learningRate);

                double[] prediction = network.predict(inputs);
                totalTrainError += MatrixMath.calculateErrorMean(prediction, target);
            }

            // Validation Phase: check performance on data the model hasn't seen yet
            double totalValError = 0;
            for (CSVRow row : validationSet) {
                double[] prediction = network.predict(row.getFeatures());
                totalValError += MatrixMath.calculateErrorMean(prediction, row.getLabel());
            }

            // Output loss metrics to track convergence and see if the model is overfitting
            double avgTrainError = totalTrainError / trainingSet.size();
            double avgValError = totalValError / validationSet.size();

            System.out.println("Epoch: " + (epoch + 1) + ", Train Error: " + avgTrainError + ", Val Error: " + avgValError);

            //if (epoch == 30) learningRate = 0.01;
        }
        network.saveModel("core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/model.weights");
    }
}
