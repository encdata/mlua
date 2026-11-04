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

    @Test
    void ownerQueriesAreSortedAndCountsStayInSyncWithRemoval() {
        var owners = new MLuaSoundOwnershipRegistry();
        var first = new MLuaSoundOwnershipRegistry.Owner(new Object(), "one");
        var other = new MLuaSoundOwnershipRegistry.Owner(new Object(), "two");
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
