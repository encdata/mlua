package dev.mlua.loaderapi;

import java.util.List;
import java.util.Locale;
import java.util.Arrays;

public interface WorldGenerationBridge {
    List<String> DECORATION_STEPS = Arrays.stream(GenerationDecorationStep.values())
        .map(Enum::name).toList();

    void addPlacedFeature(String owner, String name, String featureId, List<String> biomes, String step);

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
