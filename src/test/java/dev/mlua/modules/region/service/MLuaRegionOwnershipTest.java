package dev.mlua.modules.region.service;

import dev.mlua.common.enums.Side;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.signal.MLuaSignal;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRegionOwnershipTest {
    @Test
    void regionKeysSeparateOwnersAndSides() {
        MLuaRegionStateRegistry.RegionKey serverRegion = new MLuaRegionStateRegistry.RegionKey("alpha", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionKey sameRegion = new MLuaRegionStateRegistry.RegionKey("alpha", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionKey otherOwner = new MLuaRegionStateRegistry.RegionKey("beta", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionKey clientRegion = new MLuaRegionStateRegistry.RegionKey("alpha", Side.CLIENT, "spawn");

        assertEquals(serverRegion, sameRegion);
        assertNotEquals(serverRegion, otherOwner);
        assertNotEquals(serverRegion, clientRegion);
    }

    @Test
    void ownerCleanupRemovesOnlyThatOwnersRegionsOnTheRequestedSide() {
        MLuaTaskScheduler scheduler = new MLuaTaskScheduler();
        MLuaRegionStateRegistry.RegionState state = MLuaRegionStateRegistry.stateFor(scheduler);
        MLuaRegionStateRegistry.RegionKey ownerServer = new MLuaRegionStateRegistry.RegionKey("alpha", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionKey otherServer = new MLuaRegionStateRegistry.RegionKey("beta", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionKey ownerClient = new MLuaRegionStateRegistry.RegionKey("alpha", Side.CLIENT, "spawn");
        MLuaSignal ownerServerSignal = new MLuaSignal();
        MLuaSignal otherServerSignal = new MLuaSignal();
        MLuaSignal ownerClientSignal = new MLuaSignal();
        ownerServerSignal.connect(new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return org.luaj.vm2.LuaValue.NONE;
            }
        });

        try {
            state.onEnter.put(ownerServer, ownerServerSignal);
            state.onEnter.put(otherServer, otherServerSignal);
            state.onEnter.put(ownerClient, ownerClientSignal);
            assertFalse(ownerServerSignal.isEmpty());

            MLuaRegionStateRegistry.destroyOwned(scheduler, Side.SERVER, "alpha");

            assertFalse(state.onEnter.containsKey(ownerServer));
            assertSame(otherServerSignal, state.onEnter.get(otherServer));
            assertSame(ownerClientSignal, state.onEnter.get(ownerClient));
            assertTrue(ownerServerSignal.isEmpty());
        } finally {
            MLuaRegionStateRegistry.clearForScheduler(scheduler, Side.BOTH);
        }
    }

    @Test
    void identicalRegionKeysUseSeparateStateForEachRuntimeScheduler() {
        MLuaTaskScheduler firstScheduler = new MLuaTaskScheduler();
        MLuaTaskScheduler secondScheduler = new MLuaTaskScheduler();
        MLuaRegionStateRegistry.RegionState first = MLuaRegionStateRegistry.stateFor(firstScheduler);
        MLuaRegionStateRegistry.RegionState second = MLuaRegionStateRegistry.stateFor(secondScheduler);
        MLuaRegionStateRegistry.RegionKey sameKey = new MLuaRegionStateRegistry.RegionKey("alpha", Side.SERVER, "spawn");
        MLuaRegionStateRegistry.RegionDef firstDefinition = new MLuaRegionStateRegistry.RegionDef(sameKey, 1, 2, 3, 4);
        MLuaRegionStateRegistry.RegionDef secondDefinition = new MLuaRegionStateRegistry.RegionDef(sameKey, 7, 8, 9, 2);
        MLuaSignal firstSignal = new MLuaSignal();
        MLuaSignal secondSignal = new MLuaSignal();
        firstSignal.connect(noopListener());
        secondSignal.connect(noopListener());
        first.onEnter.put(sameKey, firstSignal);
        second.onEnter.put(sameKey, secondSignal);
        first.regions.put(sameKey, firstDefinition);
        second.regions.put(sameKey, secondDefinition);

        try {
            assertNotSame(first, second);
            assertSame(firstSignal, first.onEnter.get(sameKey));
            assertSame(secondSignal, second.onEnter.get(sameKey));
            assertSame(firstDefinition, first.regions.get(sameKey));
            assertSame(secondDefinition, second.regions.get(sameKey));

            MLuaRegionStateRegistry.clearForScheduler(firstScheduler, Side.SERVER);

            assertFalse(first.onEnter.containsKey(sameKey));
            assertFalse(first.regions.containsKey(sameKey));
            assertSame(secondSignal, second.onEnter.get(sameKey));
            assertTrue(firstSignal.isEmpty());
            assertFalse(secondSignal.isEmpty());
        } finally {
            MLuaRegionStateRegistry.clearForScheduler(firstScheduler, Side.BOTH);
            MLuaRegionStateRegistry.clearForScheduler(secondScheduler, Side.BOTH);
        }
    }

    private static VarArgFunction noopListener() {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return org.luaj.vm2.LuaValue.NONE; }
        };
    }
}
