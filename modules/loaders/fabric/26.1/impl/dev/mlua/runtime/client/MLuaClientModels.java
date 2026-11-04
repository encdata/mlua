package dev.mlua.runtime.client;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.world.entity.EntityType;

/** Registers render-state renderers for MLua entities on the client. */
public final class MLuaClientModels {
    private MLuaClientModels() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void registerEntityRenderers() {
        for (Map.Entry<String, RegistryHandle<?>> entry :
                MLuaLoaderRegistry.getRegisteredHandles(RegistryKind.ENTITY).entrySet()) {
            EntityType<?> type = (EntityType<?>) entry.getValue().get();
            if (type == null) continue;
            String key = entry.getKey();
            EntityRenderers.register((EntityType) type,
                context -> new MLuaScriptedEntityRenderer(context, key));
        }

        EntityType<MLuaScriptedProjectile> projectileType = MLuaScriptedProjectileType.get();
        if (projectileType != null) {
            EntityRenderers.register(projectileType, MLuaProjectileRenderer::new);
        }
    }
}
