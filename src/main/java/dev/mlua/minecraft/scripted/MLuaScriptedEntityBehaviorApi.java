package dev.mlua.minecraft.scripted;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Binds the scripted AI state controls exposed to behavior callbacks. */
final class MLuaScriptedEntityBehaviorApi {
    private MLuaScriptedEntityBehaviorApi() { }

    static void bind(LuaTable methods, MLuaScriptedEntity entity) {
        methods.set("GetBehaviorKey", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = entity.behaviorKey();
                return LuaValue.valueOf(key == null ? "unknown" : key);
            }
        });
        methods.set("GetAIState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String state = entity.aiState();
                return state == null ? LuaValue.NIL : LuaValue.valueOf(state);
            }
        });
        methods.set("GetAIStateTicks", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(entity.aiStateTicks());
            }
        });
        methods.set("SetAIState", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                entity.requireServerSide("SetAIState");
                LuaValue state = args.arg(2);
                if (state.type() != LuaValue.TSTRING || state.tojstring().isBlank()) {
                    throw new LuaError("[MLua] ScriptedEntity:SetAIState expects a non-empty state name string.");
                }
                entity.setScriptedAIState(state.tojstring());
                return LuaValue.TRUE;
            }
        });
    }
}
