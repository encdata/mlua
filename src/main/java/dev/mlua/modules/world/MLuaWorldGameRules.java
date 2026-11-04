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
    private static final RuleCatalog RULE_CATALOG = discoverGameRuleCatalog();

    private MLuaWorldGameRules() { }

    public static LuaTable list(Level level, MLuaRegistration registration,
                                Supplier<MinecraftServer> serverSupplier) {
        LuaTable table = new LuaTable();
        int index = 1;
        for (String name : RULE_CATALOG.orderedNames()) {
            LuaTable entry = createEntry(level, name, RULE_CATALOG.keysByName().get(name), registration, serverSupplier);
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
        GameRules.Key<?> key = RULE_CATALOG.keysByName().get(name);
        if (key == null) throw new LuaError("[MLua] Unknown gamerule: " + name);
        return key;
    }

    private static RuleCatalog discoverGameRuleCatalog() {
        Map<String, GameRules.Key<?>> keys = new HashMap<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                keys.put(key.getId(), key);
            }
        });
        List<String> names = new ArrayList<>(keys.keySet());
        Collections.sort(names);
        return new RuleCatalog(Collections.unmodifiableMap(keys), List.copyOf(names));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setValue(GameRules rules, GameRules.Key<?> key, String name,
                                 LuaValue value, MinecraftServer server) {
        GameRules.Value rule = rules.getRule((GameRules.Key) key);
        if (rule instanceof GameRules.BooleanValue booleanValue) {
            boolean converted = (boolean) MLuaWorldGameRuleValue.convert(name, booleanValue.get(), value);
            booleanValue.set(converted, server);
            return;
        }
        if (rule instanceof GameRules.IntegerValue integerValue) {
            int converted = (int) MLuaWorldGameRuleValue.convert(name, integerValue.get(), value);
            integerValue.set(converted, server);
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

    private record RuleCatalog(Map<String, GameRules.Key<?>> keysByName, List<String> orderedNames) { }
}
//--endversion

//--version 26.1

import dev.mlua.core.util.MLuaWeakIdentityMap;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Typed Lua access to the 26.1 gamerule registry and live level values. */
public final class MLuaWorldGameRules {
    private static final Map<String, String> LEGACY_PATHS = Map.of("doDaylightCycle", "advance_time");
    private static final Map<String, String> LEGACY_NAMES = Map.of("advance_time", "doDaylightCycle");
    private static final MLuaWeakIdentityMap<GameRules, RuleCatalog> CATALOGS = new MLuaWeakIdentityMap<>();

    private MLuaWorldGameRules() { }

    public static LuaTable list(Level level, MLuaRegistration registration,
                                Supplier<MinecraftServer> serverSupplier) {
        GameRules rules = gameRules(level);
        LuaTable table = new LuaTable();
        int index = 1;
        for (GameRule<?> rule : catalog(rules).orderedRules()) {
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
        Object converted = MLuaWorldGameRuleValue.convert(name, rules.get(rule), value);
        String serialized = String.valueOf(converted);
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
                MLuaWorldGameRules.set(serverLevel, name, value, serverSupplier.get());
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
        return catalog(rules).byName().get(name);
    }

    private static RuleCatalog catalog(GameRules rules) {
        RuleCatalog cached = CATALOGS.get(rules);
        if (cached != null) return cached;
        RuleCatalog created = createCatalog(rules);
        RuleCatalog raced = CATALOGS.put(rules, created);
        return raced == null ? created : raced;
    }

    private static RuleCatalog createCatalog(GameRules rules) {
        List<GameRule<?>> orderedRules = new ArrayList<>(rules.availableRules().toList());
        orderedRules.sort((left, right) -> left.id().compareTo(right.id()));
        Map<String, GameRule<?>> byName = new HashMap<>();
        for (GameRule<?> rule : orderedRules) {
            String path = rule.getIdentifier().getPath();
            String identifier = rule.getIdentifier().toString();
            addAlias(byName, rule.id(), rule);
            addAlias(byName, path, rule);
            addAlias(byName, identifier, rule);
            addAlias(byName, camelCaseName(path), rule);
            String legacyName = LEGACY_NAMES.get(path);
            if (legacyName != null) {
                addAlias(byName, legacyName, rule);
                if (identifier.startsWith("minecraft:")) {
                    addAlias(byName, "minecraft:" + legacyName, rule);
                }
            }
            String legacyPath = LEGACY_PATHS.get(path);
            if (legacyPath != null) {
                addAlias(byName, legacyPath, rule);
                if (identifier.startsWith("minecraft:")) {
                    addAlias(byName, "minecraft:" + legacyPath, rule);
                }
            }
        }
        return new RuleCatalog(List.copyOf(orderedRules), Collections.unmodifiableMap(byName));
    }

    private static void addAlias(Map<String, GameRule<?>> aliases, String name, GameRule<?> rule) {
        aliases.putIfAbsent(name, rule);
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

    private record RuleCatalog(List<GameRule<?>> orderedRules, Map<String, GameRule<?>> byName) { }
}
//--endversion
