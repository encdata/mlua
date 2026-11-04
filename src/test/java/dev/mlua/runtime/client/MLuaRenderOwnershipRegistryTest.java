package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRenderOwnershipRegistryTest {
    @Test
    void idsAreVisibleOnlyToTheirSessionAndMod() {
        var registry = new MLuaRenderOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var firstOwner = new MLuaRenderOwnershipRegistry.Owner(firstSession, "one");
        var otherMod = new MLuaRenderOwnershipRegistry.Owner(firstSession, "two");
        var otherSession = new MLuaRenderOwnershipRegistry.Owner(secondSession, "one");
        registry.register(1, firstOwner);
        registry.register(2, otherMod);
        registry.register(3, otherSession);

        assertEquals(java.util.List.of(1), registry.ids(firstOwner));
        assertTrue(registry.owns(2, otherMod));
        assertFalse(registry.owns(2, firstOwner));
    }

    @Test
    void ownerAndRuntimeCleanupPreserveUnrelatedIds() {
        var registry = new MLuaRenderOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var firstOwner = new MLuaRenderOwnershipRegistry.Owner(firstSession, "one");
        var sameRuntimeOtherMod = new MLuaRenderOwnershipRegistry.Owner(firstSession, "two");
        var otherRuntime = new MLuaRenderOwnershipRegistry.Owner(secondSession, "one");
        registry.register(1, firstOwner);
        registry.register(2, sameRuntimeOtherMod);
        registry.register(3, otherRuntime);

        assertEquals(java.util.List.of(1), registry.clearOwned(firstOwner));
        assertEquals(java.util.List.of(2), registry.ids(sameRuntimeOtherMod));
        assertEquals(java.util.List.of(2), registry.clearRuntime(firstSession));
        assertEquals(java.util.List.of(3), registry.ids(otherRuntime));
    }

    @Test
    void registrationLimitsAreAtomicAndDuplicateIdsRemainErrors() {
        var registry = new MLuaRenderOwnershipRegistry();
        var firstOwner = new MLuaRenderOwnershipRegistry.Owner(new Object(), "one");
        var secondOwner = new MLuaRenderOwnershipRegistry.Owner(new Object(), "two");

        assertTrue(registry.tryRegister(1, firstOwner, 2, 1));
        assertFalse(registry.tryRegister(2, firstOwner, 2, 1));
        assertTrue(registry.tryRegister(2, secondOwner, 2, 1));
        assertFalse(registry.tryRegister(3, secondOwner, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.tryRegister(1, secondOwner, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.tryRegister(3, secondOwner, 0, 1));
    }
}
