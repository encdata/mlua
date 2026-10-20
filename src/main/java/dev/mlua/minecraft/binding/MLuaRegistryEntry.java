package dev.mlua.minecraft.binding;

import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaRegistryEntry {
    private static final String BACKING = "__mlua_registry_entry";

    private MLuaRegistryEntry() { }

    public static void registerClass(MLuaRegistration registration) {
        registration.registerClass("RegistryEntry", registration.requireInstanceBaseClassName(),
            "A live view of a namespaced key in one of MLua's supported Minecraft registries.");
        registration.registerClassFunction("RegistryEntry", "new", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                int offset = args.arg1().istable() && args.arg1().rawget("__class").toboolean() ? 1 : 0;
                return create(registration, kind(args.arg(offset + 1).checkjstring()), args.arg(offset + 2).checkjstring());
            }
        }, "Creates a RegistryEntry from a registry kind and namespaced identifier.");
        register(registration, "GetKind", "Returns the normalized registry kind.", entry -> LuaValue.valueOf(entry.kind.name().toLowerCase(java.util.Locale.ROOT)));
        register(registration, "GetId", "Returns the full namespaced registry identifier.", entry -> LuaValue.valueOf(entry.id.toString()));
        register(registration, "GetNamespace", "Returns the identifier namespace.", entry -> LuaValue.valueOf(MLuaResourceLocations.namespace(entry.id)));
        register(registration, "GetPath", "Returns the identifier path.", entry -> LuaValue.valueOf(MLuaResourceLocations.path(entry.id)));
        register(registration, "Exists", "Checks whether the key currently resolves in its Minecraft registry.", entry -> LuaValue.valueOf(exists(entry)));
        register(registration, "GetValue", "Returns the resolved native Minecraft registry value as Java userdata. Use Java:Cast or Java:Invoke to inspect or pass it to Java APIs.", entry -> {
            if (!exists(entry)) {
                String state = isKnown(entry.kind, entry.id.toString())
                    ? "is not available until native registry registration completes"
                    : "does not exist in Minecraft's registry";
                throw new LuaError("[MLua] Registry entry '" + entry.kind.name().toLowerCase(java.util.Locale.ROOT)
                    + ":" + entry.id + "' " + state + ".");
            }
            return LuaValue.userdataOf(value(entry));
        });
        register(registration, "IsKnown", "Checks whether the key is registered with MLua or resolves in Minecraft's registry.",
            entry -> LuaValue.valueOf(isKnown(entry.kind, entry.id.toString())));
        registration.registerClassFunction("RegistryEntry", "Equals", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Entry entry = backing(args.arg1().checktable());
                LuaValue other = args.arg(2);
                return LuaValue.valueOf(other.istable()
                    && registration.isInstanceOf(other, "RegistryEntry")
                    && entry.equals(backing(other.checktable())));
            }
        }, "Compares registry kind and namespaced identifier with another RegistryEntry.");
        register(registration, "GetHashCode", "Returns a hash code derived from this registry key.",
            entry -> LuaValue.valueOf(entry.hashCode()));
        register(registration, "ToString", "Returns this registry key as kind:namespace:path.",
            entry -> LuaValue.valueOf(entry.kind.name().toLowerCase(java.util.Locale.ROOT) + ":" + entry.id));
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryEntry", "Kind", "GetKind", null,
            "string", "shared:Shared", "The normalized registry kind.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryEntry", "Id", "GetId", null,
            "string", "shared:Shared", "The full namespaced registry identifier.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryEntry", "Namespace", "GetNamespace", null,
            "string", "shared:Shared", "The identifier namespace.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryEntry", "Path", "GetPath", null,
            "string", "shared:Shared", "The identifier path.");
        MLuaClassMethods.registerInstanceProperty(registration, "RegistryEntry", "IsRegistered", "Exists", null,
            "boolean", "shared:Shared", "Whether this key currently resolves in its Minecraft registry.");
    }

    private static void register(MLuaRegistration registration, String name, String description,
                                 java.util.function.Function<Entry, LuaValue> getter) {
        registration.registerClassFunction("RegistryEntry", name, new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return getter.apply(backing(args.arg1().checktable()));
            }
        }, description);
    }

    public static LuaTable create(MLuaRegistration registration, RegistryKind kind, String identifier) {
        if (kind == null) throw new LuaError("[MLua] Registry kind is required");
        var id = MLuaResourceLocations.tryParseOrNull(identifier);
        if (id == null) throw new LuaError("[MLua] Invalid registry identifier: " + identifier);
        LuaTable instance = registration.createClassInstance("RegistryEntry");
        instance.rawset(BACKING, LuaValue.userdataOf(new Entry(kind, id)));
        return instance;
    }

    private static Entry backing(LuaTable instance) {
        LuaValue value = instance.rawget(BACKING);
        Object backing = value.isuserdata() ? value.touserdata() : null;
        if (!(backing instanceof Entry entry)) throw new LuaError("[MLua] RegistryEntry has no live registry key");
        return entry;
    }

    private static boolean exists(Entry entry) {
        return switch (entry.kind) {
            case ITEM -> BuiltInRegistries.ITEM.containsKey(entry.id);
            case BLOCK_ITEM -> BuiltInRegistries.ITEM.getOptional(entry.id).filter(BlockItem.class::isInstance).isPresent();
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(entry.id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(entry.id);
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.containsKey(entry.id);
            case POTION -> BuiltInRegistries.POTION.containsKey(entry.id);
            case MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT.containsKey(entry.id);
            case CREATIVE_TAB -> BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(entry.id);
        };
    }

    private static Object value(Entry entry) {
        return switch (entry.kind) {
            case ITEM, BLOCK_ITEM -> BuiltInRegistries.ITEM.get(entry.id);
            case BLOCK -> BuiltInRegistries.BLOCK.get(entry.id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.get(entry.id);
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.get(entry.id);
            case POTION -> BuiltInRegistries.POTION.get(entry.id);
            case MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT.get(entry.id);
            case CREATIVE_TAB -> BuiltInRegistries.CREATIVE_MODE_TAB.get(entry.id);
        };
    }

    /**
     * Returns whether this identifier names either a live vanilla registry value or
     * an MLua registration that is waiting for its loader to resolve. Lookup APIs
     * use this so a pending registration can be found during the registry phase,
     * while arbitrary unknown identifiers still return nil.
     */
    public static boolean isKnown(RegistryKind kind, String identifier) {
        if (kind == null) return false;
        var id = MLuaResourceLocations.tryParseOrNull(identifier);
        if (id == null) return false;
        if (kind == RegistryKind.BLOCK_ITEM) {
            String namespace = MLuaResourceLocations.namespace(id);
            String path = MLuaResourceLocations.path(id);
            boolean pendingBlock = MLuaLoaderRegistry.isRegistered(RegistryKind.BLOCK, namespace, path);
            boolean pendingItem = MLuaLoaderRegistry.isRegistered(RegistryKind.BLOCK_ITEM, namespace, path);
            return pendingBlock && pendingItem || exists(new Entry(kind, id));
        }
        if (MLuaLoaderRegistry.isRegistered(kind,
                MLuaResourceLocations.namespace(id), MLuaResourceLocations.path(id))) {
            return true;
        }
        return exists(new Entry(kind, id));
    }

    public static RegistryKind kind(String name) {
        if (name == null) throw new LuaError("[MLua] Registry kind is required");
        return switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "item", "items" -> RegistryKind.ITEM;
            case "block", "blocks" -> RegistryKind.BLOCK;
            case "block_item", "blockitem", "blockitems" -> RegistryKind.BLOCK_ITEM;
            case "entity", "entities", "entity_type", "entity_types" -> RegistryKind.ENTITY;
            case "particle", "particles" -> RegistryKind.PARTICLE;
            case "potion", "potions" -> RegistryKind.POTION;
            case "effect", "effects", "mob_effect", "mob_effects" -> RegistryKind.MOB_EFFECT;
            case "tab", "tabs", "creative_tab", "creative_tabs" -> RegistryKind.CREATIVE_TAB;
            default -> throw new LuaError("[MLua] Unknown registry kind '" + name + "'");
        };
    }

    private record Entry(RegistryKind kind, ResourceLocation id) { }
}
