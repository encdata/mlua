package dev.mlua.modules.events;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EventsModuleStateTest {
    @AfterEach
    void clearState() {
        EventsModule.LAST_PLAYER_STEP_POSITIONS.clear();
    }

    @Test
    void playerLeaveRemovesOnlyThatPlayersDimensionSnapshots() {
        UUID leaving = UUID.randomUUID();
        UUID staying = UUID.randomUUID();
        Object runtimeOwner = new Object();
        Object otherRuntime = new Object();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
        EventsModule.PlayerStepKey leavingKey = new EventsModule.PlayerStepKey(runtimeOwner, leaving, overworld);
        EventsModule.PlayerStepKey otherDimension = new EventsModule.PlayerStepKey(runtimeOwner, leaving, nether);
        EventsModule.PlayerStepKey stayingKey = new EventsModule.PlayerStepKey(runtimeOwner, staying, overworld);
        EventsModule.PlayerStepKey samePlayerOtherRuntime = new EventsModule.PlayerStepKey(otherRuntime, leaving, overworld);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(leavingKey, 1L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(otherDimension, 2L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(stayingKey, 3L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(samePlayerOtherRuntime, 4L);

        EventsModule.forgetPlayerStepPositions(leaving, runtimeOwner);

        assertFalse(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(leavingKey));
        assertFalse(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(otherDimension));
        assertEquals(3L, EventsModule.LAST_PLAYER_STEP_POSITIONS.get(stayingKey));
        assertEquals(4L, EventsModule.LAST_PLAYER_STEP_POSITIONS.get(samePlayerOtherRuntime));
    }

    @Test
    void worldUnloadRemovesOnlyThatDimensionsSnapshots() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Object firstRuntime = new Object();
        Object secondRuntime = new Object();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        ResourceLocation nether = new ResourceLocation("minecraft", "the_nether");
        EventsModule.PlayerStepKey firstOverworld = new EventsModule.PlayerStepKey(firstRuntime, first, overworld);
        EventsModule.PlayerStepKey secondOverworld = new EventsModule.PlayerStepKey(secondRuntime, second, overworld);
        EventsModule.PlayerStepKey netherKey = new EventsModule.PlayerStepKey(firstRuntime, first, nether);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(firstOverworld, 1L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(secondOverworld, 2L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(netherKey, 3L);

        EventsModule.forgetWorldStepPositions(overworld, firstRuntime);

        assertFalse(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(firstOverworld));
        assertTrue(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(secondOverworld));
        assertTrue(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(netherKey));
    }

    @Test
    void runtimeCleanupPreservesStepSnapshotsOwnedByOtherSessions() {
        Object firstRuntime = new Object();
        Object secondRuntime = new Object();
        ResourceLocation overworld = new ResourceLocation("minecraft", "overworld");
        var first = new EventsModule.PlayerStepKey(firstRuntime, UUID.randomUUID(), overworld);
        var second = new EventsModule.PlayerStepKey(secondRuntime, UUID.randomUUID(), overworld);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(first, 1L);
        EventsModule.LAST_PLAYER_STEP_POSITIONS.put(second, 2L);

        EventsModule.clearRuntime(firstRuntime);

        assertFalse(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(first));
        assertTrue(EventsModule.LAST_PLAYER_STEP_POSITIONS.containsKey(second));
    }
}
