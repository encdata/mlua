package dev.mlua.minecraft.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Live, read-only handle for an entry in an addon-owned registry category. */
public final class MLuaRegistryDefinition {
    private static final String BACKING = "__mlua_registry_definition";

    private MLuaRegistryDefinition() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("RegistryDefinition", registration.requireInstanceBaseClassName(),
            "A live handle for an entry in an addon-owned Registry category. Definition tables returned by this class are copies.");
        register(registration, "GetId", "Returns this entry's full namespaced identifier.", entry -> LuaValue.valueOf(entry.reference.id()));
        register(registration, "GetCategory", "Returns the canonical addon-owned Registry category.", entry -> LuaValue.valueOf(entry.reference.category()));
        register(registration, "GetOwner", "Returns the addon that registered this definition.", entry -> LuaValue.valueOf(entry.reference.owner()));
        register(registration, "GetCategoryDescription", "Returns the current description registered for this category, or nil when the category was removed.", entry -> {
            String description = entry.catalog.categoryDescription(entry.reference);
            return description == null ? LuaValue.NIL : LuaValue.valueOf(description);
        });
        register(registration, "GetDefinition", "Returns a copy while this exact entry registration remains active, or nil after it is removed or replaced.", entry -> {
            LuaTable definition = entry.catalog.definition(entry.reference);
            return definition == null ? LuaValue.NIL : definition;
        });
        register(registration, "Exists", "Checks whether this exact entry registration remains active.", entry ->
            LuaValue.valueOf(entry.catalog.containsDefinition(entry.reference)));
        registration.registerClassFunction("RegistryDefinition", "Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Entry entry = backing(args.arg1().checktable());
                LuaValue other = args.arg(2);
                if (!other.istable() || !registration.isInstanceOf(other, "RegistryDefinition")) {
                    return LuaValue.FALSE;
                }
                Entry candidate = backing(other.checktable());
                return LuaValue.valueOf(entry.sameKey(candidate));
            }
        }, "Compares category, owner, and identifier with another RegistryDefinition.");
        registration.registerClassFunction("RegistryDefinition", "GetHashCode", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(backing(args.arg1().checktable()).keyHashCode());
            }
        }, "Returns a hash code derived from the runtime catalog, category, owner, and identifier.");
        registration.registerClassFunction("RegistryDefinition", "ToString", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Entry entry = backing(args.arg1().checktable());
                return LuaValue.valueOf(entry.reference.category() + "#" + entry.reference.id());
            }
        }, "Returns this entry as category#namespace:path.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "Id", "GetId", null,
            "string", "shared:Shared", "The full namespaced definition identifier.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "Category", "GetCategory", null,
            "string", "shared:Shared", "The canonical addon-owned registry category.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "Owner", "GetOwner", null,
            "string", "shared:Shared", "The addon that registered this definition.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "CategoryDescription", "GetCategoryDescription", null,
            "string|nil", "shared:Shared", "The current category description, or nil after category removal.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "Definition", "GetDefinition", null,
            "table|nil", "shared:Shared", "A copy while this exact entry registration remains active, or nil after removal or replacement.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryDefinition", "IsRegistered", "Exists", null,
            "boolean", "shared:Shared", "Whether this exact entry registration remains active.");
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 java.util.function.Function<Entry, LuaValue> getter) {
        registration.registerClassFunction("RegistryDefinition", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return getter.apply(backing(args.arg1().checktable()));
            }
        }, description);
    }

    public static LuaTable create(MLuaRegistration registration, MLuaDynamicRegistryCatalog catalog,
                                  String category, String id) {
        MLuaDynamicRegistryCatalog.DefinitionReference reference = catalog.definitionReference(category, id);
        if (reference == null) return null;
        LuaTable instance = registration.createClassInstance("RegistryDefinition");
        instance.rawset(BACKING, LuaValue.userdataOf(new Entry(catalog, reference)));
        return instance;
    }

    private static Entry backing(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object backing = value.isuserdata() ? value.touserdata() : null;
        if (!(backing instanceof Entry entry)) throw new LuaError("[MLua] RegistryDefinition has no live registry backing.");
        return entry;
    }

    private record Entry(MLuaDynamicRegistryCatalog catalog,
                         MLuaDynamicRegistryCatalog.DefinitionReference reference) {
        private boolean sameKey(Entry other) {
            return other != null && this.catalog == other.catalog
                && this.reference.category().equals(other.reference.category())
                && this.reference.id().equals(other.reference.id())
                && this.reference.owner().equals(other.reference.owner());
        }

        private int keyHashCode() {
            int result = System.identityHashCode(this.catalog);
            result = 31 * result + this.reference.category().hashCode();
            result = 31 * result + this.reference.id().hashCode();
            return 31 * result + this.reference.owner().hashCode();
        }
    }
}
