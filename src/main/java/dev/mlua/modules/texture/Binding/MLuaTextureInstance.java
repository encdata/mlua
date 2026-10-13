package dev.mlua.modules.texture.Binding;

import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public final class MLuaTextureInstance {
    private final String name;
    private final ResourceLocation location;

    public MLuaTextureInstance(String name, ResourceLocation location) {
        this.name = name;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public ResourceLocation location() {
        return location;
    }

    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("Name", LuaValue.valueOf(name));
        table.set("Path", LuaValue.valueOf(location.toString()));
        table.set("Namespace", LuaValue.valueOf(location.getNamespace()));
        table.set("TexturePath", LuaValue.valueOf(location.getPath()));
        table.set("__class", LuaValue.valueOf("TextureInstance"));
        return table;
    }
}
