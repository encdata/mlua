package dev.mlua.core.classes;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClassLiveInstancesTest {
    @Test
    void reclassificationMovesAnInstanceBetweenClassBuckets() {
        MLuaClassLiveInstances instances = new MLuaClassLiveInstances();
        LuaTable base = classTable("Base");
        LuaTable child = classTable("Child");
        LuaTable instance = new LuaTable();

        instances.attach(instance, base);
        instances.attach(instance, child);

        assertTrue(instances.ofClass("Base").isEmpty());
        assertEquals(List.of(instance), instances.ofClass("Child"));
        assertTrue(instances.hasInstances("Child"));
        assertFalse(instances.hasInstances("Base"));
        assertTrue(instances.ofClassAndDescendants("Base", List.of("Child")).contains(instance));
        assertTrue(instances.classOf(instance) == child);
    }

    private static LuaTable classTable(String name) {
        LuaTable type = new LuaTable();
        type.rawset("__type", LuaValue.valueOf(name));
        return type;
    }
}
