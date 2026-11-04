package dev.mlua.runtime.client;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.world.entity.EntityType;
import java.util.Map;

public final class MLuaClientModels {
    private MLuaClientModels() {
    }

    public static void registerEntityRenderers() {
        for (Map.Entry<String, RegistryHandle<?>> entry : MLuaLoaderRegistry.getRegisteredHandles(RegistryKind.ENTITY).entrySet()) {
            EntityType<?> type = (EntityType<?>) entry.getValue().get();
            if (type != null) {
                String key = entry.getKey();
                EntityRendererRegistry.register((EntityType) type, context -> new MLuaScriptedEntityRenderer(context, key));
            }
        }

        EntityType<dev.mlua.minecraft.scripted.MLuaScriptedProjectile> projType = MLuaScriptedProjectileType.get();
        if (projType != null) {
            EntityRendererRegistry.register(projType, MLuaProjectileRenderer::new);
        }
    }
}
