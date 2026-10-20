package dev.mlua.modules.world;

import dev.mlua.module.MLuaRegistration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Typed Lua access to the 26.1 gamerule registry and live level values. */
public final class MLuaWorldGameRules26 {
    private static final Map<String, String> LEGACY_PATHS = Map.of("doDaylightCycle", "advance_time");
    private static final Map<String, String> LEGACY_NAMES = Map.of("advance_time", "doDaylightCycle");

    private MLuaWorldGameRules26() { }

    public static LuaTable list(Level level, MLuaRegistration registration,
                                Supplier<MinecraftServer> serverSupplier) {
        GameRules rules = gameRules(level);
        LuaTable table = new LuaTable();
        int index = 1;
        List<GameRule<?>> availableRules = new ArrayList<>(rules.availableRules().toList());
        availableRules.sort((left, right) -> left.id().compareTo(right.id()));
        for (GameRule<?> rule : availableRules) {
            String name = rule.getIdentifier().getPath();
            LuaTable entry = entry(level, name, rule, registration, serverSupplier);
            table.set(index++, entry);
            table.set(name, entry.get("Value"));
            table.set(camelCaseName(name), entry.get("Value"));
            table.set(rule.getIdentifier().toString(), entry.get("Value"));
            String legacyName = LEGACY_NAMES.get(name);
            if (legacyName != null) table.set(legacyName, entry.get("Value"));
        }
        return table;
    }

    public static LuaValue get(Level level, String name) {
        GameRules rules = gameRules(level);
        GameRule<?> rule = findGameRule(rules, name);
        if (rule == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        return gameRuleValue(rules, rule);
    }

    public static LuaTable entry(Level level, String name, MLuaRegistration registration,
                                 Supplier<MinecraftServer> serverSupplier) {
        GameRule<?> rule = findGameRule(gameRules(level), name);
        if (rule == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        return entry(level, name, rule, registration, serverSupplier);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void set(ServerLevel level, String name, LuaValue value, MinecraftServer server) {
        GameRules rules = gameRules(level);
        GameRule rule = findGameRule(rules, name);
        if (rule == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        String serialized = value.isboolean() ? Boolean.toString(value.toboolean()) : value.tojstring();
        var parsed = rule.deserialize(serialized);
        if (parsed.error().isPresent()) {
            throw new LuaError("[MLua] Invalid value for gamerule " + name + ": " + parsed.error().get());
        }
        rules.set(rule, parsed.result().orElseThrow(), server);
    }

    private static LuaTable entry(Level level, String name, GameRule<?> rule,
                                  MLuaRegistration registration, Supplier<MinecraftServer> serverSupplier) {
        return MLuaWorldGameRuleEntry.create(name, registration,
            () -> gameRuleValue(gameRules(level), rule), value -> {
                if (!(level instanceof ServerLevel serverLevel)) {
                    throw new LuaError("[MLua] GameRuleEntry values can only be changed on the server.");
                }
                MLuaWorldGameRules26.set(serverLevel, name, value, serverSupplier.get());
            });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static LuaValue gameRuleValue(GameRules rules, GameRule<?> rule) {
        Object value = rules.get((GameRule) rule);
        if (value instanceof Boolean booleanValue) return LuaValue.valueOf(booleanValue);
        if (value instanceof Number number) return LuaValue.valueOf(number.doubleValue());
        return LuaValue.valueOf(String.valueOf(value));
    }

    private static GameRule<?> findGameRule(GameRules rules, String name) {
        String canonicalName = canonicalName(name);
        return rules.availableRules()
            .filter(rule -> rule.id().equals(canonicalName)
                || rule.getIdentifier().getPath().equals(canonicalName)
                || rule.getIdentifier().toString().equals(canonicalName)
                || camelCaseName(rule.getIdentifier().getPath()).equals(name))
            .findFirst()
            .orElse(null);
    }

    private static String canonicalName(String name) {
        String path = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        String canonicalPath = LEGACY_PATHS.get(path);
        return canonicalPath == null ? name : (name.startsWith("minecraft:") ? "minecraft:" : "") + canonicalPath;
    }

    private static String camelCaseName(String path) {
        StringBuilder name = new StringBuilder(path.length());
        boolean capitalizeNext = false;
        for (int index = 0; index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == '_') {
                capitalizeNext = true;
            } else {
                name.append(capitalizeNext ? Character.toUpperCase(character) : character);
                capitalizeNext = false;
            }
        }
        return name.toString();
    }

    private static GameRules gameRules(Level level) {
        var server = level == null ? null : level.getServer();
        if (server == null) {
            throw new LuaError("[MLua] Game rules are only available for a server world.");
        }
        return server.getGameRules();
    }
}
