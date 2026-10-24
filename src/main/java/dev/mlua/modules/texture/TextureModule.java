package dev.mlua.modules.texture;

import dev.mlua.modules.texture.service.MLuaTextureService;
import dev.mlua.common.enums.Side;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
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
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        registration.registerClass("TextureInstance", registration.requireInstanceBaseClassName(),
            "A registered texture handle with its identifier and resource path.");
        String[][] properties = {
            {"Name", "GetName", "string", "This texture's registered name."},
            {"Path", "GetPath", "string", "This texture's full resource identifier."},
            {"Namespace", "GetNamespace", "string", "The mod namespace that owns this texture."},
            {"TexturePath", "GetTexturePath", "string", "The texture path within its namespace."}
        };
        for (String[] property : properties) {
            MLuaClassMethods.registerInstanceMethod(registration, "TextureInstance", property[1],
                "Returns " + property[3].substring(0, 1).toLowerCase() + property[3].substring(1));
            MLuaClassMethods.registerInstanceProperty(registration, "TextureInstance", property[0], property[1], null,
                property[2], "client:Client", property[3]);
        }
    }

    @Override
    public void clearOwnedState(MLuaRegistration runtime, Object runtimeSession, Side side, String ownerModId) {
        if (runtimeSession instanceof dev.mlua.core.MLuaRuntimeSession session) {
            session.removeSharedServiceState(side, "TextureService", "textures:" + ownerModId);
        }
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder texture = ServiceEntry.builder("TextureService", MLuaTextureService::new)
            .reflective(MLuaTextureService.class);
        return List.of(texture.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("TextureService", "MLuaTextureService",
                "Service for registering and managing textures.",
                "services/texture-service.html"));
        docs.register(MLuaDocBuilder.Registration.clazz("TextureInstance", "MLuaTextureInstance",
                "Represents a registered texture.",
                "classes/texture-instance.html", "Instance"));
    }
}
