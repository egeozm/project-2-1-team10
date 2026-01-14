package io.github.cantstop.model.ai.Hybrid_Model;

public class MatrixMath {
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

    public static double[] sigmoid(double[] z){
        for (int i = 0; i < z.length; i++){
            z[i] = 1/(1 + Math.exp(-z[i]));
        }
        return z;
    }

    public static double[] sigmoidDerivative(double[] activations){
        for (int i = 0; i < activations.length; i++){
            activations[i] = activations[i] * (1 - activations[i]);
        }
        return activations;
    }

    public static double[] calculateError(double[] predicted, double[] target){
        double[] errors = new double[predicted.length];
        if (predicted.length != target.length) {
            throw new IllegalArgumentException("Number of predicted values: " + predicted.length +
                " does not match the number of target values: " + target.length);
        }
        for (int i = 0; i < predicted.length; i++){
            errors[i] = Math.pow((predicted[i] - target[i]), 2);
        }
        return errors;
    }

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

    public static double[] multiplyElements(double[] a, double[] b) {
        double[] result = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            result[i] = a[i] * b[i];
        }
        return result;
    }

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

    public static void main(String[] args){
        double[] inputs = new double[]{0,1};
        double[][] weights = new double[][]{{0.4,0.6},{0.3,0.9},{0.7,0.6}};
        double[] neurons = dotProduct(inputs, weights);
        System.out.println(java.util.Arrays.toString(neurons));

        double[] biases = new double[]{0.3, 0.1, 0.6};
        double[] result = addBias(biases, neurons);
        System.out.println(java.util.Arrays.toString(result));

        System.out.println((java.util.Arrays.toString(sigmoid(result))));

        System.out.println((java.util.Arrays.toString(sigmoidDerivative(result))));


    }
}

