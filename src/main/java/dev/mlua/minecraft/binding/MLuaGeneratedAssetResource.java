package dev.mlua.minecraft.binding;

import com.google.gson.JsonObject;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Objects;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Live identity and detached definition view for a generated recipe or loot table. */
public final class MLuaGeneratedAssetResource {
    private static final String BACKING = "__mlua_generated_asset_resource";

    private MLuaGeneratedAssetResource() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("GeneratedAssetResource", registration.requireInstanceBaseClassName(),
            "A live handle for an MLua-generated recipe or loot table. Definition reads return independent copies.");
        register(registration, "GetKind", "Returns the generated asset kind, such as recipe or loot_table.",
            asset -> LuaValue.valueOf(asset.kind().name().toLowerCase(java.util.Locale.ROOT)));
        register(registration, "GetId", "Returns the full namespaced resource identifier.",
            asset -> LuaValue.valueOf(asset.id()));
        register(registration, "GetDefinition", "Returns a fresh copy of the JSON definition, or nil after removal.",
            asset -> {
                JsonObject definition = find(asset);
                return definition == null ? LuaValue.NIL : MLuaJsonLuaConverter.toLua(definition);
            });
        register(registration, "Exists", "Checks whether this generated asset is still registered.",
            asset -> LuaValue.valueOf(find(asset) != null));
        registration.registerClassFunction("GeneratedAssetResource", "Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue other = args.arg(2);
                return LuaValue.valueOf(other.istable() && registration.isInstanceOf(other, "GeneratedAssetResource")
                    && state(args.arg1().checktable()).equals(state(other.checktable())));
            }
        }, "Compares generated asset kind and namespaced id.");
        registration.registerClassFunction("GeneratedAssetResource", "GetHashCode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(state(args.arg1().checktable()).hashCode());
            }
        }, "Returns a hash code derived from the kind and identifier.");
        registration.registerClassFunction("GeneratedAssetResource", "ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                State asset = state(args.arg1().checktable());
                return LuaValue.valueOf(asset.kind().name().toLowerCase(java.util.Locale.ROOT) + "#" + asset.id());
            }
        }, "Returns this resource as kind#namespace:path.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedAssetResource", "Kind", "GetKind", null,
            "string", "shared:Shared", "The generated data kind.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedAssetResource", "Id", "GetId", null,
            "string", "shared:Shared", "The full namespaced resource identifier.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedAssetResource", "Definition", "GetDefinition", null,
            "table|nil", "shared:Shared", "A copy of the generated JSON definition.");
        MLuaClassMethods.registerInstanceProperty(registration, "GeneratedAssetResource", "IsRegistered", "Exists", null,
            "boolean", "shared:Shared", "Whether this generated asset remains registered.");
    }

    public static LuaTable create(MLuaRegistration registration, GeneratedAssetKind kind, String id) {
        State asset = new State(kind, id);
        LuaTable instance = registration.createClassInstance("GeneratedAssetResource");
        instance.rawset(BACKING, LuaValue.userdataOf(asset));
        return instance;
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 java.util.function.Function<State, LuaValue> getter) {
        registration.registerClassFunction("GeneratedAssetResource", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return getter.apply(state(args.arg1().checktable()));
            }
        }, description);
    }

    private static State state(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object raw = value.isuserdata() ? value.touserdata() : null;
        if (!(raw instanceof State asset)) throw new LuaError("[MLua] Expected a GeneratedAssetResource instance.");
        return asset;
    }

    private static JsonObject find(State asset) {
        return MLuaLoaderRegistry.getGeneratedAssets(asset.kind()).get(asset.id());
    }

    private record State(GeneratedAssetKind kind, String id) {
        private State {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
        }
    }
}
