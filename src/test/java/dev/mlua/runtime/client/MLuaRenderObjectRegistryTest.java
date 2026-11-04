package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRenderObjectRegistryTest {
    @Test
    void removingObjectAlsoReleasesItsOwner() {
        var registry = new MLuaRenderObjectRegistry();
        var owner = new MLuaRenderOwnershipRegistry.Owner(new Object(), "test-addon");
        int id = registry.nextId();
        var object = new MLuaClientRenderManager.RenderObject(
            id, MLuaClientRenderManager.RenderType.RECT, null, false, "AFTER_ENTITIES");

        assertEquals(id, registry.publish(object, owner));
        assertTrue(registry.owns(id, owner));

        assertSame(object, registry.remove(id));
        assertNull(registry.get(id));
        assertNull(registry.ownerOf(id));
        assertFalse(registry.owns(id, owner));
        assertEquals(java.util.List.of(), registry.ids(owner));
    }
}
