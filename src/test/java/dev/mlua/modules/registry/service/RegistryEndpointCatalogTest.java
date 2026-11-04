package dev.mlua.modules.registry.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class RegistryEndpointCatalogTest {
    @Test
    void genericAssetRegistrationUsesCanonicalMethodCasing() {
        RegistryEndpointCatalog catalog = new RegistryEndpointCatalog();

        RegistryEndpoint assets = catalog.builtIn("assets");

        assertNotNull(assets);
        assertEquals("Register", assets.registerMethod());
        assertEquals(2, assets.genericArgumentCount());
    }

    @Test
    void genericWorldGenerationRegistrationKeepsFeatureArgumentShape() {
        RegistryEndpointCatalog catalog = new RegistryEndpointCatalog();

        RegistryEndpoint worldGeneration = catalog.builtIn("world_generation");

        assertNotNull(worldGeneration);
        assertEquals("addFeature", worldGeneration.registerMethod());
        assertEquals(2, worldGeneration.genericArgumentCount());
    }
}
