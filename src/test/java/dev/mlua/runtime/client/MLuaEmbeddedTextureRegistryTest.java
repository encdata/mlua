package dev.mlua.runtime.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaEmbeddedTextureRegistryTest {
    @Test
    void sharedTextureIsReleasedOnlyAfterItsLastRenderObject() {
        var registry = new MLuaEmbeddedTextureRegistry();
        ResourceLocation location = dev.mlua.minecraft.MLuaResourceLocations.build("mlua", "bbmodel/shared/0");

        assertTrue(registry.acquire(location));
        assertFalse(registry.acquire(location));
        assertFalse(registry.release(location));
        assertTrue(registry.release(location));
        assertFalse(registry.release(location));
    }
}
