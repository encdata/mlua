package dev.mlua.loaderapi;

import java.util.List;
import java.util.Locale;

public interface WorldGenerationBridge {
    List<String> DECORATION_STEPS = List.of(
        "RAW_GENERATION", "LAKES", "LOCAL_MODIFICATIONS", "UNDERGROUND_STRUCTURES",
        "SURFACE_STRUCTURES", "STRONGHOLDS", "UNDERGROUND_ORES", "UNDERGROUND_DECORATION",
        "FLUID_SPRINGS", "VEGETAL_DECORATION", "TOP_LAYER_MODIFICATION");

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
