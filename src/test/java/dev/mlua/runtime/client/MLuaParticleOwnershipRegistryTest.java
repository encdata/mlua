package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaParticleOwnershipRegistryTest {
    @Test
    void emitterIdsArePartitionedByRuntimeAndMod() {
        var owners = new MLuaParticleOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var first = new MLuaParticleOwnershipRegistry.Owner(firstSession, "one");
        var otherMod = new MLuaParticleOwnershipRegistry.Owner(firstSession, "two");
        var otherRuntime = new MLuaParticleOwnershipRegistry.Owner(secondSession, "one");
        owners.register(1, first);
        owners.register(2, otherMod);
        owners.register(3, otherRuntime);

        assertTrue(owners.owns(1, first));
        assertFalse(owners.owns(2, first));
        assertTrue(owners.owns(3, otherRuntime));
    }

    @Test
    void cleanupLeavesOtherOwnersEmittersRegistered() {
        var owners = new MLuaParticleOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var first = new MLuaParticleOwnershipRegistry.Owner(firstSession, "one");
        var otherMod = new MLuaParticleOwnershipRegistry.Owner(firstSession, "two");
        var otherRuntime = new MLuaParticleOwnershipRegistry.Owner(secondSession, "one");
        owners.register(1, first);
        owners.register(2, otherMod);
        owners.register(3, otherRuntime);

        assertEquals(java.util.List.of(1), owners.clearOwned(first));
        assertEquals(java.util.List.of(2), owners.clearRuntime(firstSession));
        assertTrue(owners.owns(3, otherRuntime));
    }

    @Test
    void ownerQueriesAreSortedAndCountsStayInSyncWithRemoval() {
        var owners = new MLuaParticleOwnershipRegistry();
        var first = new MLuaParticleOwnershipRegistry.Owner(new Object(), "one");
        var other = new MLuaParticleOwnershipRegistry.Owner(new Object(), "two");
        owners.register(9, first);
        owners.register(3, first);
        owners.register(5, other);

        assertEquals(java.util.List.of(3, 9), owners.idsOwned(first));
        assertEquals(2, owners.countOwned(first));
        owners.remove(3);
        assertEquals(1, owners.countOwned(first));
        assertEquals(java.util.List.of(9), owners.clearOwned(first));
        assertEquals(0, owners.countOwned(first));
        assertEquals(1, owners.countOwned(other));
        assertEquals(java.util.List.of(5), owners.clearRuntime(other.runtimeOwner()));
        assertEquals(0, owners.countOwned(other));
    }
}
