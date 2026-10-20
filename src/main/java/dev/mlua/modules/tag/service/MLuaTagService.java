package dev.mlua.modules.tag.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@MLuaDescription("Creates mutable item, block, and entity type tag builders for registration through Registry.Tags.")
public final class MLuaTagService extends MLuaReflectiveService {
    private static final String BUILDER_STATE = "__mlua_tag_builder_state";
    public static final int MAX_VALUES_PER_TAG = 16_384;

    public MLuaTagService(Context context) {
        super(context);
    }

    /** Registers the shared builder API once; each created builder only owns its data. */
    public static void registerBuilderClasses(MLuaRegistration registration) {
        registration.registerClass("TagBuilder", registration.requireInstanceBaseClassName(),
            "A mutable builder for collecting item, block, or entity type tag values.");
        registration.registerClass("ItemTagBuilder", "TagBuilder", "A builder for item tags.");
        registration.registerClass("BlockTagBuilder", "TagBuilder", "A builder for block tags.");
        registration.registerClass("EntityTypeTagBuilder", "TagBuilder", "A builder for entity type tags.");

        registerBuilderFunction(registration, "Add", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                String value = normalizeValue(args.checkjstring(2));
                if (!state.values.contains(value) && state.values.size() >= MAX_VALUES_PER_TAG) {
                    throw limitError("Add");
                }
                state.values.add(value);
                return args.arg1();
            }
        }, "Adds a registry identifier or #tag reference. Duplicate values are ignored.");

        registerBuilderFunction(registration, "AddAll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                List<String> additions = normalizeValues(args.arg(2), "AddAll");
                BuilderState state = state(args.arg1());
                ensureLimit(state.values, additions, "AddAll");
                state.values.addAll(additions);
                return args.arg1();
            }
        }, "Adds all values from a validated dense array. Duplicate values are ignored.");

        registerBuilderFunction(registration, "SetValues", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                List<String> replacement = normalizeValues(args.arg(2), "SetValues");
                LinkedHashSet<String> distinct = new LinkedHashSet<>(replacement);
                if (distinct.size() > MAX_VALUES_PER_TAG) throw limitError("SetValues");
                BuilderState state = state(args.arg1());
                state.values.clear();
                state.values.addAll(distinct);
                return args.arg1();
            }
        }, "Replaces all values with a validated dense array, preserving first-seen order.");

        registerBuilderFunction(registration, "Remove", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String value = normalizeValue(args.checkjstring(2));
                return LuaValue.valueOf(state(args.arg1()).values.remove(value));
            }
        }, "Removes an identifier and returns whether it was present.");

        registerBuilderFunction(registration, "Contains", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String value = normalizeValue(args.checkjstring(2));
                return LuaValue.valueOf(state(args.arg1()).values.contains(value));
            }
        }, "Checks whether the builder contains an identifier.");

        registerBuilderFunction(registration, "Clear", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                state(args.arg1()).values.clear();
                return args.arg1();
            }
        }, "Removes every value from the builder.");

        registerBuilderFunction(registration, "GetValues", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable values = new LuaTable();
                int index = 1;
                for (String value : state(args.arg1()).values) {
                    values.set(index++, LuaValue.valueOf(value));
                }
                return values;
            }
        }, "Returns the current values as a dense array in insertion order.");

        registerBuilderFunction(registration, "SetReplace", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!args.arg(2).isboolean()) {
                    throw new LuaError("[MLua] TagBuilder:SetReplace expects a boolean.");
                }
                state(args.arg1()).replace = args.arg(2).toboolean();
                return args.arg1();
            }
        }, "Sets whether this tag replaces values from lower-priority definitions.");

        registerBuilderFunction(registration, "GetReplace", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(state(args.arg1()).replace);
            }
        }, "Returns whether this tag replaces values from lower-priority definitions.");

        registerBuilderFunction(registration, "Build", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1());
                JsonArray values = new JsonArray();
                state.values.forEach(values::add);
                JsonObject definition = new JsonObject();
                definition.addProperty("replace", state.replace);
                definition.add("values", values);
                return LuaValue.valueOf(definition.toString());
            }
        }, "Serializes this tag definition as JSON.");
    }

    private static void registerBuilderFunction(MLuaRegistration registration, String name,
                                               LuaValue implementation, String description) {
        registration.registerClassFunction("TagBuilder", name, implementation, description);
    }

    @Override
    public String name() {
        return "Tag";
    }

    @MLuaDescription("Creates a builder for an item tag.")
    public LuaTable Items() { return createBuilder("ItemTagBuilder"); }

    @MLuaDescription("Creates a builder for a block tag.")
    public LuaTable Blocks() { return createBuilder("BlockTagBuilder"); }

    @MLuaDescription("Creates a builder for an entity type tag.")
    public LuaTable EntityTypes() { return createBuilder("EntityTypeTagBuilder"); }

    private LuaTable createBuilder(String className) {
        MLuaRegistration registration = this.context.registration();
        if (registration.classTable(className) == null) {
            throw new IllegalStateException("Lua class '" + className + "' must be registered before creating tag builders");
        }
        LuaTable builder = registration.createClassInstance(className);
        builder.rawset(BUILDER_STATE, LuaValue.userdataOf(new BuilderState()));
        return builder;
    }

    private static List<String> normalizeValues(LuaValue input, String method) {
        if (!input.istable()) {
            throw new LuaError("[MLua] TagBuilder:" + method + " expects a dense array of identifiers.");
        }
        LuaTable values = input.checktable();
        int length = values.length();
        if (length > MAX_VALUES_PER_TAG || values.keys().length != length) {
            throw new LuaError("[MLua] TagBuilder:" + method + " expects a dense array with at most "
                + MAX_VALUES_PER_TAG + " identifiers and no extra keys.");
        }
        List<String> normalized = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            LuaValue value = values.get(index);
            if (!value.isstring()) {
                throw new LuaError("[MLua] TagBuilder:" + method + " entry " + index
                    + " must be a resource identifier string.");
            }
            normalized.add(normalizeValue(value.tojstring()));
        }
        return normalized;
    }

    private static void ensureLimit(LinkedHashSet<String> current, List<String> additions, String method) {
        if (current.size() + additions.stream().filter(value -> !current.contains(value)).distinct().count()
            > MAX_VALUES_PER_TAG) {
            throw limitError(method);
        }
    }

    private static LuaError limitError(String method) {
        return new LuaError("[MLua] TagBuilder:" + method + " allows at most " + MAX_VALUES_PER_TAG + " distinct values.");
    }

    private static BuilderState state(LuaValue value) {
        if (!value.istable()) throw new LuaError("[MLua] TagBuilder method requires a builder instance.");
        LuaValue stateValue = value.checktable().rawget(BUILDER_STATE);
        Object state = stateValue.isuserdata() ? stateValue.touserdata() : null;
        if (!(state instanceof BuilderState builderState)) {
            throw new LuaError("[MLua] TagBuilder is not attached to live builder state.");
        }
        return builderState;
    }

    private static String normalizeValue(String value) {
        String trimmed = value == null ? "" : value.trim();
        boolean tagReference = trimmed.startsWith("#");
        String identifier = tagReference ? trimmed.substring(1).trim() : trimmed;
        var location = MLuaResourceLocations.tryParseOrNull(identifier);
        if (location == null) {
            throw new LuaError("[MLua] Invalid tag entry '" + value
                + "'; expected a resource location or #tag reference.");
        }
        return (tagReference ? "#" : "") + location;
    }

    private static final class BuilderState {
        private final LinkedHashSet<String> values = new LinkedHashSet<>();
        private boolean replace;
    }
}
