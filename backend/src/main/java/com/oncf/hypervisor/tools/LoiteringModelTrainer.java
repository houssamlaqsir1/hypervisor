package com.oncf.hypervisor.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncf.hypervisor.service.behavior.LoiteringModelData;
import com.oncf.hypervisor.service.behavior.TrackPoint;
import com.oncf.hypervisor.service.behavior.TrajectoryFeatureExtractor;
import com.oncf.hypervisor.service.behavior.TrajectoryFeatures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Offline trainer for the loitering behavior model used by
 * {@link com.oncf.hypervisor.service.correlation.rules.LoiteringBehaviorRule}.
 *
 * <p>There is no historical incident data to learn from yet, so this trains
 * on <b>synthetic</b> trajectories generated from two hand-specified motion
 * models:
 * <ul>
 *     <li><b>LOITERING</b> (positive class) — a small, jittery random walk
 *     confined to a few metres, sustained for minutes: someone standing
 *     around or pacing in place.</li>
 *     <li><b>NORMAL_TRANSIT</b> (negative class) — steady directional
 *     walking speed covering real ground over a short encounter: someone
 *     passing through the camera's view.</li>
 * </ul>
 * Both classes include some overlap (slow strolls on the transit side,
 * pacing on the loitering side) so the classifier has to actually weigh the
 * features instead of learning a trivial separator.
 *
 * <p>This is a deliberate bootstrap strategy: the feature extraction, model
 * shape, and inference code are all identical to what will run against real
 * data once enough is collected — only the training set needs to be
 * swapped out.
 *
 * <p>Run manually (not part of the Spring Boot app) from the {@code backend}
 * module, e.g.:
 * <pre>
 * mvnw dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 * javac -cp target/classes -d target/classes src/main/java/com/oncf/hypervisor/tools/LoiteringModelTrainer.java
 * java -cp "target/classes;$(cat target/cp.txt)" com.oncf.hypervisor.tools.LoiteringModelTrainer
 * </pre>
 * It writes {@code src/main/resources/ml/loitering-model.json}.
 */
public final class LoiteringModelTrainer {

    /** Arbitrary reference point (Casa-Voyageurs tracks) — absolute location doesn't affect the features, only relative movement does. */
    private static final double BASE_LAT = 33.5983;
    private static final double BASE_LON = -7.5805;
    private static final double METERS_PER_DEG_LAT = 111_320.0;

    private static final int TRAIN_PER_CLASS = 1500;
    private static final int TEST_PER_CLASS = 400;
    private static final long SEED_TRAIN = 42L;
    private static final long SEED_TEST = 1337L;
    private static final double LEARNING_RATE = 0.3;
    private static final int EPOCHS = 4000;
    private static final double L2_LAMBDA = 0.01;
    private static final double DECISION_THRESHOLD = 0.6;

    public static void main(String[] args) throws IOException {
        List<double[]> trainX = new ArrayList<>();
        List<Integer> trainY = new ArrayList<>();
        generateDataset(SEED_TRAIN, TRAIN_PER_CLASS, trainX, trainY);

        double[] means = columnMeans(trainX);
        double[] stds = columnStds(trainX, means);
        double[][] trainXs = standardizeAll(trainX, means, stds);

        double[] weights = new double[trainXs[0].length];
        double[] biasHolder = new double[1];
        trainLogisticRegression(trainXs, trainY, weights, biasHolder, LEARNING_RATE, EPOCHS, L2_LAMBDA);
        double bias = biasHolder[0];

        List<double[]> testX = new ArrayList<>();
        List<Integer> testY = new ArrayList<>();
        generateDataset(SEED_TEST, TEST_PER_CLASS, testX, testY);
        double[][] testXs = standardizeAll(testX, means, stds);

        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (int i = 0; i < testXs.length; i++) {
            double p = predict(testXs[i], weights, bias);
            boolean predicted = p >= DECISION_THRESHOLD;
            boolean actual = testY.get(i) == 1;
            if (predicted && actual) tp++;
            else if (predicted) fp++;
            else if (actual) fn++;
            else tn++;
        }
        double accuracy = (tp + tn) / (double) (tp + tn + fp + fn);
        double precision = (tp + fp) == 0 ? 0 : tp / (double) (tp + fp);
        double recall = (tp + fn) == 0 ? 0 : tp / (double) (tp + fn);

        System.out.printf(Locale.ROOT,
                "Test set: accuracy=%.4f precision=%.4f recall=%.4f (tp=%d fp=%d tn=%d fn=%d)%n",
                accuracy, precision, recall, tp, fp, tn, fn);
        System.out.println("Weights: " + java.util.Arrays.toString(weights) + " bias=" + bias);

        LoiteringModelData data = new LoiteringModelData();
        data.modelVersion = "loitering-v1-synthetic";
        data.trainedAt = Instant.now().toString();
        data.featureNames = TrajectoryFeatures.FEATURE_NAMES;
        data.means = means;
        data.stds = stds;
        data.weights = weights;
        data.bias = bias;
        data.recommendedThreshold = DECISION_THRESHOLD;
        data.trainSamples = TRAIN_PER_CLASS * 2;
        data.testAccuracy = accuracy;
        data.testPrecision = precision;
        data.testRecall = recall;

        Path outPath = Path.of("src", "main", "resources", "ml", "loitering-model.json");
        Files.createDirectories(outPath.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outPath.toFile(), data);
        System.out.println("Wrote " + outPath.toAbsolutePath());
    }

    // ---- synthetic data generation -----------------------------------

    private static void generateDataset(long seed, int perClass, List<double[]> outX, List<Integer> outY) {
        Random rng = new Random(seed);
        for (int i = 0; i < perClass; i++) {
            TrajectoryFeatures f = TrajectoryFeatureExtractor.extract(generateLoiteringTrack(rng));
            outX.add(f.toVector());
            outY.add(1);
        }
        for (int i = 0; i < perClass; i++) {
            TrajectoryFeatures f = TrajectoryFeatureExtractor.extract(generateTransitTrack(rng));
            outX.add(f.toVector());
            outY.add(0);
        }
    }

    /** Small jittery random walk sustained for minutes — someone standing around or pacing. */
    private static List<TrackPoint> generateLoiteringTrack(Random rng) {
        int durationSec = 150 + rng.nextInt(900 - 150);
        int numPoints = 5 + rng.nextInt(16);
        // 15% of loitering samples are a wider "pacing" pattern to overlap with transit.
        double jitterRadiusM = rng.nextDouble() < 0.15 ? 6.0 + rng.nextDouble() * 9.0 : 1.5 + rng.nextDouble() * 6.5;

        double centerX = rng.nextGaussian() * 5;
        double centerY = rng.nextGaussian() * 5;

        long[] times = sortedTimes(rng, durationSec, numPoints);
        List<TrackPoint> points = new ArrayList<>(numPoints);
        double x = centerX, y = centerY;
        for (long t : times) {
            // Small random walk step that stays anchored near the center (mean-reverting).
            x += rng.nextGaussian() * jitterRadiusM * 0.35 - 0.15 * (x - centerX);
            y += rng.nextGaussian() * jitterRadiusM * 0.35 - 0.15 * (y - centerY);
            points.add(offset(x, y, t));
        }
        return points;
    }

    /** Steady directional walking speed covering real ground — someone passing through. */
    private static List<TrackPoint> generateTransitTrack(Random rng) {
        int durationSec = 15 + rng.nextInt(165);
        int numPoints = 3 + rng.nextInt(8);
        // 15% of transit samples are a slow stroll to overlap with loitering.
        double speedMps = rng.nextDouble() < 0.15 ? 0.3 + rng.nextDouble() * 0.5 : 0.8 + rng.nextDouble() * 1.2;
        double heading = rng.nextDouble() * 2 * Math.PI;

        long[] times = sortedTimes(rng, durationSec, numPoints);
        List<TrackPoint> points = new ArrayList<>(numPoints);
        for (long t : times) {
            double dist = speedMps * t;
            double headingNoise = rng.nextGaussian() * 0.15;
            double x = Math.cos(heading + headingNoise) * dist;
            double y = Math.sin(heading + headingNoise) * dist;
            points.add(offset(x, y, t));
        }
        return points;
    }

    private static long[] sortedTimes(Random rng, int durationSec, int numPoints) {
        long[] times = new long[numPoints];
        times[0] = 0;
        times[numPoints - 1] = durationSec;
        for (int i = 1; i < numPoints - 1; i++) {
            times[i] = (long) (rng.nextDouble() * durationSec);
        }
        java.util.Arrays.sort(times);
        return times;
    }

    private static TrackPoint offset(double xMeters, double yMeters, long epochSeconds) {
        double lat = BASE_LAT + yMeters / METERS_PER_DEG_LAT;
        double lon = BASE_LON + xMeters / (METERS_PER_DEG_LAT * Math.cos(Math.toRadians(BASE_LAT)));
        return new TrackPoint(lat, lon, epochSeconds);
    }

    // ---- logistic regression (batch gradient descent) -----------------

    private static void trainLogisticRegression(double[][] x, List<Integer> y,
                                                double[] weights, double[] biasOut,
                                                double lr, int epochs, double l2Lambda) {
        int n = x.length;
        int d = weights.length;
        double bias = 0;
        for (int epoch = 0; epoch < epochs; epoch++) {
            double[] gradW = new double[d];
            double gradB = 0;
            for (int i = 0; i < n; i++) {
                double z = bias;
                for (int j = 0; j < d; j++) z += weights[j] * x[i][j];
                double p = 1.0 / (1.0 + Math.exp(-z));
                double err = p - y.get(i);
                for (int j = 0; j < d; j++) gradW[j] += err * x[i][j];
                gradB += err;
            }
            for (int j = 0; j < d; j++) {
                weights[j] -= lr * (gradW[j] / n + l2Lambda * weights[j]);
            }
            bias -= lr * (gradB / n);
        }
        biasOut[0] = bias;
    }

    private static double predict(double[] standardisedFeatures, double[] weights, double bias) {
        double z = bias;
        for (int j = 0; j < weights.length; j++) z += weights[j] * standardisedFeatures[j];
        return 1.0 / (1.0 + Math.exp(-z));
    }

    // ---- standardisation helpers ---------------------------------------

    private static double[] columnMeans(List<double[]> x) {
        int d = x.get(0).length;
        double[] means = new double[d];
        for (double[] row : x) {
            for (int j = 0; j < d; j++) means[j] += row[j];
        }
        for (int j = 0; j < d; j++) means[j] /= x.size();
        return means;
    }

    private static double[] columnStds(List<double[]> x, double[] means) {
        int d = means.length;
        double[] variance = new double[d];
        for (double[] row : x) {
            for (int j = 0; j < d; j++) {
                double diff = row[j] - means[j];
                variance[j] += diff * diff;
            }
        }
        double[] stds = new double[d];
        for (int j = 0; j < d; j++) {
            stds[j] = Math.sqrt(variance[j] / x.size());
            if (stds[j] == 0) stds[j] = 1.0;
        }
        return stds;
    }

    private static double[][] standardizeAll(List<double[]> x, double[] means, double[] stds) {
        double[][] result = new double[x.size()][];
        for (int i = 0; i < x.size(); i++) {
            double[] row = x.get(i);
            double[] std = new double[row.length];
            for (int j = 0; j < row.length; j++) {
                std[j] = (row[j] - means[j]) / stds[j];
            }
            result[i] = std;
        }
        return result;
    }

    private LoiteringModelTrainer() {
        /* CLI tool */
    }
}
