package dev.mlua.modules.texture.binding;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.mcapi.MCApiRuntime;
import org.luaj.vm2.LuaTable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;

@MLuaDescription("Service for registering and managing textures.")
public final class MLuaTextureService extends MLuaReflectiveService {
    private final TextureState state;

    public MLuaTextureService(MLuaService.Context context) {
        super(context);
        this.state = this.sharedState("textures:" + this.context.ownerModId(), TextureState.class,
            TextureState::new);
    }

    @Override
    public String name() { return "TextureService"; }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Client");
        return table;
    }

    @MLuaDescription("Registers a texture from the calling mod's texture directory.")
    public LuaTable RegisterTexture(String name, String path) {
        if (name.isBlank()) {
            throw new LuaError("[MLua] RegisterTexture requires a non-empty name");
        }
        if (path.isBlank()) {
            throw new LuaError("[MLua] RegisterTexture requires a non-empty path");
        }
        String location = MCApiRuntime.createResourceLocation(context.ownerModId(), path).toString();
        state.textures.put(name, location);
        return new MLuaTextureInstance(name, location).toLua(context.registration());
    }

    @MLuaDescription("Returns a live texture handle by identifier.")
    public LuaTable GetTexture(String name) {
        String location = state.textures.get(name);
        return location == null ? null : new MLuaTextureInstance(name, location).toLua(context.registration());
    }

    @MLuaDescription("Removes a texture registered by the calling mod.")
    public boolean RemoveTexture(String name) {
        return state.textures.remove(name) != null;
    }

    @MLuaDescription("Returns whether a texture identifier is registered.")
    public boolean HasTexture(String name) {
        return state.textures.containsKey(name);
    }

    @MLuaDescription("Returns texture identifiers registered by the calling mod.")
    public LuaTable GetRegisteredTextures() {
        LuaTable result = new LuaTable();
        for (Map.Entry<String, String> entry : state.textures.entrySet()) {
            result.set(entry.getKey(), new MLuaTextureInstance(entry.getKey(), entry.getValue()).toLua(context.registration()));
        }
        return result;
    }

    @MLuaDescription("Removes all textures registered by the calling mod.")
    public void ClearTextures() {
        state.textures.clear();
    }

    private static final class TextureState {
        private final Map<String, String> textures = new ConcurrentHashMap<>();
    }
}
