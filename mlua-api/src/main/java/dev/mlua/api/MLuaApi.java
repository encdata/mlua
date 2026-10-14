package dev.mlua.api;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.api.MLuaLuaCoercions.Adapter;
import dev.mlua.api.MLuaLuaCoercions.AdapterRegistration;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.loaderapi.RegistryHandle;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;

@MLuaApiStatus(since = "1.0.0")
public final class MLuaApi {
   private static volatile MLuaApiBridge bridge;

   private MLuaApi() {
   }

   public static void install(MLuaApiBridge bridge) {
      MLuaApi.bridge = Objects.requireNonNull(bridge, "bridge");
   }

   public static ServiceBuilder service(String name, Function<MLuaService.Context, MLuaService> factory) {
      return new ServiceBuilder(name, null, factory);
   }

   public static ServiceBuilder service(String name, String className,
                                        Function<MLuaService.Context, MLuaService> factory) {
      return new ServiceBuilder(name, className, factory);
   }

   public static final class ServiceBuilder {
      private final String name;
      private final MLuaModule.ServiceEntry.Builder declaration;
      private boolean explicitClassName;
      private Side side = Side.BOTH;
      private boolean registered;

      private ServiceBuilder(String name, String className,
                             Function<MLuaService.Context, MLuaService> factory) {
         this.name = Objects.requireNonNull(name, "name");
         this.declaration = MLuaModule.ServiceEntry.builder(name, Objects.requireNonNull(factory, "factory"));
         if (className != null) {
            this.declaration.className(className);
            this.explicitClassName = true;
         }
      }

      public ServiceBuilder className(String className) {
         ensureOpen();
         this.declaration.className(Objects.requireNonNull(className, "className"));
         this.explicitClassName = true;
         return this;
      }

      public ServiceBuilder side(Side side) {
         ensureOpen();
         this.side = Objects.requireNonNull(side, "side");
         return this;
      }

      public ServiceBuilder description(String description) {
         ensureOpen();
         this.declaration.description(Objects.requireNonNull(description, "description"));
         return this;
      }

      public ServiceBuilder reflective(Class<? extends MLuaReflectiveService> serviceType) {
         ensureOpen();
         this.declaration.reflective(serviceType);
         return this;
      }

      public ServiceBuilder function(String name, String description) {
         ensureOpen();
         this.declaration.function(name, description);
         return this;
      }

      public void register() {
         ensureOpen();
         MLuaModule.ServiceEntry entry = this.declaration.build();
         if (!this.explicitClassName) {
            MLuaApi.registerService(entry.name(), this.side, entry.factory(), entry.description(), entry.functionDescriptions());
         } else {
            MLuaApi.registerService(entry.name(), entry.className(), this.side, entry.factory(),
               entry.description(), entry.functionDescriptions());
         }
         this.registered = true;
      }

      private void ensureOpen() {
         if (this.registered) throw new IllegalStateException("Service declaration has already been registered");
      }
   }

   public static boolean isAvailable() {
      return bridge != null;
   }

   public static String getApiVersion() {
      return MLuaApiPolicy.VERSION;
   }

   public static <T> AdapterRegistration registerLuaAdapter(Adapter<T> adapter) {
      return MLuaLuaCoercions.registerAdapter(adapter);
   }

   public static boolean supportsCapability(String capability) {
      if (bridge == null) return false;
      try {
         return MLuaApiPolicy.supports(capability, LoaderApi.platform().loaderName());
      } catch (IllegalStateException unavailable) {
         return false;
      }
   }

   public static void requireCapability(String capability) {
      if (!supportsCapability(capability)) {
         throw new UnsupportedOperationException("MLua capability is unavailable: " + capability);
      }
   }

   private static MLuaApiBridge requireBridge() {
      MLuaApiBridge current = bridge;
      if (current == null) {
         throw new IllegalStateException("MLua runtime bridge is not installed. Ensure the 'mlua' mod is loaded before calling MLuaApi.");
      }

      return current;
   }

   public static void registerScriptRoot(String modId, Path path) {
      requireBridge().registerScriptRoot(modId, path);
   }

   public static void registerModule(MLuaModule module) {
      requireBridge().registerModule(module);
   }

   public static void registerGlobal(String modId, String name, LuaValue value) {
      registerGlobal(modId, name, value, Side.BOTH, null);
   }

   public static void registerGlobal(String modId, String name, LuaValue value, Side side) {
      registerGlobal(modId, name, value, side, null);
   }

   public static void registerGlobal(String modId, String name, LuaValue value, Side side, ScriptType type) {
       Objects.requireNonNull(modId, "modId");
       Objects.requireNonNull(name, "name");
       Objects.requireNonNull(value, "value");
       Objects.requireNonNull(side, "side");
       if (modId.isBlank() || name.isBlank()) {
          throw new IllegalArgumentException("Global mod id and name must be non-empty");
       }
       registerModule(new MLuaModule() {
          @Override
          public String id() {
             return modId + "_global_" + name;
          }

         @Override
         public void contributeGlobals(Globals globals, MLuaContextView context) {
            if (side != Side.BOTH && context.side() != side) {
               return;
            }
            if (type != null && !context.isScriptType(type)) {
               return;
            }
            globals.set(name, value);
         }
      });
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, Function<MLuaService.Context, MLuaService> factory) {
      registerService(name, Side.BOTH, factory, null, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory) {
      registerService(name, side, factory, null, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, Function<MLuaService.Context, MLuaService> factory, String description) {
      registerService(name, factory, description, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                      String description) {
      registerService(name, side, factory, description, Map.of());
   }

   public static void registerService(String name, Function<MLuaService.Context, MLuaService> factory,
                                      String description, Map<String, String> functionDescriptions) {
      registerService(name, Side.BOTH, factory, description, functionDescriptions);
   }

   public static void registerService(String name, Side side, Function<MLuaService.Context, MLuaService> factory,
                                      String description, Map<String, String> functionDescriptions) {
      Map<String, String> descriptions = validateServiceRegistration(name, side, factory, description, functionDescriptions);
      requireBridge().registerService(name, side, factory, description, descriptions);
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, String className,
                                      Function<MLuaService.Context, MLuaService> factory) {
      registerService(name, className, Side.BOTH, factory, null, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, String className, Side side,
                                      Function<MLuaService.Context, MLuaService> factory) {
      registerService(name, className, side, factory, null, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, String className,
                                      Function<MLuaService.Context, MLuaService> factory, String description) {
      registerService(name, className, Side.BOTH, factory, description, Map.of());
   }

   @Deprecated(forRemoval = false)
   public static void registerService(String name, String className, Side side,
                                      Function<MLuaService.Context, MLuaService> factory, String description) {
      registerService(name, className, side, factory, description, Map.of());
   }

   public static void registerService(String name, String className,
                                      Function<MLuaService.Context, MLuaService> factory,
                                      String description, Map<String, String> functionDescriptions) {
      registerService(name, className, Side.BOTH, factory, description, functionDescriptions);
   }

   public static void registerService(String name, String className, Side side,
                                      Function<MLuaService.Context, MLuaService> factory,
                                      String description, Map<String, String> functionDescriptions) {
      Objects.requireNonNull(className, "className");
      if (className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
      Map<String, String> descriptions = validateServiceRegistration(name, side, factory, description, functionDescriptions);
      requireBridge().registerService(name, className, side, factory, description, descriptions);
   }

   private static Map<String, String> validateServiceRegistration(
      String name, Side side, Function<MLuaService.Context, MLuaService> factory,
      String description, Map<String, String> functionDescriptions) {
      Objects.requireNonNull(side, "side");
      return new MLuaModule.ServiceEntry(name, factory, description, functionDescriptions).functionDescriptions();
   }

   public static RegistryHandle<Item> registerItem(String modId, String name, Supplier<Item> supplier) {
      return requireBridge().registerItem(modId, name, supplier);
   }

   public static RegistryHandle<Block> registerBlock(String modId, String name, Supplier<Block> supplier) {
      return requireBridge().registerBlock(modId, name, supplier);
   }

   public static RegistryHandle<Item> registerBlockItem(String modId, String blockName) {
      return requireBridge().registerBlockItem(modId, blockName);
   }

   public static RegistryHandle<CreativeModeTab> registerCreativeTab(String modId, String name, Function<CreativeModeTab.Builder, CreativeModeTab.Builder> config) {
      return requireBridge().registerCreativeTab(modId, name, config);
   }

   public static Item getItem(String modId, String name) {
      return requireBridge().getItem(modId, name);
   }

   public static Block getBlock(String modId, String name) {
      return requireBridge().getBlock(modId, name);
   }

   public static BlockBehaviour.Properties blockPropertiesCopy(Block block) {
      return requireBridge().blockPropertiesCopy(block);
   }
}
