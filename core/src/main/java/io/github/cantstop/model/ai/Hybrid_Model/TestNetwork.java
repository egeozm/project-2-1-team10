package io.github.cantstop.model.ai.Hybrid_Model;

// This is a class that was used to test the ANN and MatrixMath while implementing it
public class TestNetwork {
    public static void main(String[] args) {
        double[] inputs = new double[]{0,1};
        double[][] weights = new double[][]{{0.4,0.6},{0.3,0.9},{0.7,0.6}};
        double[] neurons = MatrixMath.dotProduct(inputs, weights);
        System.out.println(java.util.Arrays.toString(neurons));

        double[] biases = new double[]{0.3, 0.1, 0.6};
        double[] result = MatrixMath.addBias(biases, neurons);
        System.out.println(java.util.Arrays.toString(result));

        System.out.println((java.util.Arrays.toString(MatrixMath.sigmoid(result))));

        System.out.println((java.util.Arrays.toString(MatrixMath.sigmoidDerivative(result))));

        System.out.println("----------------");

        int dataInputs = 35;
        int hiddenLayer1 = 128;
        int hiddenLayer2 = 64;
        int outputs = 1;

        NeuralNetwork network = new NeuralNetwork(dataInputs, hiddenLayer1, hiddenLayer2, outputs);

        double[] fakeBoardState = new double[35];
        for(int i = 0; i < fakeBoardState.length; i++) {
            fakeBoardState[i] = Math.random();
        }

        double[] prediction = network.predict(fakeBoardState);

        System.out.println(prediction[0]);

    }
}
