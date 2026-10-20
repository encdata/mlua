package dev.mlua.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MLuaAddonBuilderRegistrationTest {
    @Test
    void itemBuilderHandleIsIdempotentAndCannotRemoveLaterRegistration() {
        var bridge = new dev.mlua.api.RuntimeMLuaApiBridge();
        var first = bridge.registerItemType("test_addon", "test_addon:bolt", "Test bolt",
            new dev.mlua.api.registry.MLuaItemTypeProvider() {
                @Override public net.minecraft.world.item.Item createNamed(
                        java.util.Map<String, Object> definition, net.minecraft.world.item.Item.Properties properties,
                        String registryName) { return null; }
                @Override public net.minecraft.world.item.Item createScripted(
                        java.util.Map<String, Object> definition, net.minecraft.world.item.Item.Properties properties,
                        String behaviorKey) { return null; }
            });
        assertTrue(MLuaItemFactory.registeredTypes().containsKey("test_addon:bolt"));
        first.close();
        assertFalse(MLuaItemFactory.registeredTypes().containsKey("test_addon:bolt"));

        var second = bridge.registerItemType("test_addon", "test_addon:bolt", "Replacement bolt",
            new dev.mlua.api.registry.MLuaItemTypeProvider() {
                @Override public net.minecraft.world.item.Item createNamed(
                        java.util.Map<String, Object> definition, net.minecraft.world.item.Item.Properties properties,
                        String registryName) { return null; }
                @Override public net.minecraft.world.item.Item createScripted(
                        java.util.Map<String, Object> definition, net.minecraft.world.item.Item.Properties properties,
                        String behaviorKey) { return null; }
            });
        first.close();
        assertTrue(MLuaItemFactory.registeredTypes().containsKey("test_addon:bolt"));
        IllegalStateException invalidProvider = assertThrows(IllegalStateException.class,
            () -> new MLuaItemFactory().createNamed(
                java.util.Map.of("kind", "test_addon:bolt"), "test_addon:bolt"));
        assertTrue(invalidProvider.getMessage().contains("Item type provider returned null"));
        second.close();
        assertFalse(MLuaItemFactory.registeredTypes().containsKey("test_addon:bolt"));
    }

    @Test
    void blockBuilderHandleRemovesItsAddonDefinition() {
        var bridge = new dev.mlua.api.RuntimeMLuaApiBridge();
        var handle = bridge.registerBlockType("test_addon", "test_addon:container",
            "Test container", new dev.mlua.api.registry.MLuaBlockTypeProvider() {
                @Override public net.minecraft.world.level.block.Block createNamed(
                        java.util.Map<String, Object> definition,
                        net.minecraft.world.level.block.state.BlockBehaviour.Properties properties,
                        String registryName,
                        java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<?>> blockEntityType) {
                    return null;
                }
                @Override public net.minecraft.world.level.block.Block createScripted(
                        java.util.Map<String, Object> definition,
                        net.minecraft.world.level.block.state.BlockBehaviour.Properties properties,
                        String behaviorKey,
                        java.util.function.Supplier<? extends net.minecraft.world.level.block.entity.BlockEntityType<?>> blockEntityType) {
                    return null;
                }
            });
        assertTrue(MLuaBlockFactory.registeredTypes().containsKey("test_addon:container"));
        handle.close();
        assertFalse(MLuaBlockFactory.registeredTypes().containsKey("test_addon:container"));
    }
}
