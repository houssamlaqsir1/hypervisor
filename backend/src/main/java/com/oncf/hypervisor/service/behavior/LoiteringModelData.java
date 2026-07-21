package com.oncf.hypervisor.service.behavior;

/**
 * Plain, Jackson-friendly mirror of {@code ml/loitering-model.json}. Public
 * mutable fields on purpose — this is a pure data-transfer shape shared
 * between the offline trainer (writer) and {@link LoiteringModelProvider}
 * (reader), nothing more.
 */
public class LoiteringModelData {
    public String modelVersion;
    public String trainedAt;
    public String[] featureNames;
    public double[] means;
    public double[] stds;
    public double[] weights;
    public double bias;
    public double recommendedThreshold;
    public int trainSamples;
    public double testAccuracy;
    public double testPrecision;
    public double testRecall;
}
