package dev.mlua.loaderapi;

import java.util.List;

public interface WorldGenerationBridge {
    void addPlacedFeature(String owner, String name, String featureId, List<String> biomes, String step);
}
