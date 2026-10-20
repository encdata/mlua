package dev.mlua.api;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.api.MLuaLuaCoercions.Adapter;
import dev.mlua.api.MLuaLuaCoercions.AdapterRegistration;
import dev.mlua.api.registry.MLuaBlockTypeProvider;
import dev.mlua.api.registry.MLuaItemTypeProvider;
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
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

@MLuaApiStatus(since = "1.0.0")
public final class MLuaApi {
   private static volatile MLuaApiBridge bridge;
   private static final AtomicLong SCOPED_SERVICE_MODULE_IDS = new AtomicLong();
   private static final AtomicLong SCOPED_GLOBAL_MODULE_IDS = new AtomicLong();

   private MLuaApi() {
   }

   /** A closeable registration owned by the addon that created it. */
   @MLuaApiStatus(since = "0.0.5")
   public interface Registration extends AutoCloseable {
      @Override void close();
   }

   /** A namespaced event exposed to Lua and fired by its Java addon owner. */
   @MLuaApiStatus(since = "0.0.5")
   public interface EventSignal extends Registration {
      /** Fully-qualified key available as {@code Events[name()]}. */
      String name();

      /** Dispatches a LuaJ payload synchronously on the calling Java thread. */
      void fire(Varargs payload);
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

   public static <T extends MLuaReflectiveService> ServiceBuilder reflectiveService(
      String name, Class<T> serviceType, Function<MLuaService.Context, T> factory) {
      return reflectiveService(name, null, serviceType, factory);
   }

   public static <T extends MLuaReflectiveService> ServiceBuilder reflectiveService(
      String name, String className, Class<T> serviceType, Function<MLuaService.Context, T> factory) {
      Objects.requireNonNull(serviceType, "serviceType");
      Objects.requireNonNull(factory, "factory");
      ServiceBuilder builder = new ServiceBuilder(name, className, context -> factory.apply(context));
      return builder.reflective(serviceType);
   }

   public static final class ServiceBuilder {
      private final MLuaModule.ServiceEntry.Builder declaration;
      private boolean explicitClassName;
      private Side side = Side.BOTH;
      private boolean registered;

      private ServiceBuilder(String name, String className,
                             Function<MLuaService.Context, MLuaService> factory) {
         this.declaration = MLuaModule.ServiceEntry.builder(Objects.requireNonNull(name, "name"),
            Objects.requireNonNull(factory, "factory"));
         if (className != null) {
            this.declaration.className(className);
            this.explicitClassName = true;
         }
      }

      public ServiceBuilder className(String className) {
         ensureOpen();
         Objects.requireNonNull(className, "className");
         if (className.isBlank()) throw new IllegalArgumentException("Service class name must be non-empty");
         this.declaration.className(className);
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
         this.declaration.description(description);
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

      /** Registers this service as a module and returns a handle that removes its owned state. */
      public Registration registerScoped() {
         ensureOpen();
         MLuaModule.ServiceEntry entry = this.declaration.build();
         String moduleId = "mlua_api_service_" + entry.name().replaceAll("[^A-Za-z0-9_.-]", "_")
            + "_" + SCOPED_SERVICE_MODULE_IDS.incrementAndGet();
         MLuaModule module = new MLuaModule() {
            @Override public String id() { return moduleId; }
            @Override public Side side() { return ServiceBuilder.this.side; }
            @Override public java.util.List<ServiceEntry> services() { return java.util.List.of(entry); }
         };
         Registration registration = MLuaApi.registerModuleScoped(module);
         this.registered = true;
         return registration;
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

   /** Registers an addon module whose services, Lua classes and event callbacks can be closed together. */
   public static Registration registerModuleScoped(MLuaModule module) {
      return requireBridge().registerModuleScoped(Objects.requireNonNull(module, "module"));
   }

   public static void registerGlobal(String modId, String name, LuaValue value) {
      registerGlobal(modId, name, value, Side.BOTH, null);
   }

   public static void registerGlobal(String modId, String name, LuaValue value, Side side) {
      registerGlobal(modId, name, value, side, null);
   }

   public static void registerGlobal(String modId, String name, LuaValue value, Side side, ScriptType type) {
      registerModule(globalModule(modId, name, value, side, type));
   }

   /** Registers an addon global whose Lua value is removed when the returned owner handle is closed. */
   @MLuaApiStatus(since = "0.0.5")
   public static Registration registerGlobalScoped(String modId, String name, LuaValue value) {
      return registerGlobalScoped(modId, name, value, Side.BOTH, null);
   }

   /** Registers an addon global for the selected runtime side. */
   @MLuaApiStatus(since = "0.0.5")
   public static Registration registerGlobalScoped(String modId, String name, LuaValue value, Side side) {
      return registerGlobalScoped(modId, name, value, side, null);
   }

   /** Registers an addon global for selected sides and script types with a closeable owner handle. */
   @MLuaApiStatus(since = "0.0.5")
   public static Registration registerGlobalScoped(String modId, String name, LuaValue value,
                                                    Side side, ScriptType type) {
      return registerModuleScoped(globalModule(modId, name, value, side, type));
   }

   private static MLuaModule globalModule(String modId, String name, LuaValue value, Side side, ScriptType type) {
       Objects.requireNonNull(modId, "modId");
       Objects.requireNonNull(name, "name");
       Objects.requireNonNull(value, "value");
       Objects.requireNonNull(side, "side");
       if (modId.isBlank() || name.isBlank()) {
          throw new IllegalArgumentException("Global mod id and name must be non-empty");
       }
       String moduleId = "mlua_api_global_" + modId + "_" + name + "_"
          + SCOPED_GLOBAL_MODULE_IDS.incrementAndGet();
       return new MLuaModule() {
          @Override
          public String id() {
             return moduleId;
          }

         @Override
         public void contributeGlobals(Globals globals, MLuaContextView context) {
            if (side != Side.BOTH && context.side() != side) {
               return;
            }
            if (type != null && !context.isScriptType(type)) {
               return;
            }
            LuaValue existing = globals.get(name);
            if (!existing.isnil() && !existing.raweq(value)) {
               throw new IllegalStateException("Lua global '" + name
                  + "' is already defined. Choose a unique addon global name.");
            }
            globals.set(name, value);
         }
      };
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
      Objects.requireNonNull(side, "side");
      MLuaModule.ServiceEntry declaration = new MLuaModule.ServiceEntry(
         name, factory, description, functionDescriptions);
      requireBridge().registerService(side, null, declaration);
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
      Objects.requireNonNull(side, "side");
      MLuaModule.ServiceEntry declaration = new MLuaModule.ServiceEntry(
         name, className, factory, description, functionDescriptions);
      requireBridge().registerService(side, className, declaration);
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

   /**
    * Adds a Java-backed item kind to the Lua Registry.Items builder catalog.
    * Register during mod initialization, before registry scripts are evaluated.
    */
   public static Registration registerItemType(String owner, String name, String description,
                                                MLuaItemTypeProvider provider) {
      return requireBridge().registerItemType(owner, name, description, Objects.requireNonNull(provider, "provider"));
   }

   /**
    * Adds a Java-backed block kind to the Lua Registry.Blocks builder catalog.
    * Register during mod initialization, before registry scripts are evaluated.
    */
   public static Registration registerBlockType(String owner, String name, String description,
                                                 MLuaBlockTypeProvider provider) {
      return requireBridge().registerBlockType(owner, name, description, Objects.requireNonNull(provider, "provider"));
   }

   /**
    * Registers an addon-owned signal under {@code owner:name} (or validates an already-qualified key).
    * Lua scripts subscribe with {@code Events[signal.name()]:Connect(...)}. Closing the handle removes
    * only this signal and disconnects its listeners. Firing executes callbacks synchronously on the
    * calling thread; callers should fire from the relevant Minecraft event thread.
    */
   public static EventSignal registerEventSignal(String owner, String name, String description) {
      return requireBridge().registerEventSignal(owner, name, description);
   }
}
