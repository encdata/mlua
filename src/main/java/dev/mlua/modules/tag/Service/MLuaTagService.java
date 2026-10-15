package dev.mlua.modules.tag.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;

@MLuaDescription("Creates mutable item, block, and entity type tag builders for registration through Registry.Tags.")
public final class MLuaTagService extends MLuaReflectiveService {
    private static final String BUILDER_STATE = "__mlua_tag_builder_state";

    public MLuaTagService(Context context) {
        super(context);
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
        MLuaClassMethods.setInstanceMethod(builder, "Add", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                String value = args.checkjstring(2);
                MLuaResourceLocations.parse(value.startsWith("#") ? value.substring(1) : value);
                state(self).values.add(value);
                return self;
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "AddAll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                LuaValue input = args.arg(2);
                if (!input.istable()) throw new LuaError("[MLua] TagBuilder:AddAll expects a dense array of identifiers");
                LuaTable values = input.checktable();
                int length = values.length();
                LuaValue[] keys = values.keys();
                if (keys.length != length) throw new LuaError("[MLua] TagBuilder:AddAll expects a dense array without extra keys");
                List<String> additions = new ArrayList<>(length);
                for (int index = 1; index <= length; index++) {
                    String value = values.get(index).checkjstring();
                    MLuaResourceLocations.parse(value.startsWith("#") ? value.substring(1) : value);
                    additions.add(value);
                }
                state(self).values.addAll(additions);
                return self;
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "Remove", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                String value = args.checkjstring(2);
                MLuaResourceLocations.parse(value.startsWith("#") ? value.substring(1) : value);
                return LuaValue.valueOf(state(self).values.remove(value));
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "Contains", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                String value = args.checkjstring(2);
                MLuaResourceLocations.parse(value.startsWith("#") ? value.substring(1) : value);
                return LuaValue.valueOf(state(self).values.contains(value));
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "Clear", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                state(self).values.clear();
                return self;
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "GetValues", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable values = new LuaTable();
                int index = 1;
                for (String value : state(args.arg1().checktable()).values) {
                    values.set(index++, LuaValue.valueOf(value));
                }
                return values;
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "SetReplace", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable self = args.arg1().checktable();
                state(self).replace = args.checkboolean(2);
                return self;
            }
        });
        MLuaClassMethods.setInstanceMethod(builder, "Build", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                BuilderState state = state(args.arg1().checktable());
                JsonArray values = new JsonArray();
                state.values.forEach(values::add);
                JsonObject definition = new JsonObject();
                definition.addProperty("replace", state.replace);
                definition.add("values", values);
                return LuaValue.valueOf(definition.toString());
            }
        });
        return builder;
    }

    private static BuilderState state(LuaTable instance) {
        LuaValue value = instance.rawget(BUILDER_STATE);
        Object state = value.isuserdata() ? value.touserdata() : null;
        if (!(state instanceof BuilderState builderState)) {
            throw new LuaError("[MLua] TagBuilder is not attached to live builder state");
        }
        return builderState;
    }

    private static final class BuilderState {
        private final LinkedHashSet<String> values = new LinkedHashSet<>();
        private boolean replace;
    }
}
