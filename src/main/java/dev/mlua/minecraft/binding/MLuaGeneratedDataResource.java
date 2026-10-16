package dev.mlua.minecraft.binding;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Objects;
import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Live identity and copied definition view for a generated data-pack resource. */
public final class MLuaGeneratedDataResource {
    private static final String STATE = "__mlua_generated_data_resource";

    private MLuaGeneratedDataResource() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("GeneratedDataResource", registration.requireInstanceBaseClassName(),
            "A live handle for an addon-generated data resource. Definition reads return independent copies.");
        register(registration, "GetResourceType", "Returns the Minecraft data resource type, such as worldgen/biome.",
            entry -> LuaValue.valueOf(state(entry).type()));
        register(registration, "GetId", "Returns the full namespaced resource id.",
            entry -> LuaValue.valueOf(state(entry).id()));
        register(registration, "GetDefinition", "Returns a copy of the generated JSON definition, or nil after removal.",
            entry -> {
                State value = state(entry);
                JsonObject definition = find(value);
                return definition == null ? LuaValue.NIL : jsonToLua(definition);
            });
        register(registration, "Exists", "Checks whether this resource is still registered.",
            entry -> LuaValue.valueOf(find(state(entry)) != null));
        registration.registerClassFunction("GeneratedDataResource", "Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                return LuaValue.valueOf(other.istable() && registration.isInstanceOf(other, "GeneratedDataResource")
                    && state(args.arg1().checktable()).equals(state(other.checktable())));
            }
        }, "Compares data resource type and namespaced id.");
        registration.registerClassFunction("GeneratedDataResource", "GetHashCode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.valueOf(state(args.arg1().checktable()).hashCode()); }
        }, "Returns a hash code derived from this data resource's type and id.");
        registration.registerClassFunction("GeneratedDataResource", "ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                State value = state(args.arg1().checktable());
                return LuaValue.valueOf(value.type() + "#" + value.id());
            }
        }, "Returns this resource as type#namespace:path.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedDataResource", "Type", "GetResourceType", null,
            "string", "shared:Shared", "The Minecraft data resource type.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedDataResource", "Id", "GetId", null,
            "string", "shared:Shared", "The full namespaced resource id.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedDataResource", "Definition", "GetDefinition", null,
            "table|nil", "shared:Shared", "A copy of the resource JSON definition, or nil after removal.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedDataResource", "IsRegistered", "Exists", null,
            "boolean", "shared:Shared", "Whether this resource is still registered.");
    }

    public static LuaTable create(MLuaRegistration registration, String type, String id) {
        State state = new State(type, id);
        if (!MLuaLoaderRegistry.hasGeneratedDataResource(type, id)) return null;
        LuaTable instance = registration.createClassInstance("GeneratedDataResource");
        instance.rawset(STATE, LuaValue.userdataOf(state));
        return instance;
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 Function<LuaTable, LuaValue> getter) {
        registration.registerClassFunction("GeneratedDataResource", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return getter.apply(args.arg1().checktable()); }
        }, description);
    }

    private static State state(LuaTable instance) {
        LuaValue value = instance.rawget(STATE);
        Object raw = value.isuserdata() ? value.touserdata() : null;
        if (!(raw instanceof State state)) throw new LuaError("[MLua] Expected a GeneratedDataResource instance.");
        return state;
    }

    private static JsonObject find(State state) {
        return MLuaLoaderRegistry.getGeneratedDataResource(state.type(), state.id());
    }

    private static LuaValue jsonToLua(JsonElement element) {
        if (element == null || element.isJsonNull()) return LuaValue.NIL;
        if (element.isJsonPrimitive()) {
            JsonPrimitive value = element.getAsJsonPrimitive();
            if (value.isBoolean()) return LuaValue.valueOf(value.getAsBoolean());
            if (value.isNumber()) return LuaValue.valueOf(value.getAsNumber().doubleValue());
            return LuaValue.valueOf(value.getAsString());
        }
        if (element.isJsonArray()) {
            LuaTable result = new LuaTable();
            JsonArray values = element.getAsJsonArray();
            for (int index = 0; index < values.size(); index++) result.set(index + 1, jsonToLua(values.get(index)));
            return result;
        }
        LuaTable result = new LuaTable();
        JsonObject values = element.getAsJsonObject();
        for (var entry : values.entrySet()) result.set(entry.getKey(), jsonToLua(entry.getValue()));
        return result;
    }

    private record State(String type, String id) {
        private State {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(id, "id");
        }
    }
}
