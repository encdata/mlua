package dev.mlua.runtime.client;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers render-state renderers for MLua entities on the physical client. */
@EventBusSubscriber(modid = "mlua", value = Dist.CLIENT)
public final class MLuaClientModels {
    private static final AtomicBoolean MISSING_LOADER_WARNING_LOGGED = new AtomicBoolean();

    private MLuaClientModels() {}

    /** NeoForge registers entity renderers through RegisterRenderersEvent. */
    public static void registerEntityRenderers() {}

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!LoaderApi.isInstalled()) {
            if (MISSING_LOADER_WARNING_LOGGED.compareAndSet(false, true)) {
                MLuaLogger.LOGGER.error("[MLua] Skipping scripted entity renderer registration because the NeoForge loader provider was not installed before renderer registration.");
            }
            return;
        }

        for (Map.Entry<String, RegistryHandle<?>> entry :
                MLuaLoaderRegistry.getRegisteredHandles(RegistryKind.ENTITY).entrySet()) {
            EntityType<?> type = (EntityType<?>) entry.getValue().get();
            if (type == null) continue;
            String key = entry.getKey();
            event.registerEntityRenderer((EntityType) type,
                (EntityRendererProvider) context -> new MLuaScriptedEntityRenderer(context, key));
        }

        EntityType<MLuaScriptedProjectile> projectileType = MLuaScriptedProjectileType.get();
        if (projectileType != null) {
            event.registerEntityRenderer((EntityType) projectileType,
                (EntityRendererProvider) MLuaProjectileRenderer::new);
        }
    }
}
