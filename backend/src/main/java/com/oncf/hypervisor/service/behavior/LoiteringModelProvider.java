package com.oncf.hypervisor.service.behavior;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads the offline-trained loitering model (see {@code tools/LoiteringModelTrainer})
 * from {@code classpath:ml/loitering-model.json} at startup and exposes it to
 * {@link com.oncf.hypervisor.service.correlation.rules.LoiteringBehaviorRule}.
 *
 * <p>The model is trained on synthetic trajectories (no real incident
 * history exists yet) — see the trainer for how the training set is
 * generated and how test accuracy is reported. Swap the JSON resource with
 * one trained on real operational data once it's available; nothing else
 * needs to change.
 */
@Component
@Slf4j
@Getter
public class LoiteringModelProvider {

    private static final String RESOURCE_PATH = "ml/loitering-model.json";

    private final ObjectMapper objectMapper;

    private LogisticModel model;
    private double threshold;
    private String version;

    public LoiteringModelProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            LoiteringModelData data = objectMapper.readValue(in, LoiteringModelData.class);
            this.model = new LogisticModel(data.means, data.stds, data.weights, data.bias);
            this.threshold = data.recommendedThreshold;
            this.version = data.modelVersion;
            log.info("Loaded loitering model '{}' (trained {}, {} samples, test accuracy {}, threshold {})",
                    data.modelVersion, data.trainedAt, data.trainSamples, data.testAccuracy, data.recommendedThreshold);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load " + RESOURCE_PATH + " — run LoiteringModelTrainer to (re)generate it", ex);
        }
    }

    /** Predicted probability (0..1) that the given track is loitering behavior. */
    public double predict(TrajectoryFeatures features) {
        return model.predictProba(features.toVector());
    }
}
