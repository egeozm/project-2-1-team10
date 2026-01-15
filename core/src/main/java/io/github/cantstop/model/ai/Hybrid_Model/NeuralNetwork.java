package io.github.cantstop.model.ai.Hybrid_Model;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class NeuralNetwork {
    private List<Layer> layers;

    public NeuralNetwork(int... layerSizes) {
        this.layers = new ArrayList<>();
        for (int i = 0; i < layerSizes.length - 1; i++) {
            layers.add(new Layer(layerSizes[i], layerSizes[i + 1]));
        }
    }

    public double[] predict(double[] inputs) {
        double[] currentSignals = inputs;
        for (Layer layer : layers) {
            currentSignals = layer.forwardPass(currentSignals);
        }
        return currentSignals;
    }

    public void train(double[] input, double[] target, double learningRate) {
        double[] prediction = predict(input);

        double[] outputError = new double[prediction.length];
        for (int i = 0; i < prediction.length; i++) {
            outputError[i] = prediction[i] - target[i];
        }

        double[] errorGradient = outputError;
        for (int i = layers.size() - 1; i >= 0; i--) {
            errorGradient = layers.get(i).backpropagate(errorGradient, learningRate);
        }
    }

    public void saveModel(String fileName) {
        try (PrintWriter writer = new PrintWriter(new File(fileName))) {
            for (Layer layer : layers) {
                writer.print(layer.exportLayer());
            }
            System.out.println("Model saved to " + fileName);
        } catch (Exception e) {
            System.out.println("Error saving model: " + e.getMessage());
        }
    }

    public List<Layer> getLayers() {
        return layers;
    }
}
