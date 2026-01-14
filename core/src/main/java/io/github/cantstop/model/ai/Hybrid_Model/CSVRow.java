package io.github.cantstop.model.ai.Hybrid_Model;

public class CSVRow {
    private double[] features;
    private double[] label;

    public CSVRow(double[] features, double[] label) {
        this.features = features;
        this.label = label;
    }

    public double[] getFeatures() { return features; }
    public double[] getLabel() { return label; }
}
