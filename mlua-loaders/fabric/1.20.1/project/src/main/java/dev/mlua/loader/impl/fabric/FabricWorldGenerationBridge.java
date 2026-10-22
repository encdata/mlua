package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.WorldGenerationBridge;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.biome.v1.BiomeModification;
import net.fabricmc.fabric.api.biome.v1.BiomeModificationContext;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectionContext;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Owns MLua biome feature contributions behind a stable Fabric API modifier per
 * addon. The Fabric API has no unregister operation, so each modifier reads
 * this live catalog when biome settings are built and rollback removes entries.
 */
public final class FabricWorldGenerationBridge implements WorldGenerationBridge {
    private static final Object LOCK = new Object();
    private static final Map<String, FeatureRegistration> REGISTRATIONS = new LinkedHashMap<>();
    private static final Map<String, Integer> REGISTRATIONS_BY_OWNER = new HashMap<>();
    private static final Map<String, List<FeatureRegistration>> OWNER_REGISTRATION_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Set<String> INSTALLED_MODIFIERS = new LinkedHashSet<>();
    private static final ThreadLocal<Map<String, List<FeatureRegistration>>> SELECTED_FEATURES =
        ThreadLocal.withInitial(HashMap::new);

    @Override
    public void addPlacedFeature(String owner, String name, String featureId, List<String> biomes, String step) {
        FeatureRegistration registration = registration(owner, name, featureId, biomes, step);
        ensureModifierInstalled(owner);
        synchronized (LOCK) {
            if (REGISTRATIONS.containsKey(registration.id())) {
                throw new IllegalStateException("World generation feature is already registered: " + registration.id());
            }
            if (REGISTRATIONS.size() >= MAX_PLACED_FEATURES_TOTAL) {
                throw new IllegalStateException("MLua placed feature registration limit reached ("
                    + MAX_PLACED_FEATURES_TOTAL + ")");
            }
            int ownerCount = REGISTRATIONS_BY_OWNER.getOrDefault(owner, 0);
            if (ownerCount >= MAX_PLACED_FEATURES_PER_ADDON) {
                throw new IllegalStateException("Placed feature registration limit reached for addon '" + owner
                    + "' (" + MAX_PLACED_FEATURES_PER_ADDON + ")");
            }
            REGISTRATIONS.put(registration.id(), registration);
            REGISTRATIONS_BY_OWNER.put(owner, ownerCount + 1);
            List<FeatureRegistration> ownerRegistrations = new ArrayList<>(
                OWNER_REGISTRATION_SNAPSHOTS.getOrDefault(owner, List.of()));
            ownerRegistrations.add(registration);
            ownerRegistrations.sort(java.util.Comparator.comparing(FeatureRegistration::id));
            OWNER_REGISTRATION_SNAPSHOTS.put(owner, List.copyOf(ownerRegistrations));
        }
    }

    @Override
    public boolean removePlacedFeature(String owner, String name, String featureId, List<String> biomes, String step) {
        FeatureRegistration expected = registration(owner, name, featureId, biomes, step);
        synchronized (LOCK) {
            FeatureRegistration current = REGISTRATIONS.get(expected.id());
            if (current == null || !current.sameDefinition(expected)) return false;
            REGISTRATIONS.remove(expected.id());
            int ownerCount = REGISTRATIONS_BY_OWNER.getOrDefault(owner, 1) - 1;
            if (ownerCount <= 0) REGISTRATIONS_BY_OWNER.remove(owner);
            else REGISTRATIONS_BY_OWNER.put(owner, ownerCount);
            List<FeatureRegistration> remaining = REGISTRATIONS_BY_OWNER.getOrDefault(owner, 0) == 0
                ? List.of()
                : OWNER_REGISTRATION_SNAPSHOTS.getOrDefault(owner, List.of()).stream()
                    .filter(registration -> !registration.id().equals(expected.id())).toList();
            if (remaining.isEmpty()) OWNER_REGISTRATION_SNAPSHOTS.remove(owner);
            else OWNER_REGISTRATION_SNAPSHOTS.put(owner, remaining);
            return true;
        }
    }

    @Override
    public FeatureCounts featureCounts(String owner) {
        synchronized (LOCK) {
            return new FeatureCounts(REGISTRATIONS.size(), REGISTRATIONS_BY_OWNER.getOrDefault(owner, 0));
        }
    }

    private static FeatureRegistration registration(String owner, String name, String featureId,
                                                    List<String> biomes, String step) {
        if (owner == null || owner.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("World generation owner and id must not be empty");
        }
        MLuaResourceLocations.parse(owner + ":" + name);
        if (featureId == null || featureId.isBlank()) {
            throw new IllegalArgumentException("Placed feature id must not be empty");
        }
        if (biomes == null || biomes.isEmpty()) {
            throw new IllegalArgumentException("At least one biome id or tag is required");
        }
        List<String> selectors = List.copyOf(biomes);
        List<Predicate<BiomeSelectionContext>> predicates = new ArrayList<>(selectors.size());
        for (String biome : selectors) {
            if (biome == null || biome.isBlank()) throw new IllegalArgumentException("Biome selector must not be empty");
            if (biome.startsWith("#")) {
                predicates.add(BiomeSelectors.tag(TagKey.create(Registries.BIOME,
                    MLuaResourceLocations.parse(biome.substring(1)))));
            } else {
                predicates.add(BiomeSelectors.includeByKey(ResourceKey.create(Registries.BIOME,
                    MLuaResourceLocations.parse(biome))));
            }
        }
        String normalizedStep = WorldGenerationBridge.normalizeStep(step);
        GenerationStep.Decoration generationStep;
        try {
            generationStep = GenerationStep.Decoration.valueOf(normalizedStep.toUpperCase(Locale.ROOT));
        } catch (RuntimeException invalidStep) {
            throw new IllegalArgumentException("Unknown world generation step: " + step, invalidStep);
        }
        return new FeatureRegistration(owner, name, featureId, selectors, normalizedStep,
            ResourceKey.create(Registries.PLACED_FEATURE, MLuaResourceLocations.parse(featureId)),
            generationStep, List.copyOf(predicates));
    }

    private static void ensureModifierInstalled(String owner) {
        synchronized (LOCK) {
            if (INSTALLED_MODIFIERS.contains(owner)) return;
            BiomeModification modification = BiomeModifications.create(
                MLuaResourceLocations.parse(owner + ":_mlua/scripted_world_features"));
            modification.add(ModificationPhase.ADDITIONS, context -> matchesAny(owner, context),
                context -> applyFeatures(owner, context));
            INSTALLED_MODIFIERS.add(owner);
        }
    }

    private static boolean matchesAny(String owner, BiomeSelectionContext context) {
        ArrayList<FeatureRegistration> selected = null;
        for (FeatureRegistration registration : registrationsSnapshot(owner)) {
            if (!registration.matches(context)) continue;
            if (selected == null) selected = new ArrayList<>();
            selected.add(registration);
        }
        Map<String, List<FeatureRegistration>> selections = SELECTED_FEATURES.get();
        if (selected == null) {
            selections.remove(owner);
            return false;
        }
        selections.put(owner, List.copyOf(selected));
        return true;
    }

    private static List<FeatureRegistration> registrationsSnapshot(String owner) {
        return OWNER_REGISTRATION_SNAPSHOTS.getOrDefault(owner, List.of());
    }

    private static void applyFeatures(String owner, BiomeModificationContext context) {
        List<FeatureRegistration> selected = SELECTED_FEATURES.get().remove(owner);
        if (selected == null) return;
        BiomeModificationContext.GenerationSettingsContext settings = context.getGenerationSettings();
        for (FeatureRegistration registration : selected) {
            try {
                settings.addFeature(registration.generationStep(), registration.featureKey());
            } catch (RuntimeException failure) {
                throw new IllegalStateException("Fabric could not apply placed feature " + registration.id(), failure);
            }
        }
    }

    private record FeatureRegistration(String owner, String name, String featureId, List<String> biomes,
                                       String step, ResourceKey<PlacedFeature> featureKey,
                                       GenerationStep.Decoration generationStep,
                                       List<Predicate<BiomeSelectionContext>> predicates) {
        private String id() { return owner + ":" + name; }

        private boolean matches(BiomeSelectionContext context) {
            for (Predicate<BiomeSelectionContext> selector : predicates) {
                if (selector.test(context)) return true;
            }
            return false;
        }

        private boolean sameDefinition(FeatureRegistration other) {
            return owner.equals(other.owner) && name.equals(other.name)
                && featureId.equals(other.featureId) && biomes.equals(other.biomes) && step.equals(other.step);
        }
    }
}
