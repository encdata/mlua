package dev.mlua.runtime.client;

import java.nio.charset.StandardCharsets;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaEmbeddedTextureLocationTest {
    @Test
    void distinctImagesWithCollidingJavaHashesGetDistinctLocations() {
        byte[] firstImage = "Aa".getBytes(StandardCharsets.UTF_8);
        byte[] secondImage = "BB".getBytes(StandardCharsets.UTF_8);
        assertEquals("Aa".hashCode(), "BB".hashCode());

        ResourceLocation first = MLuaClientRenderManager.embeddedTextureLocation(firstImage, 0);
        ResourceLocation second = MLuaClientRenderManager.embeddedTextureLocation(secondImage, 0);

        assertNotEquals(first, second);
    }
}
