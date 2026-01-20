package io.github.cantstop.model.ai.Hybrid_Model;

// Represents a single fully connected layer in the ANN
// Handles weight initialisation, signal forward pass and gradient based learning
public class Layer {
    int numInputs;
    int numNeurons;
    double[][] weights;
    double[] biases;
    double[] lastInputs;
    double[] lastZ;
    double[] lastActivations;

    // Initialises the layer with random weights and small positive biases
    // Weights are centered around 0 (-0.5 to 0.5) to prevent early saturation
    public Layer(int numInputs, int numNeurons){
        this.numNeurons = numNeurons;
        this.numInputs = numInputs;
        weights = new double[numNeurons][numInputs];
        biases = new double[numNeurons];

        for (int i = 0; i < numNeurons; i++){
            for (int j = 0; j < numInputs; j ++){
                weights[i][j] = Math.random() - 0.5;
            }
            biases[i] = 0.1;
        }
    }

    // Calculates the layer's output using MatrixMath class
    // Logic: output = sigmoid( (inputs * weights) + bias )
    public double[] forwardPass(double[] inputs){
        lastInputs = inputs;
        lastZ = MatrixMath.addBias(biases, MatrixMath.dotProduct(inputs, weights));
        lastActivations = MatrixMath.sigmoid(lastZ.clone());
        return lastActivations;
    }

    // Updates weights and biases based on the error received from the next layer
    // Calculates the local gradient (delta) and returns the error to be passed backwards
    public double[] backpropagate(double[] errorGradient, double learningRate) {
        double[] derivative = MatrixMath.sigmoidDerivative(lastActivations.clone());
        double[] delta = MatrixMath.multiplyElements(errorGradient, derivative);

        double[] nextErrorGradient = MatrixMath.transposeDotProduct(delta, weights);

        for (int i = 0; i < numNeurons; i++) {
            for (int j = 0; j < numInputs; j++) {
                weights[i][j] -= learningRate * delta[i] * lastInputs[j];
            }
            biases[i] -= learningRate * delta[i];
        }

        return nextErrorGradient;
    }

    // Serialises weights and biases into a comma-separated string for file storage
    public String exportLayer() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < weights.length; i++) {
            for (int j = 0; j < weights[i].length; j++) {
                sb.append(weights[i][j]);
                if (i != weights.length - 1 || j != weights[i].length - 1) sb.append(",");
            }
        }
        sb.append("\n");

        for (int i = 0; i < biases.length; i++) {
            sb.append(biases[i]);
            if (i != biases.length - 1) sb.append(",");
        }
        sb.append("\n");
        return sb.toString();
    }

    // Injects externally loaded weights and biases, used when loading the trained model in HybridModel class
    public void importLayer(double[][] newWeights, double[] newBiases) {
        this.weights = newWeights;
        this.biases = newBiases;
    }
}
