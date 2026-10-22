package dev.mlua.runtime.client;

import dev.mlua.runtime.assets.MLuaGeneratedAssetPack;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.Pack.Position;
//--version 1.21.1
import java.util.Optional;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackResources;
//--endversion
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
   modid = "mlua",
   bus = Bus.MOD
)
public final class MLuaPackFinder {
   private static final AtomicBoolean REGISTERED = new AtomicBoolean();

   private MLuaPackFinder() {
   }

   public static void registerResourcePack() {
      if (REGISTERED.compareAndSet(false, true)) {
         FMLJavaModLoadingContext.get().getModEventBus().addListener(MLuaPackFinder::onAddPackFinders);
      }
   }

   @SubscribeEvent
   public static void onAddPackFinders(AddPackFindersEvent event) {
      if (event.getPackType() == PackType.CLIENT_RESOURCES || event.getPackType() == PackType.SERVER_DATA) {
         MLuaGeneratedAssetPack.generate();
         if (MLuaGeneratedAssetPack.hasGeneratedContent()) {
            PackType packType = event.getPackType();
            RepositorySource source = (RepositorySource)Proxy.newProxyInstance(MLuaPackFinder.class.getClassLoader(), new Class[]{RepositorySource.class}, (proxy, method, args) -> {
               if (method.getDeclaringClass() == Object.class) {
                  Object var10000;
                  switch (method.getName()) {
                     case "hashCode":
                        var10000 = System.identityHashCode(proxy);
                        break;
                     case "equals":
                        var10000 = proxy == (args != null && args.length > 0 ? args[0] : null);
                        break;
                     case "toString":
                        var10000 = "MLuaGeneratedAssetRepositorySource";
                        break;
                     default:
                        var10000 = null;
                  }

                  return var10000;
               } else {
                  if (args != null && args.length == 1) {
                     Object patt1820$temp = args[0];
                     if (patt1820$temp instanceof Consumer) {
                        Consumer<?> rawConsumer = (Consumer)patt1820$temp;
                         //--version 1.21.1
                         Component title = Component.literal(packType == PackType.CLIENT_RESOURCES ? "MLua Generated Assets" : "MLua Generated Data");
                         PackLocationInfo location = new PackLocationInfo("mlua_generated_assets", title, PackSource.BUILT_IN, Optional.empty());
                         Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                            @Override
                            public PackResources openPrimary(PackLocationInfo info) {
                               return MLuaGeneratedAssetPack.createPack();
                            }

                            @Override
                            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                               return MLuaGeneratedAssetPack.createPack();
                            }
                         };
                         Pack pack = Pack.readMetaAndCreate(location, supplier, packType, new PackSelectionConfig(true, Position.TOP, false));
                         //--endversion
                         //--version 1.20.1
                         Pack pack = Pack.readMetaAndCreate("mlua_generated_assets", Component.literal(packType == PackType.CLIENT_RESOURCES ? "MLua Generated Assets" : "MLua Generated Data"), true, (name) -> {
                            return MLuaGeneratedAssetPack.createPack();
                         }, packType, Position.TOP, PackSource.BUILT_IN);
                         //--endversion
                        if (pack != null) {
                           ((Consumer<Object>)rawConsumer).accept(pack);
                        }
                     }
                  }

                  return null;
               }
            });
            event.addRepositorySource(source);
         }
      }
   }
}
