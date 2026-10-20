package dev.mlua.loaderapi;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WorldGenerationBridgeTest {
    @Test
    void generatedBiomeModifierCanBeRemovedOnlyByItsMatchingRegistration() {
        String owner = "mlua_test_" + UUID.randomUUID().toString().replace("-", "");
        String id = "smoke";
        String fullId = owner + ":" + id;
        String resourceType = "neoforge/biome_modifier";
        String featureId = "minecraft:ore_diamond";
        List<String> biomes = List.of("#minecraft:is_overworld", "minecraft:plains");
        WorldGenerationBridge bridge = LoaderWorldGenerationBridges.create("neoforge");

        bridge.addPlacedFeature(owner, id, featureId, biomes, "UNDERGROUND_ORES");
        try {
            JsonObject created = GeneratedDataResources.get(resourceType, fullId);
            assertNotNull(created);
            assertEquals("neoforge:add_features", created.get("type").getAsString());
            assertEquals(featureId, created.get("features").getAsString());
            assertEquals(2, created.getAsJsonArray("biomes").size());
            assertEquals(1, bridge.featureCounts(owner).forAddon());
            assertFalse(bridge.removePlacedFeature(owner, id, "minecraft:ore_iron", biomes,
                "underground_ores"), "rollback must preserve a definition that does not match");
            assertTrue(GeneratedDataResources.contains(resourceType, fullId));

            assertTrue(bridge.removePlacedFeature(owner, id, featureId, biomes, "underground_ores"));
            assertFalse(GeneratedDataResources.contains(resourceType, fullId));
            assertEquals(0, bridge.featureCounts(owner).forAddon());
            assertFalse(bridge.removePlacedFeature(owner, id, featureId, biomes, "underground_ores"));
        } finally {
            GeneratedDataResources.unregister(owner, resourceType, fullId);
        }
    }
}
