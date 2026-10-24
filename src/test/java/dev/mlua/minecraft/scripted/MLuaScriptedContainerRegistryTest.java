package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedContainerRegistryTest {
    private static final String BLOCK_KEY = "mlua_test:container_rollback";
    private static final String TYPE_KEY = "mlua_test:container_type_rollback";

    @Test
    void rollbackUsesRegistrationIdentityWhenEqualDefinitionsAreReplaced() {
        MLuaBehaviorRegistry.ContainerDefinition original =
                new MLuaBehaviorRegistry.ContainerDefinition(2, "Original");
        MLuaBehaviorRegistry.ContainerDefinition replacement =
                new MLuaBehaviorRegistry.ContainerDefinition(2, "Original");
        try {
            MLuaBehaviorRegistry.registerBlockContainer(BLOCK_KEY, original);
            MLuaBehaviorRegistry.registerBlockContainer(BLOCK_KEY, replacement);

            assertFalse(MLuaBehaviorRegistry.unregisterBlockContainer(BLOCK_KEY, original));
            assertSame(replacement, MLuaBehaviorRegistry.getBlockContainer(BLOCK_KEY));
            assertTrue(MLuaBehaviorRegistry.restoreBlockContainer(BLOCK_KEY, replacement, original));
            assertSame(original, MLuaBehaviorRegistry.getBlockContainer(BLOCK_KEY));
        } finally {
            MLuaBehaviorRegistry.clearBlockCallbacks(BLOCK_KEY);
        }
    }

    @Test
    void containerTypeRemovalAlsoRequiresTheRegisteredDefinitionInstance() {
        MLuaBehaviorRegistry.ContainerDefinition registered =
                new MLuaBehaviorRegistry.ContainerDefinition(1, "Type");
        try {
            MLuaBehaviorRegistry.registerContainerType(TYPE_KEY, registered);

            assertFalse(MLuaBehaviorRegistry.unregisterContainerType(TYPE_KEY,
                    new MLuaBehaviorRegistry.ContainerDefinition(1, "Type")));
            assertSame(registered, MLuaBehaviorRegistry.getContainerType(TYPE_KEY));
            assertTrue(MLuaBehaviorRegistry.unregisterContainerType(TYPE_KEY, registered));
        } finally {
            MLuaBehaviorRegistry.unregisterContainerType(TYPE_KEY, registered);
        }
    }
}
