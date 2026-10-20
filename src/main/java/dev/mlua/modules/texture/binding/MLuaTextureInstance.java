package dev.mlua.modules.texture.binding;

import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.module.MLuaRegistration;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

public final class MLuaTextureInstance {
    private final String name;
    private final String location;

    public MLuaTextureInstance(String name, String location) {
        this.name = name;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public String location() {
        return location;
    }

    public LuaTable toLua(MLuaRegistration registration) {
        LuaTable table = new LuaTable();
        table.rawset("__side", LuaValue.valueOf("Client"));
        table.rawset("__texture_name", LuaValue.valueOf(name));
        int separator = location.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : location.substring(0, separator);
        String path = separator < 0 ? location : location.substring(separator + 1);
        table.rawset("__texture_path", LuaValue.valueOf(location));
        table.rawset("__texture_namespace", LuaValue.valueOf(namespace));
        table.rawset("__texture_resource_path", LuaValue.valueOf(path));
        registration.attachClassInstance(table, "TextureInstance", null);
        bind(registration, table, "GetName", "__texture_name");
        bind(registration, table, "GetPath", "__texture_path");
        bind(registration, table, "GetNamespace", "__texture_namespace");
        bind(registration, table, "GetTexturePath", "__texture_resource_path");
        registration.initializeClassInstance(table, "TextureInstance", null);
        return table;
    }

    private static void bind(MLuaRegistration registration, LuaTable instance, String method, String field) {
        MLuaClassMethods.bindInstanceMethod(registration, "TextureInstance", instance, method, new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                LuaValue value = args.arg1().checktable().rawget(field);
                if (value.isnil()) throw new LuaError("[MLua] TextureInstance is missing " + field);
                return value;
            }
        });
    }
}
