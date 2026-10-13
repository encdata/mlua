package dev.mlua.loaderapi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;

public final class LoaderWorldGenerationBridges {
    private LoaderWorldGenerationBridges() { }

    public static WorldGenerationBridge create(String loaderName) {
        String loader = loaderName == null ? "" : loaderName.toLowerCase(Locale.ROOT);
        String resourceType = switch (loader) {
            case "forge" -> "forge/biome_modifier";
            case "neoforge" -> "neoforge/biome_modifier";
            default -> throw new UnsupportedOperationException("World generation bridge is not available for loader '" + loaderName + "'");
        };
        String modifierType = loader + ":add_features";
        return (owner, name, featureId, biomes, step) -> {
            if (biomes == null || biomes.isEmpty()) throw new IllegalArgumentException("At least one biome id or tag is required");
            JsonObject definition = new JsonObject();
            definition.addProperty("type", modifierType);
            if (biomes.size() == 1) definition.addProperty("biomes", biomes.get(0));
            else {
                JsonArray biomeValues = new JsonArray();
                biomes.forEach(biomeValues::add);
                definition.add("biomes", biomeValues);
            }
            definition.addProperty("features", featureId);
            definition.addProperty("step", step.toLowerCase(Locale.ROOT));
            GeneratedDataResources.register(owner, resourceType, name, definition);
        };
    }
}
