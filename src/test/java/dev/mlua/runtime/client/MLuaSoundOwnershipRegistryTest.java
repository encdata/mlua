package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaSoundOwnershipRegistryTest {
    @Test
    void soundHandlesArePartitionedByRuntimeAndMod() {
        var owners = new MLuaSoundOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var firstOwner = new MLuaSoundOwnershipRegistry.Owner(firstSession, "one");
        var otherMod = new MLuaSoundOwnershipRegistry.Owner(firstSession, "two");
        var otherSession = new MLuaSoundOwnershipRegistry.Owner(secondSession, "one");
        owners.register(1, firstOwner);
        owners.register(2, otherMod);
        owners.register(3, otherSession);

        assertTrue(owners.owns(1, firstOwner));
        assertFalse(owners.owns(2, firstOwner));
        assertTrue(owners.owns(3, otherSession));
    }

    @Test
    void cleanupRemovesOnlyTheSelectedOwnerSounds() {
        var owners = new MLuaSoundOwnershipRegistry();
        Object firstSession = new Object();
        Object secondSession = new Object();
        var firstOwner = new MLuaSoundOwnershipRegistry.Owner(firstSession, "one");
        var otherMod = new MLuaSoundOwnershipRegistry.Owner(firstSession, "two");
        var otherSession = new MLuaSoundOwnershipRegistry.Owner(secondSession, "one");
        owners.register(1, firstOwner);
        owners.register(2, otherMod);
        owners.register(3, otherSession);

        assertEquals(java.util.List.of(1), owners.clearOwned(firstOwner));
        assertEquals(java.util.List.of(2), owners.clearRuntime(firstSession));
        assertTrue(owners.owns(3, otherSession));
    }
}
