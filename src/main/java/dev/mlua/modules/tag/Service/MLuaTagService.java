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

@MLuaDescription("Service for creating tag builders.")
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
    public LuaTable Items() { return createBuilder(); }

    @MLuaDescription("Creates a builder for a block tag.")
    public LuaTable Blocks() { return createBuilder(); }

    @MLuaDescription("Creates a builder for an entity type tag.")
    public LuaTable EntityTypes() { return createBuilder(); }

    private LuaTable createBuilder() {
        MLuaRegistration registration = this.context.registration();
        if (registration.classTable("TagBuilder") == null) {
            throw new IllegalStateException("TagBuilder class must be registered before creating tag builders");
        }

        LuaTable builder = registration.createClassInstance("TagBuilder");
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
        private final java.util.List<String> values = new java.util.ArrayList<>();
        private boolean replace;
    }
}
