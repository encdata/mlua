package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaEntityComponentManagerScopeTest {
    @Test
    void sameNumericIdInDifferentDimensionsGetsIndependentComponentBuckets() {
        UUID uuid = UUID.randomUUID();
        var overworld = MLuaEntityComponentManager.entityKey(
                new MLuaEntityScope(Side.SERVER, "minecraft:overworld", uuid, 23), Side.SERVER);
        var nether = MLuaEntityComponentManager.entityKey(
                new MLuaEntityScope(Side.SERVER, "minecraft:the_nether", uuid, 23), Side.SERVER);
        var buckets = new HashMap<MLuaEntityComponentManager.EntityComponentsKey, String>();
        buckets.put(overworld, "overworld component");
        buckets.put(nether, "nether component");

        assertNotEquals(overworld, nether);
        assertEquals("overworld component", buckets.get(overworld));
        assertEquals("nether component", buckets.get(nether));
    }

    @Test
    void removingOneEntityKeyPreservesComponentsForTheOtherDimension() {
        UUID uuid = UUID.randomUUID();
        var overworld = MLuaEntityComponentManager.entityKey(
                new MLuaEntityScope(Side.SERVER, "minecraft:overworld", uuid, 23), Side.SERVER);
        var nether = MLuaEntityComponentManager.entityKey(
                new MLuaEntityScope(Side.SERVER, "minecraft:the_nether", uuid, 23), Side.SERVER);
        var buckets = new HashMap<MLuaEntityComponentManager.EntityComponentsKey, String>();
        buckets.put(overworld, "overworld component");
        buckets.put(nether, "nether component");

        buckets.remove(overworld);

        assertNull(buckets.get(overworld));
        assertEquals("nether component", buckets.get(nether));
    }

    @Test
    void sideIsPartOfTheComponentBucketIdentity() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope scope = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", uuid, 23);

        assertNotEquals(
                MLuaEntityComponentManager.entityKey(scope, Side.SERVER),
                MLuaEntityComponentManager.entityKey(scope, Side.CLIENT));
    }

    @Test
    void identicalComponentNamesBelongToDifferentRuntimeSessions() {
        Object firstSession = new Object();
        Object secondSession = new Object();

        var first = new MLuaEntityComponentManager.ComponentKey(firstSession, "example", "health");
        var second = new MLuaEntityComponentManager.ComponentKey(secondSession, "example", "health");

        assertNotEquals(first, second);
    }

    @Test
    void sameRuntimeSessionCanResolveItsComponentKey() {
        Object session = new Object();

        assertEquals(
                new MLuaEntityComponentManager.ComponentKey(session, "example", "health"),
                new MLuaEntityComponentManager.ComponentKey(session, "example", "health"));
    }
}
