package dev.mlua.runtime.client;

import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectileType;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "mlua",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public final class MLuaClientModels {
   private static final AtomicBoolean MISSING_LOADER_WARNING_LOGGED = new AtomicBoolean();

   private MLuaClientModels() {
   }

   /** NeoForge registers entity renderers through RegisterRenderersEvent. */
   public static void registerEntityRenderers() {
   }

   @SubscribeEvent
   @SuppressWarnings({"rawtypes", "unchecked"})
   public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
      if (!LoaderApi.isInstalled()) {
         if (MISSING_LOADER_WARNING_LOGGED.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.error("[MLua] Skipping scripted entity renderer registration because the NeoForge loader provider was not installed before renderer registration.");
         }
         return;
      }

      for (Map.Entry<String, RegistryHandle<?>> entry : MLuaLoaderRegistry.getRegisteredHandles(RegistryKind.ENTITY).entrySet()) {
         EntityType<?> type = (EntityType)((RegistryHandle)entry.getValue()).get();
         if (type != null) {
            String key = entry.getKey();
            event.registerEntityRenderer((EntityType) type, proxyRendererProvider(context -> new MLuaScriptedEntityRenderer(context, key)));
         }
      }

      EntityType<dev.mlua.minecraft.scripted.MLuaScriptedProjectile> projType = MLuaScriptedProjectileType.get();
      if (projType != null) {
         event.registerEntityRenderer((EntityType) projType, proxyRendererProvider(MLuaProjectileRenderer::new));
      }
   }

   @SuppressWarnings("unchecked")
   private static <T extends net.minecraft.world.entity.Entity> EntityRendererProvider<T> proxyRendererProvider(
      Function<EntityRendererProvider.Context, net.minecraft.client.renderer.entity.EntityRenderer<? super T>> factory
   ) {
      return (EntityRendererProvider<T>) Proxy.newProxyInstance(
         MLuaClientModels.class.getClassLoader(),
         new Class<?>[]{EntityRendererProvider.class},
         (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
               return method.invoke(proxy, args);
            }
            EntityRendererProvider.Context context = (EntityRendererProvider.Context) args[0];
            return factory.apply(context);
         }
      );
   }
}
