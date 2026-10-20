package dev.mlua.modules.world;

//--version 1.20.1,1.21.1
import dev.mlua.module.MLuaRegistration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Lua-facing gamerule lookup and typed access shared by every World instance. */
public final class MLuaWorldGameRules {
    private MLuaWorldGameRules() { }

    public static LuaTable list(Level level, MLuaRegistration registration,
                                Supplier<MinecraftServer> serverSupplier) {
        LuaTable table = new LuaTable();
        int index = 1;
        Map<String, GameRules.Key<?>> keys = gameRuleKeys();
        List<String> names = new ArrayList<>(keys.keySet());
        Collections.sort(names);
        for (String name : names) {
            LuaTable entry = createEntry(level, name, keys.get(name), registration, serverSupplier);
            table.set(index++, entry);
            table.set(name, entry.get("Value"));
        }
        return table;
    }

    public static LuaValue get(Level level, String name) {
        GameRules.Key<?> key = requireKey(name);
        return gameRuleValue(level.getGameRules(), key);
    }

    public static LuaTable entry(Level level, String name, MLuaRegistration registration,
                                 Supplier<MinecraftServer> serverSupplier) {
        return createEntry(level, name, requireKey(name), registration, serverSupplier);
    }

    public static void set(ServerLevel level, String name, LuaValue value, MinecraftServer server) {
        GameRules.Key<?> key = requireKey(name);
        setValue(level.getGameRules(), key, name, value, server);
    }

    public static LuaTable createEntry(Level level, String name, GameRules.Key<?> key,
                                       MLuaRegistration registration, Supplier<MinecraftServer> serverSupplier) {
        return MLuaWorldGameRuleEntry.create(name, registration,
            () -> gameRuleValue(level.getGameRules(), key), value -> {
                if (!(level instanceof ServerLevel serverLevel)) {
                    throw new LuaError("[MLua] GameRuleEntry values can only be changed on the server.");
                }
                setValue(serverLevel.getGameRules(), key, name, value, serverSupplier.get());
            });
    }

    private static GameRules.Key<?> requireKey(String name) {
        GameRules.Key<?> key = gameRuleKeys().get(name);
        if (key == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        return key;
    }

    private static Map<String, GameRules.Key<?>> gameRuleKeys() {
        Map<String, GameRules.Key<?>> keys = new HashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                keys.put(key.getId(), key);
            }
        });
        return Collections.unmodifiableMap(keys);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setValue(GameRules rules, GameRules.Key<?> key, String name,
                                 LuaValue value, MinecraftServer server) {
        GameRules.Value rule = rules.getRule((GameRules.Key) key);
        if (rule instanceof GameRules.BooleanValue booleanValue) {
            if (!value.isboolean()) throw new LuaError("[MLua] Gamerule " + name + " requires a boolean value.");
            booleanValue.set(value.checkboolean(), server);
            return;
        }
        if (rule instanceof GameRules.IntegerValue integerValue) {
            if (!value.isnumber()) throw new LuaError("[MLua] Gamerule " + name + " requires an integer value.");
            double number = value.todouble();
            if (!Double.isFinite(number) || number != Math.rint(number)
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
                throw new LuaError("[MLua] Gamerule " + name
                    + " requires a whole number within the 32-bit integer range.");
            }
            integerValue.set((int) number, server);
            return;
        }
        throw new LuaError("[MLua] Unsupported gamerule type: " + name);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LuaValue gameRuleValue(GameRules rules, GameRules.Key<?> key) {
        GameRules.Value rule = rules.getRule((GameRules.Key) key);
        if (rule instanceof GameRules.BooleanValue booleanValue) return LuaValue.valueOf(booleanValue.get());
        if (rule instanceof GameRules.IntegerValue integerValue) return LuaValue.valueOf(integerValue.get());
        return LuaValue.valueOf(rule.toString());
    }
}
//--endversion
