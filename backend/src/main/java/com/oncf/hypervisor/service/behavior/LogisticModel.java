package com.oncf.hypervisor.service.behavior;

/**
 * A trained binary logistic regression: standardises raw features with the
 * training-set mean/std, then applies a linear combination + sigmoid.
 *
 * <p>Deliberately tiny and dependency-free (no ML runtime, no ONNX) so it
 * can be embedded directly in the Spring app — the "model" is just five
 * numbers and a bias, learned offline by {@code LoiteringModelTrainer} on
 * synthetic trajectories and shipped as a JSON resource.
 */
public final class LogisticModel {

    private final double[] means;
    private final double[] stds;
    private final double[] weights;
    private final double bias;

    public LogisticModel(double[] means, double[] stds, double[] weights, double bias) {
        if (means.length != stds.length || means.length != weights.length) {
            throw new IllegalArgumentException("means/stds/weights must have the same length");
        }
        this.means = means;
        this.stds = stds;
        this.weights = weights;
        this.bias = bias;
    }

    /** Returns the predicted probability (0..1) that the input belongs to the positive class. */
    public double predictProba(double[] rawFeatures) {
        if (rawFeatures.length != weights.length) {
            throw new IllegalArgumentException("expected " + weights.length + " features, got " + rawFeatures.length);
        }
        double z = bias;
        for (int i = 0; i < weights.length; i++) {
            double std = stds[i] == 0 ? 1.0 : stds[i];
            double standardised = (rawFeatures[i] - means[i]) / std;
            z += weights[i] * standardised;
        }
        return sigmoid(z);
    }

    static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }
}
