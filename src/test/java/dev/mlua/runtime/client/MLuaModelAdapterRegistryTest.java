package dev.mlua.runtime.client;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModelAdapterRegistryTest {
    private final MLuaModelAdapterRegistry.Owner addonA = new MLuaModelAdapterRegistry.Owner(new Object(), "addon_a");
    private final MLuaModelAdapterRegistry.Owner addonB = new MLuaModelAdapterRegistry.Owner(new Object(), "addon_b");

    @AfterEach
    void clearAdapters() {
        MLuaModelAdapterRegistry.clearOwned(addonA);
        MLuaModelAdapterRegistry.clearOwned(addonB);
    }

    @Test
    void addonsCanContributeDifferentBonesToTheSameModelClass() {
        String modelClass = SharedModel.class.getName();
        MLuaModelAdapterRegistry.register(modelClass, Map.of("head", "head"), addonA);
        MLuaModelAdapterRegistry.register(modelClass, Map.of("rightHand", "rightArm"), addonB);

        assertEquals(Map.of("head", "head", "rightHand", "rightArm"),
            MLuaModelAdapterRegistry.findMapping(SharedModel.class));
        assertTrue(MLuaModelAdapterRegistry.unregister(modelClass, addonA));
        assertEquals(Map.of("rightHand", "rightArm"),
            MLuaModelAdapterRegistry.findMapping(SharedModel.class));
    }

    @Test
    void duplicateBoneNamesCannotReplaceAnotherAddonsMapping() {
        String modelClass = ConflictingModel.class.getName();
        MLuaModelAdapterRegistry.register(modelClass, Map.of("head", "head"), addonA);

        assertThrows(IllegalStateException.class,
            () -> MLuaModelAdapterRegistry.register(modelClass, Map.of("head", "hat"), addonB));
        assertEquals(Map.of("head", "head"),
            MLuaModelAdapterRegistry.findMapping(ConflictingModel.class));
    }

    @Test
    void subclassMappingsOverrideInheritedBoneNames() {
        MLuaModelAdapterRegistry.register(ParentModel.class.getName(), Map.of("head", "head"), addonA);
        MLuaModelAdapterRegistry.register(ChildModel.class.getName(), Map.of("head", "hat"), addonB);

        assertEquals(Map.of("head", "hat"), MLuaModelAdapterRegistry.findMapping(ChildModel.class));
    }

    private static class SharedModel {}
    private static class ConflictingModel {}
    private static class ParentModel {}
    private static final class ChildModel extends ParentModel {}
}
