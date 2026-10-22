package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Lua convenience API for the configured, placed, and biome-bound parts of an ore vein. */
final class MLuaRegistryOreVeinApi {
    private MLuaRegistryOreVeinApi() { }

    static void install(MLuaRegistryService service, LuaTable worldGeneration) {
        VarArgFunction register = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                service.ensureRegistryPhase();
                String name = args.checkjstring(2);
                LuaTable definition = args.checktable(3);
                LuaValue targets = field(definition, "targets", "Targets");
                LuaValue biomes = field(definition, "biomes", "Biomes");
                LuaValue step = field(definition, "step", "Step");
                if (!targets.istable() || MLuaLuaJson.jsonArrayLength(targets.checktable()) <= 0) {
                    throw new LuaError("[MLua] WorldGeneration:RegisterOreVein requires a non-empty targets array.");
                }
                validateTargets(targets.checktable());
                MLuaRegistryDefinitions.stringValues(biomes, "ore vein biomes");
                if (step.isnil() || (step.type() != LuaValue.TSTRING
                    && dev.mlua.modules.enums.MLuaEnumValue.toJava(
                        step, dev.mlua.loaderapi.GenerationDecorationStep.class) == null)) {
                    throw new LuaError("[MLua] WorldGeneration:RegisterOreVein requires a generation step.");
                }
                int size = wholeNumber(definition, 9, "size", "Size");
                int countValue = wholeNumber(definition, 12, "count", "Count");
                int minY = wholeNumber(definition, -48, "minY", "MinY");
                int maxY = wholeNumber(definition, 48, "maxY", "MaxY");
                if (size < 1 || size > 64) {
                    throw new LuaError("[MLua] Ore vein size must be between 1 and 64.");
                }
                if (countValue < 1 || countValue > 256) {
                    throw new LuaError("[MLua] Ore vein count must be between 1 and 256.");
                }
                if (minY > maxY) throw new LuaError("[MLua] Ore vein minY must not exceed maxY.");

                String configuredId = name;
                String placedId = name + "_placed";
                LuaTable configured = new LuaTable();
                configured.set("type", LuaValue.valueOf("minecraft:ore"));
                LuaTable config = new LuaTable();
                config.set("size", LuaValue.valueOf(size));
                config.set("discard_chance_on_air_exposure",
                    decimal(definition, 0.0, "discardChanceOnAirExposure", "DiscardChanceOnAirExposure"));
                config.set("targets", targets);
                configured.set("config", config);

                LuaTable placed = new LuaTable();
                placed.set("feature", LuaValue.valueOf(qualifiedId(service, configuredId)));
                LuaTable placement = new LuaTable();
                LuaTable count = new LuaTable();
                count.set("type", LuaValue.valueOf("minecraft:count"));
                count.set("count", LuaValue.valueOf(countValue));
                placement.set(1, count);
                LuaTable square = new LuaTable();
                square.set("type", LuaValue.valueOf("minecraft:in_square"));
                placement.set(2, square);
                LuaTable height = new LuaTable();
                height.set("type", LuaValue.valueOf("minecraft:height_range"));
                LuaTable uniform = new LuaTable();
                uniform.set("type", LuaValue.valueOf("minecraft:uniform"));
                LuaTable range = new LuaTable();
                uniform.set("min_inclusive", absolute(LuaValue.valueOf(minY)));
                uniform.set("max_inclusive", absolute(LuaValue.valueOf(maxY)));
                height.set("height", uniform);
                placement.set(3, height);
                LuaTable biome = new LuaTable();
                biome.set("type", LuaValue.valueOf("minecraft:biome"));
                placement.set(4, biome);
                placed.set("placement", placement);

                invokeRegistration(worldGeneration, "RegisterConfiguredFeature", configuredId, configured);
                invokeRegistration(worldGeneration, "RegisterPlacedFeature", placedId, placed);
                LuaTable biomeDefinition = new LuaTable();
                biomeDefinition.set("feature", LuaValue.valueOf(qualifiedId(service, placedId)));
                biomeDefinition.set("biomes", biomes);
                biomeDefinition.set("step", step);
                invokeRegistration(worldGeneration, "AddFeature", name, biomeDefinition);
                return LuaValue.valueOf(MLuaRegistryService.generatedAssetId(
                    service.apiContext().mlua().ownerModId(), name));
            }
        };
        worldGeneration.set("RegisterOreVein", register);
        worldGeneration.set("registerOreVein", register);
    }

    private static LuaValue field(LuaTable table, String lower, String upper) {
        LuaValue value = table.get(lower);
        return value.isnil() ? table.get(upper) : value;
    }

    private static void validateTargets(LuaTable targets) {
        for (int index = 1; index <= targets.length(); index++) {
            LuaValue entryValue = targets.get(index);
            if (!entryValue.istable()) {
                throw new LuaError("[MLua] Ore vein target " + index + " must be a table.");
            }
            LuaTable entry = entryValue.checktable();
            LuaValue predicateValue = entry.get("target");
            LuaValue stateValue = entry.get("state");
            if (!predicateValue.istable() || !stateValue.istable()) {
                throw new LuaError("[MLua] Ore vein target " + index + " requires target and state tables.");
            }
            LuaValue predicateType = predicateValue.checktable().get("predicate_type");
            LuaValue blockName = stateValue.checktable().get("Name");
            if (predicateType.type() != LuaValue.TSTRING || predicateType.tojstring().isBlank()) {
                throw new LuaError("[MLua] Ore vein target " + index + " requires target.predicate_type.");
            }
            if (blockName.type() != LuaValue.TSTRING || blockName.tojstring().isBlank()) {
                throw new LuaError("[MLua] Ore vein target " + index + " requires state.Name.");
            }
        }
    }

    private static int wholeNumber(LuaTable table, int fallback, String lower, String upper) {
        LuaValue value = field(table, lower, upper);
        if (value.isnil()) return fallback;
        double number = value.checkdouble();
        if (!Double.isFinite(number) || number != Math.rint(number)
            || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new LuaError("[MLua] Ore vein " + lower + " must be a 32-bit whole number.");
        }
        return (int)number;
    }

    private static LuaValue decimal(LuaTable table, double fallback, String lower, String upper) {
        LuaValue value = field(table, lower, upper);
        double result = value.isnil() ? fallback : value.checkdouble();
        if (!Double.isFinite(result) || result < 0.0 || result > 1.0) {
            throw new LuaError("[MLua] Ore vein discardChanceOnAirExposure must be between 0 and 1.");
        }
        return LuaValue.valueOf(result);
    }

    private static LuaTable absolute(LuaValue y) {
        LuaTable value = new LuaTable();
        value.set("absolute", y);
        return value;
    }

    private static String qualifiedId(MLuaRegistryService service, String id) {
        return id.indexOf(':') >= 0 ? id : service.apiContext().mlua().ownerModId() + ":" + id;
    }

    private static void invokeRegistration(LuaTable target, String method, String id, LuaTable definition) {
        LuaValue function = target.get(method);
        if (!function.isfunction()) throw new IllegalStateException("Missing world-generation method " + method);
        function.invoke(LuaValue.varargsOf(new LuaValue[]{target, LuaValue.valueOf(id), definition}));
    }
}
