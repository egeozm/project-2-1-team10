package io.github.cantstop.model.ai.Hybrid_Model;

public class Layer {
    int numInputs;
    int numNeurons;
    double[][] weights;
    double[] biases;
    double[] lastInputs;
    double[] lastZ;
    double[] lastActivations;
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

    public double[] forwardPass(double[] inputs){
        lastInputs = inputs;
        lastZ = MatrixMath.addBias(biases, MatrixMath.dotProduct(inputs, weights));
        lastActivations = MatrixMath.sigmoid(lastZ.clone());
        return lastActivations;
    }

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

    public void importLayer(double[][] newWeights, double[] newBiases) {
        this.weights = newWeights;
        this.biases = newBiases;
    }
}
