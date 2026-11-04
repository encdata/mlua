package dev.mlua.modules.events.service;

import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.modules.events.service.MLuaEventSignalRegistry.Entry;
import dev.mlua.modules.events.service.MLuaEventsService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaEventSignalRegistryTest {
    @Test
    void builtInsRejectDuplicateNamesAndSignalInstances() {
        MLuaSignal serverTickSignal = MLuaEventsService.SERVER_TICK;
        Entry serverTick = MLuaEventSignalRegistry.snapshot().get("ServerTick");

        assertThrows(IllegalStateException.class, () -> MLuaEventSignalRegistry.registerBuiltIn(
                "ServerTick", new MLuaSignal(), "duplicate name"));
        assertThrows(IllegalStateException.class, () -> MLuaEventSignalRegistry.registerBuiltIn(
                "AnotherBuiltIn", serverTickSignal, "duplicate signal"));

        assertSame(serverTick.signal(), MLuaEventSignalRegistry.snapshot().get("ServerTick").signal());
    }

    @Test
    void builtInCatalogPublishesEveryDocumentedSignal() {
        assertSame(MLuaBuiltInEventSignals.SERVER_TICK, MLuaEventsService.SERVER_TICK);
        java.util.Set<String> names = java.util.Set.of(
                "ServerTick", "ClientTick", "RenderStepped", "PreRender", "PostRender", "EntityHurt",
                "EntityDeath", "BlockBreak", "BlockPlace", "ItemUse", "EntityInteract", "PlayerRespawn",
                "EntitySpawn", "EntityUnload", "WorldLoad", "WorldUnload", "BlockRightClick", "ItemDrop",
                "ItemPickup", "ItemCraft", "ContainerOpen", "ContainerClose", "ProjectileHit", "Explosion",
                "AdvancementCompleted");

        for (String name : names) {
            Entry entry = MLuaEventSignalRegistry.snapshot().get(name);
            assertTrue(entry != null, "missing built-in signal " + name);
            assertTrue(entry.description() != null && !entry.description().isBlank(),
                    "missing description for built-in signal " + name);
        }
    }

    @Test
    void ownerAndSignalIndexesTrackRegistrationAndRemoval() {
        String firstName = "testaddon:event_" + java.util.UUID.randomUUID().toString().replace("-", "");
        String secondName = "testaddon:event_" + java.util.UUID.randomUUID().toString().replace("-", "");
        MLuaSignal signal = new MLuaSignal();
        MLuaSignal nextSignal = new MLuaSignal();
        java.util.Map<String, Entry> beforeRegistration = MLuaEventSignalRegistry.snapshot();

        MLuaEventSignalRegistry.register(firstName, signal, "test description", "testaddon");
        try {
            java.util.Map<String, Entry> registeredSnapshot = MLuaEventSignalRegistry.snapshot();
            assertFalse(beforeRegistration.containsKey(firstName));
            assertSame(signal, registeredSnapshot.get(firstName).signal());
            assertThrows(UnsupportedOperationException.class, registeredSnapshot::clear);
            assertEquals(1, MLuaEventSignalRegistry.countOwner("testaddon"));
            assertThrows(IllegalStateException.class,
                    () -> MLuaEventSignalRegistry.register(secondName, signal, "duplicate instance", "testaddon"));
            assertFalse(MLuaEventSignalRegistry.unregisterOwned(firstName, "otheraddon", signal));
            assertTrue(MLuaEventSignalRegistry.unregisterOwned(firstName, "testaddon", signal));
            assertSame(signal, registeredSnapshot.get(firstName).signal());
            assertFalse(MLuaEventSignalRegistry.snapshot().containsKey(firstName));
            assertEquals(0, MLuaEventSignalRegistry.countOwner("testaddon"));

            MLuaEventSignalRegistry.register(secondName, nextSignal, "second signal", "testaddon");
            assertEquals(1, MLuaEventSignalRegistry.countOwner("testaddon"));
            assertTrue(MLuaEventSignalRegistry.unregisterOwned(secondName, "testaddon", nextSignal));
        } finally {
            MLuaEventSignalRegistry.unregisterOwned(firstName, "testaddon", signal);
            MLuaEventSignalRegistry.unregisterOwned(secondName, "testaddon", nextSignal);
        }
        assertEquals(0, MLuaEventSignalRegistry.countOwner("testaddon"));
    }
}
