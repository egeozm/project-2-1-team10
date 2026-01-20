package io.github.cantstop.model.ai.Hybrid_Model;

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// Manages the multilayer feedforward ANN
// Acts as the high level interface for making game state predictions and executing the training feedback loop
public class NeuralNetwork {
    private List<Layer> layers;

    // Constructs the network architecture based on provided dimensions
    // new NeuralNetwork(35, 256, 1) creates 35 inputs, 256 neurons in hidden layer and a one neuron output layer
    public NeuralNetwork(int... layerSizes) {
        this.layers = new ArrayList<>();
        for (int i = 0; i < layerSizes.length - 1; i++) {
            layers.add(new Layer(layerSizes[i], layerSizes[i + 1]));
        }
    }

    // Propagates inputs through all layers to produce a final win probability
    // Each layer's output becomes the next layer's input
    public double[] predict(double[] inputs) {
        double[] currentSignals = inputs;
        for (Layer layer : layers) {
            currentSignals = layer.forwardPass(currentSignals);
        }
        return currentSignals;
    }

    // Executes a single training step using supervised learning
    // Calculates the difference between prediction and actual outcome then pushes that error back through
    // the layers to adjust weights
    public void train(double[] input, double[] target, double learningRate) {
        double[] prediction = predict(input);

        // Calculate the raw error (delta) at the final output
        double[] outputError = new double[prediction.length];
        for (int i = 0; i < prediction.length; i++) {
            outputError[i] = prediction[i] - target[i];
        }

        // Backpropagate the error from the last layer to the first
        double[] errorGradient = outputError;
        for (int i = layers.size() - 1; i >= 0; i--) {
            errorGradient = layers.get(i).backpropagate(errorGradient, learningRate);
        }
    }

    // Saves the current state of all weights and biases to a text file
    // This allows the trained model to be reloaded later without retraining
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

    // Returns the internal list of layers for inspection or weight injection
    public List<Layer> getLayers() {
        return layers;
    }
}
