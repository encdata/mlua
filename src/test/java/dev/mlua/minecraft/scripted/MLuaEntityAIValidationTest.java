package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaEntityAIValidationTest {
    @Test
    void snapshotsStateDataButKeepsLuaCallbacks() {
        LuaValue update = new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        };
        LuaTable idle = new LuaTable();
        idle.set("update", update);
        idle.set("speed", 0.2);
        LuaTable states = new LuaTable();
        states.set("idle", idle);
        LuaTable definition = new LuaTable();
        definition.set("initial", "idle");
        definition.set("states", states);

        LuaTable snapshot = MLuaBehaviorRegistry.snapshotEntityAI(definition);

        assertNotSame(definition, snapshot);
        assertNotSame(states, snapshot.rawget("states"));
        assertNotSame(idle, snapshot.rawget("states").checktable().rawget("idle"));
        assertSame(update, snapshot.rawget("states").checktable().rawget("idle").checktable().rawget("update"));
        assertEquals(0.2, snapshot.rawget("states").checktable().rawget("idle").checktable()
                .rawget("speed").todouble(), 0.0001);
        idle.set("speed", 0.9);
        assertEquals(0.2, snapshot.rawget("states").checktable().rawget("idle").checktable()
                .rawget("speed").todouble(), 0.0001);
    }

    @Test
    void rejectsStateFieldsProvidedOnlyByMetatables() {
        LuaTable metatable = new LuaTable();
        metatable.set("__index", new LuaTable());
        LuaTable definition = new LuaTable();
        definition.setmetatable(metatable);

        assertThrows(LuaError.class, () -> MLuaBehaviorRegistry.validateEntityAI(definition));
    }

    @Test
    void rejectsMetatablesNestedInStateData() {
        LuaTable idle = new LuaTable();
        idle.set("update", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.NIL; }
        });
        idle.setmetatable(new LuaTable());
        LuaTable states = new LuaTable();
        states.set("idle", idle);
        LuaTable definition = new LuaTable();
        definition.set("initial", "idle");
        definition.set("states", states);

        assertThrows(LuaError.class, () -> MLuaBehaviorRegistry.snapshotEntityAI(definition));
    }

    @Test
    void rejectsCyclicTablesBeforeSnapshotting() {
        LuaTable idle = new LuaTable();
        idle.set("nested", idle);
        LuaTable states = new LuaTable();
        states.set("idle", idle);
        LuaTable definition = new LuaTable();
        definition.set("initial", "idle");
        definition.set("states", states);

        assertThrows(LuaError.class, () -> MLuaBehaviorRegistry.snapshotEntityAI(definition));
    }
}
