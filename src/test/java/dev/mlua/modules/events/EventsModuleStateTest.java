package dev.mlua.modules.events;

import net.minecraft.resources.ResourceLocation;
import dev.mlua.modules.events.service.MLuaEventsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EventsModuleStateTest {
    private final Set<Object> runtimeOwners = new HashSet<>();

    @AfterEach
    void clearState() {
        runtimeOwners.forEach(EventsModule::clearRuntime);
    }

    private Object newRuntimeOwner() {
        Object runtimeOwner = new Object();
        runtimeOwners.add(runtimeOwner);
        return runtimeOwner;
    }

    @Test
    void playerLeaveRemovesOnlyThatPlayersDimensionSnapshots() {
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        Object runtimeOwner = newRuntimeOwner();
        Object otherRuntime = newRuntimeOwner();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
        MLuaPlayerStepTracker.recordPosition(runtimeOwner, overworld, leaving, 1L);
        MLuaPlayerStepTracker.recordPosition(runtimeOwner, nether, leaving, 2L);
        MLuaPlayerStepTracker.recordPosition(runtimeOwner, overworld, staying, 3L);
        MLuaPlayerStepTracker.recordPosition(otherRuntime, overworld, leaving, 4L);

        EventsModule.forgetPlayerStepPositions(leaving, runtimeOwner);

        assertNull(MLuaPlayerStepTracker.positionOf(runtimeOwner, overworld, leaving));
        assertNull(MLuaPlayerStepTracker.positionOf(runtimeOwner, nether, leaving));
        assertEquals(3L, MLuaPlayerStepTracker.positionOf(runtimeOwner, overworld, staying));
        assertEquals(4L, MLuaPlayerStepTracker.positionOf(otherRuntime, overworld, leaving));
    }

    @Test
    void worldUnloadRemovesOnlyThatDimensionsSnapshots() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Object firstRuntime = newRuntimeOwner();
        Object secondRuntime = newRuntimeOwner();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
        MLuaPlayerStepTracker.recordPosition(firstRuntime, overworld, first, 1L);
        MLuaPlayerStepTracker.recordPosition(secondRuntime, overworld, second, 2L);
        MLuaPlayerStepTracker.recordPosition(firstRuntime, nether, first, 3L);

        EventsModule.forgetWorldStepPositions(overworld, firstRuntime);

        assertNull(MLuaPlayerStepTracker.positionOf(firstRuntime, overworld, first));
        assertEquals(2L, MLuaPlayerStepTracker.positionOf(secondRuntime, overworld, second));
        assertEquals(3L, MLuaPlayerStepTracker.positionOf(firstRuntime, nether, first));
    }

    @Test
    void runtimeCleanupPreservesStepSnapshotsOwnedByOtherSessions() {
        Object firstRuntime = newRuntimeOwner();
        Object secondRuntime = newRuntimeOwner();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        MLuaPlayerStepTracker.recordPosition(firstRuntime, overworld, firstPlayer, 1L);
        MLuaPlayerStepTracker.recordPosition(secondRuntime, overworld, secondPlayer, 2L);

        EventsModule.clearRuntime(firstRuntime);

        assertNull(MLuaPlayerStepTracker.positionOf(firstRuntime, overworld, firstPlayer));
        assertEquals(2L, MLuaPlayerStepTracker.positionOf(secondRuntime, overworld, secondPlayer));
    }

    @Test
    void clientEntityUnloadIsRegisteredAsABuiltInSignal() {
        assertTrue(MLuaEventsService.signalNames().contains("EntityUnload"));
        assertFalse(MLuaEventsService.unregisterSignal("EntityUnload", MLuaEventsService.ENTITY_UNLOAD));
    }
}
