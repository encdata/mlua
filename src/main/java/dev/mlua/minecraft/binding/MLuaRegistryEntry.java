package dev.mlua.minecraft.binding;

import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Live identity view for a registry key, shared across all supported registry kinds. */
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
            case ITEM, BLOCK_ITEM -> BuiltInRegistries.ITEM.containsKey(entry.id);
            case BLOCK -> BuiltInRegistries.BLOCK.containsKey(entry.id);
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.containsKey(entry.id);
            case PARTICLE -> BuiltInRegistries.PARTICLE_TYPE.containsKey(entry.id);
            case POTION -> BuiltInRegistries.POTION.containsKey(entry.id);
            case MOB_EFFECT -> BuiltInRegistries.MOB_EFFECT.containsKey(entry.id);
            case CREATIVE_TAB -> MLuaLoaderRegistry.getRegisteredIds(RegistryKind.CREATIVE_TAB).contains(entry.id.toString());
        };
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

    private record Entry(RegistryKind kind, net.minecraft.resources.ResourceLocation id) { }
}
