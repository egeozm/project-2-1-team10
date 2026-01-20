package io.github.cantstop.model.ai.Hybrid_Model;

// A data transfer object representing a single training instance
// Bundles the board state inputs with their corresponding game outcome (win/lose)
public class CSVRow {
    // These are the 35 input features
    private double[] features;
    // This is the win/lose label used for error calculation
    private double[] label;

    // Constructs a training sample with pre-parsed numerical data
    public CSVRow(double[] features, double[] label) {
        this.features = features;
        this.label = label;
    }

    // Returns the input feature vector for the network's input layer
    public double[] getFeatures() { return features; }

    // Returns the target value for error calculation during training
    public double[] getLabel() { return label; }
}
