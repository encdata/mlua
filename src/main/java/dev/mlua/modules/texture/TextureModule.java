package dev.mlua.modules.texture;

import dev.mlua.modules.texture.Binding.MLuaTextureService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import java.util.List;

@dev.mlua.module.MLuaModuleSide(Side.CLIENT)
public class TextureModule implements MLuaModule {
    @Override
    public String id() { return "texture"; }

    @Override
    public Side side() { return Side.CLIENT; }

    @Override
    public int priority() { return 50; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder texture = ServiceEntry.builder("TextureService", MLuaTextureService::new)
            .description("Service for registering and managing textures.");
        texture.function("RegisterTexture", "Registers a texture from the calling mod's texture directory.")
            .function("GetTexture", "Returns a live texture handle by identifier.")
            .function("RemoveTexture", "Removes a texture registered by the calling mod.")
            .function("HasTexture", "Returns whether a texture identifier is registered.")
            .function("GetRegisteredTextures", "Returns texture identifiers registered by the calling mod.")
            .function("ClearTextures", "Removes all textures registered by the calling mod.");
        return List.of(texture.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("TextureService", "MLuaTextureService",
                "Service for registering and managing textures.",
                "services/texture-service.html");
        docs.clazz("TextureInstance", "MLuaTextureInstance",
                "Represents a registered texture.",
                "classes/texture-instance.html", "Instance");
    }
}
