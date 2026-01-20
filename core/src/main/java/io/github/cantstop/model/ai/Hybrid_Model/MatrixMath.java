package io.github.cantstop.model.ai.Hybrid_Model;

// Low level linear algebra utility class
// Provides the matrix and vector operations necessary for neural network forward pass calculations
// and backpropagation gradients
public class MatrixMath {

    // Performs vector/matrix multiplication
    // Logic: Sums (input * weight) for every connection
    public static double[] dotProduct(double[] neurons, double[][] weights){
        int inputSize = weights[0].length;
        int outputSize = weights.length;
        double[] result = new double[outputSize];
        for (int i = 0; i < outputSize; i++){
            if (neurons.length != weights[i].length) {
                throw new IllegalArgumentException("Input size: " + neurons.length +
                    " does not match the number of weight columns " + weights[0].length);
            }
            for (int j = 0; j < inputSize; j++){
                result[i] += neurons[j]*weights[i][j];
            }
        }
        return result;
    }

    // Shifts the neuron values by a fixed bias
    // Pass in the biases for the layer and the neurons and it will add the bias to each corresponding neuron
    public static double[] addBias(double[] biases, double[] neurons){
        if (neurons.length != biases.length) {
            throw new IllegalArgumentException("Number of neurons: " + neurons.length +
                " does not match the number of biases: " + biases.length);
        }
        for (int i = 0; i < neurons.length; i++){
            neurons[i] += biases[i];
        }
        return(neurons);
    }

    // The sctivation function used
    // It bounds raw sums into a probability range between 0 and 1.
    public static double[] sigmoid(double[] z){
        for (int i = 0; i < z.length; i++){
            z[i] = 1/(1 + Math.exp(-z[i]));
        }
        return z;
    }

    // Calculates the slope of the sigmoid curve
    // Used in backpropagation to determine how much to adjust weights
    public static double[] sigmoidDerivative(double[] activations){
        for (int i = 0; i < activations.length; i++){
            activations[i] = activations[i] * (1 - activations[i]);
        }
        return activations;
    }

    // Calculates the mean squared error across a whole layer
    // Provides a single number to track how well the model is learning per epoch
    public static double calculateErrorMean(double[] predicted, double[] target){
        double[] errors = new double[predicted.length];
        if (predicted.length != target.length) {
            throw new IllegalArgumentException("Number of predicted values: " + predicted.length +
                " does not match the number of target values: " + target.length);
        }
        double errorSum = 0;
        for (int i = 0; i < predicted.length; i++){
            errors[i] = Math.pow((predicted[i] - target[i]), 2);
            errorSum += errors[i];
        }
        return errorSum / predicted.length;
    }

    // Performs element-wise multiplication
    // Used to apply the sigmoid derivative to the error gradient
    public static double[] multiplyElements(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }
        return result;
    }

    // Multiplies the error delta by the transposed weight matrix
    // This "pushes" the error backwards from the current layer to the previous one
    public static double[] transposeDotProduct(double[] delta, double[][] weights) {
        int numInputs = weights[0].length;
        int numNeurons = weights.length;
        double[] nextError = new double[numInputs];
        for (int j = 0; j < numInputs; j++) {
            for (int i = 0; i < numNeurons; i++) {
                nextError[j] += delta[i] * weights[i][j];
            }
        }
        return nextError;
    }
}

