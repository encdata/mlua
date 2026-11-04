package dev.mlua.runtime.entity;

import dev.mlua.common.enums.Side;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.service.MLuaServiceProperties;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import net.minecraft.world.entity.Entity;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Builds the stable Lua class instance exposed for an attached entity component. */
final class MLuaEntityComponentHandle {
    interface Access {
        Side side();
        String name();
        Entity entity();
        LuaTable state();
        boolean enabled();
        boolean failed();
        String errorMessage();
        void setEnabled(boolean enabled);
        void destroy();
    }

    private MLuaEntityComponentHandle() { }

    static LuaTable entityTable(Entity entity) {
        var registration = MLuaRuntime.registrationForLevel(entity.level());
        return MLuaEntityWrapper.entityTable(entity, registration);
    }

    static LuaTable initialState(LuaTable callbacks) {
        LuaTable state = new LuaTable();
        LuaValue initialState = callbacks.get("State");
        if (initialState.isnil()) initialState = callbacks.get("state");
        if (!initialState.istable()) return state;
        LuaTable source = initialState.checktable();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = source.next(key);
            key = next.arg1();
            if (key.isnil()) return state;
            state.set(key, next.arg(2));
        }
    }

    static LuaTable create(Access component, MLuaRegistration registration) {
        LuaTable table = new LuaTable();
        boolean typed = registration != null && registration.classTable("EntityComponent") != null;
        table.rawset("__side", LuaValue.valueOf(component.side().name()));
        table.rawset("__component_name", LuaValue.valueOf(component.name()));
        if (typed) registration.attachClassInstance(table, "EntityComponent", null);
        else table.rawset("Name", LuaValue.valueOf(component.name()));

        bind(registration, typed, table, "GetName", args -> LuaValue.valueOf(component.name()));
        bind(registration, typed, table, "GetEntity",
            args -> entityTable(component.entity()));
        bind(registration, typed, table, "GetState", args ->
            args.narg() < 2 || args.arg(2).isnil() ? component.state() : component.state().get(args.arg(2)));
        bind(registration, typed, table, "SetState", args -> {
            component.state().set(args.arg(2), args.arg(3));
            return LuaValue.NONE;
        });
        bind(registration, typed, table, "GetEnabled", args -> LuaValue.valueOf(component.enabled()));
        bind(registration, typed, table, "GetFailed", args -> LuaValue.valueOf(component.failed()));
        bind(registration, typed, table, "GetError", args -> component.errorMessage() == null
            ? LuaValue.NIL : LuaValue.valueOf(component.errorMessage()));
        bind(registration, typed, table, "SetEnabled", args -> {
            component.setEnabled(args.checkboolean(2));
            return LuaValue.NONE;
        });
        bind(registration, typed, table, "Destroy", args -> {
            component.destroy();
            return LuaValue.NONE;
        });
        if (typed) registration.initializeClassInstance(table, "EntityComponent", null);
        return MLuaServiceProperties.attach(table);
    }

    private static void bind(MLuaRegistration registration, boolean typed, LuaTable table,
                             String name, java.util.function.Function<Varargs, Varargs> implementation) {
        VarArgFunction function = new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return implementation.apply(args); }
        };
        if (typed) MLuaClassMethods.bindInstanceMethod(registration, "EntityComponent", table, name, function);
        else table.set(name, function);
    }
}
