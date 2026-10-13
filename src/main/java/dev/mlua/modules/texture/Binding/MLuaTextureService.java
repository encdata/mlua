package dev.mlua.modules.texture.Binding;

import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.ResourceLocation;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MLuaTextureService extends MLuaServiceBase {
    private final Map<String, ResourceLocation> textures = new ConcurrentHashMap<>();

    public MLuaTextureService(MLuaService.Context context) {
        super(context);
    }

    @Override
    public String name() { return "TextureService"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Client");

        table.set("RegisterTexture", fn(args -> {
            String name = args.checkjstring(2);
            String path = args.checkjstring(3);
            if (name.isBlank()) {
                throw new LuaError("[MLua] RegisterTexture requires a non-empty name");
            }
            if (path.isBlank()) {
                throw new LuaError("[MLua] RegisterTexture requires a non-empty path");
            }
            ResourceLocation location = MLuaResourceLocations.build(
                context.mlua().ownerModId(), path
            );
            textures.put(name, location);
            return new MLuaTextureInstance(name, location).toLua();
        }));

        table.set("GetTexture", fn(args -> {
            String name = args.checkjstring(2);
            ResourceLocation location = textures.get(name);
            if (location == null) return LuaValue.NIL;
            return new MLuaTextureInstance(name, location).toLua();
        }));

        table.set("RemoveTexture", fn(args -> {
            String name = args.checkjstring(2);
            ResourceLocation removed = textures.remove(name);
            return LuaValue.valueOf(removed != null);
        }));

        table.set("HasTexture", fn(args -> {
            String name = args.checkjstring(2);
            return LuaValue.valueOf(textures.containsKey(name));
        }));

        table.set("GetRegisteredTextures", fn(args -> {
            LuaTable result = new LuaTable();
            for (Map.Entry<String, ResourceLocation> entry : textures.entrySet()) {
                result.set(entry.getKey(),
                    new MLuaTextureInstance(entry.getKey(), entry.getValue()).toLua());
            }
            return result;
        }));

        table.set("ClearTextures", fn(args -> {
            textures.clear();
            return LuaValue.NONE;
        }));

        return table;
    }

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    return body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
