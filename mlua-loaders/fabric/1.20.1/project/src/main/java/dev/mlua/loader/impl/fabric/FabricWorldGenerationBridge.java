package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.WorldGenerationBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class FabricWorldGenerationBridge implements WorldGenerationBridge {
    @Override
    public void addPlacedFeature(String owner, String name, String featureId, List<String> biomes, String step) {
        if (biomes == null || biomes.isEmpty()) throw new IllegalArgumentException("At least one biome id or tag is required");
        List<Predicate<BiomeSelectionContext>> selectors = new ArrayList<>();
        for (String value : biomes) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("Biome selector must not be empty");
            if (value.startsWith("#")) {
                selectors.add(BiomeSelectors.tag(TagKey.create(Registries.BIOME, MLuaResourceLocations.parse(value.substring(1)))));
            } else {
                selectors.add(BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME, MLuaResourceLocations.parse(value))));
            }
        }
        GenerationStep.Decoration generationStep;
        try {
            generationStep = GenerationStep.Decoration.valueOf(step.toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown world generation step: " + step, exception);
        }
        ResourceKey<PlacedFeature> featureKey = ResourceKey.create(
            Registries.PLACED_FEATURE, MLuaResourceLocations.parse(featureId));
        BiomeModification modification = BiomeModifications.create(MLuaResourceLocations.parse(owner + ":" + name));
        modification.add(ModificationPhase.ADDITIONS,
            context -> selectors.stream().anyMatch(selector -> selector.test(context)),
            context -> context.getGenerationSettings().addFeature(generationStep, featureKey));
    }
}
