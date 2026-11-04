package dev.mlua.modules.world;

import dev.mlua.module.MLuaRegistration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Owns the version-neutral Lua shape of a live GameRuleEntry instance. */
final class MLuaWorldGameRuleEntry {
    private MLuaWorldGameRuleEntry() { }

    static LuaTable create(String name, MLuaRegistration registration,
                           Supplier<LuaValue> valueGetter, Consumer<LuaValue> valueSetter) {
        LuaTable fields = new LuaTable();
        fields.set("__game_rule_name", LuaValue.valueOf(name));
        fields.set("__game_rule_get_value", new ZeroArgFunction() {
            @Override public LuaValue call() { return valueGetter.get(); }
        });
        fields.set("__game_rule_set_value", new OneArgFunction() {
            @Override public LuaValue call(LuaValue value) {
                valueSetter.accept(value);
                return LuaValue.NONE;
            }
        });
        return registration.createClassInstance("GameRuleEntry", fields);
    }
}
