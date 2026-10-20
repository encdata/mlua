package dev.mlua.loaderapi;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;

public interface WorldGenerationBridge {
    List<String> DECORATION_STEPS = Arrays.stream(GenerationDecorationStep.values())
        .map(Enum::name).toList();
    int MAX_PLACED_FEATURES_TOTAL = GeneratedDataResources.MAX_TOTAL_RESOURCES;
    int MAX_PLACED_FEATURES_PER_ADDON = GeneratedDataResources.MAX_RESOURCES_PER_OWNER;

    record FeatureCounts(int total, int forAddon) {
        public FeatureCounts {
            if (total < 0 || forAddon < 0 || forAddon > total) {
                throw new IllegalArgumentException("World generation feature counts are invalid");
            }
        }
    }

    void addPlacedFeature(String owner, String name, String featureId, List<String> biomes, String step);

    /** Removes exactly the contribution described by the supplied definition, when still owned by MLua. */
    default boolean removePlacedFeature(String owner, String name, String featureId,
                                        List<String> biomes, String step) {
        return false;
    }

    default FeatureCounts featureCounts(String owner) {
        return new FeatureCounts(0, 0);
    }

    static String normalizeStep(String step) {
        if (step == null || step.isBlank()) throw new IllegalArgumentException("World generation step must not be empty");
        String normalized = step.trim().toUpperCase(Locale.ROOT);
        if (!DECORATION_STEPS.contains(normalized)) {
            throw new IllegalArgumentException("Unknown world generation step '" + step + "'. Expected one of: "
                + String.join(", ", DECORATION_STEPS));
        }
        return normalized.toLowerCase(Locale.ROOT);
    }
}
