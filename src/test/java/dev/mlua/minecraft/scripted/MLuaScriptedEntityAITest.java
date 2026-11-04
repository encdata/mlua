package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedEntityAITest {
    @Test
    void entersInitialStateAndUsesUpdateReturnToTransition() {
        MLuaScriptedEntityAI ai = new MLuaScriptedEntityAI();
        RecordingHost host = new RecordingHost();
        List<String> hooks = new ArrayList<>();

        LuaTable idle = new LuaTable();
        idle.set("onEnter", callback((args) -> hooks.add("enter:" + args.arg(2).get("State").tojstring())));
        idle.set("onExit", callback((args) -> hooks.add("exit:" + args.arg(2).get("State").tojstring())));
        idle.set("update", callback((args) -> hooks.add("update:" + args.arg(2).get("State").tojstring()),
                LuaValue.valueOf("attack")));
        LuaTable attack = new LuaTable();
        attack.set("onEnter", callback((args) -> hooks.add("enter:" + args.arg(2).get("State").tojstring())));
        LuaTable states = new LuaTable();
        states.set("idle", idle);
        states.set("attack", attack);
        LuaTable definition = definition("idle", states);

        ai.tick(definition, host);

        assertEquals("attack", ai.state());
        assertEquals(0, ai.ticksInState());
        assertEquals(List.of("enter:idle", "update:idle", "exit:idle", "enter:attack"), hooks);
        assertTrue(host.disabled.isEmpty());
    }

    @Test
    void queuesStateChangesRequestedFromTransitionHooks() {
        MLuaScriptedEntityAI ai = new MLuaScriptedEntityAI();
        RecordingHost host = new RecordingHost();
        List<String> entered = new ArrayList<>();
        LuaTable states = new LuaTable();
        LuaTable definition = definition("first", states);
        LuaTable first = new LuaTable();
        LuaTable third = new LuaTable();
        first.set("onEnter", callback((args) -> {
            entered.add("first");
            ai.setState(definition, "third", definition, host);
        }));
        third.set("onEnter", callback((args) -> entered.add("third")));
        states.set("first", first);
        states.set("third", third);
        ai.setState(definition, "first", definition, host);

        assertEquals("third", ai.state());
        assertEquals(List.of("first", "third"), entered);
    }

    @Test
    void failedUpdateIsDisabledAndStateTicksRemainOwnedByController() {
        MLuaScriptedEntityAI ai = new MLuaScriptedEntityAI();
        RecordingHost host = new RecordingHost();
        LuaTable state = new LuaTable();
        state.set("update", callback((args) -> { throw new IllegalStateException("broken update"); }));
        LuaTable states = new LuaTable();
        states.set("idle", state);
        LuaTable definition = definition("idle", states);

        ai.tick(definition, host);
        ai.tickState();

        assertEquals("idle", ai.state());
        assertEquals(1, ai.ticksInState());
        assertTrue(host.disabled.contains("aiState:idle:update"));
        assertFalse(host.shouldInvoke("aiState:idle:update", state.get("update")));
    }

    private static LuaTable definition(String initial, LuaTable states) {
        LuaTable definition = new LuaTable();
        definition.set("initial", initial);
        definition.set("states", states);
        return definition;
    }

    private static LuaValue callback(Callback callback) {
        return callback(callback, LuaValue.NIL);
    }

    private static LuaValue callback(Callback callback, LuaValue result) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                callback.call(args);
                return result;
            }
        };
    }

    @FunctionalInterface
    private interface Callback {
        void call(Varargs args);
    }

    private static final class RecordingHost implements MLuaScriptedEntityAI.CallbackHost {
        private final LuaTable entity = new LuaTable();
        private final List<String> disabled = new ArrayList<>();

        @Override public LuaValue entity() { return entity; }
        @Override public LuaValue target() { return LuaValue.NIL; }
        @Override public String behaviorKey() { return "test:entity"; }
        @Override public Varargs invokeDefinition(LuaValue callback, LuaValue... args) {
            return callback.invoke(LuaValue.varargsOf(args));
        }
        @Override public Varargs invokeState(LuaValue definition, LuaValue callback, LuaValue... args) {
            return callback.invoke(LuaValue.varargsOf(args));
        }
        @Override public boolean shouldInvoke(String event, LuaValue callback) {
            return !this.disabled.contains(event);
        }
        @Override public void disable(String event, Exception failure) {
            this.disabled.add(event);
        }
        @Override public void clearFailures(String state) {
            this.disabled.removeIf(event -> state == null || event.startsWith("aiState:" + state + ":"));
        }
    }
}
