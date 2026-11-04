package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import dev.mlua.api.MLuaLuaErrors;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.modules.combat.MLuaProjectileDefinition;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaCreativeTabFactory;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import dev.mlua.runtime.registry.MLuaItemFactory;

public final class MLuaRegistryService extends MLuaServiceBase {
   final MLuaRegistryEndpointResolver endpointResolver;
   final MLuaRegistryEntryCatalog entries;
   final MLuaRegistryIdentity identifiers;
   final MLuaDynamicRegistryCatalog dynamicRegistry = this.sharedState(
      "dynamic-categories", MLuaDynamicRegistryCatalog.class, MLuaDynamicRegistryCatalog::new);
   final MLuaItemFactory itemFactory = new MLuaItemFactory();
   final MLuaBlockFactory blockFactory = new MLuaBlockFactory();
   final MLuaCreativeTabFactory tabFactory = new MLuaCreativeTabFactory();
   private final MLuaRegistryGeneratedResources generatedResources;
   private final MLuaRegistryTagOperations tagOperations;
   private final MLuaRegistryVisualOperations visualOperations;
   private final MLuaRegistryBehaviorOperations behaviorOperations;
   private final MLuaRegistryContentTypes contentTypes;
   private final MLuaRegistryDynamicCategoryInstaller dynamicCategoryInstaller =
      new MLuaRegistryDynamicCategoryInstaller(this);

   public MLuaRegistryService(MLuaService.Context context) {
      super(context);
      this.endpointResolver = new MLuaRegistryEndpointResolver(this);
      this.entries = new MLuaRegistryEntryCatalog(this);
      this.identifiers = new MLuaRegistryIdentity(context);
      this.generatedResources = new MLuaRegistryGeneratedResources(context, this.identifiers::registryId);
      this.tagOperations = new MLuaRegistryTagOperations(this);
      this.visualOperations = new MLuaRegistryVisualOperations(this);
      this.behaviorOperations = new MLuaRegistryBehaviorOperations(this);
      this.contentTypes = new MLuaRegistryContentTypes(this);
   }

   final MLuaService.Context apiContext() {
      return context;
   }

   public static Map<String, String> serviceMethodDescriptions() {
      return RegistryServiceMethods.serviceMethodDescriptions();
   }

   void registerConfig(String scope, String name, LuaTable values) {
      String owner = this.context.mlua().ownerModId();
      MLuaConfigRegistry.ConfigSectionHandle handle = MLuaConfigRegistry.get().register(owner, scope, name, values);
      Runnable rollback = () -> MLuaConfigRegistry.get().unregister(handle);
      this.context.mlua().registration().trackRollback(rollback);
   }

   void addWorldGenerationRegistration(LuaTable worldGeneration, String name, String resourceType) {
      VarArgFunction register = new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            MLuaRegistryService.this.ensureRegistryPhase();
            String owner = MLuaRegistryService.this.context.mlua().ownerModId();
            String id = args.checkjstring(2);
            LuaTable definition = MLuaRegistryService.this.context.mlua().registration().resolveContentType(
               "worldgen", owner, args.checktable(3));
            MLuaRegistryService.this.registerWorldGenerationResource(owner, resourceType, id, definition);
            return LuaValue.valueOf(generatedAssetId(owner, id));
         }
      };
      worldGeneration.set("Register" + name, register);
      worldGeneration.set("register" + name, register);
      worldGeneration.set("Get" + name, this.generatedDataGetter(resourceType));
   }

   void registerWorldGenerationResource(String owner, String resourceType, String id, LuaTable definition) {
      JsonObject json = MLuaLuaJson.objectArg(definition, "world generation resource");
      try {
         this.registerGeneratedDataResource(owner, resourceType, id, json);
      } catch (IllegalArgumentException | IllegalStateException error) {
         throw MLuaLuaErrors.javaFailure("World generation resource registration", error);
      }
   }

   void registerGeneratedDataResource(String owner, String resourceType, String id, JsonObject definition) {
      this.generatedResources.registerData(owner, resourceType, id, definition);
   }

   private VarArgFunction generatedDataGetter(String resourceType) {
      return new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            return MLuaRegistryService.this.generatedResources.dataResource(resourceType, args.checkjstring(2));
         }
      };
   }

   LuaValue generatedDataResource(String resourceType, String id) {
      return this.generatedResources.dataResource(resourceType, id);
   }

   List<String> generatedDataIds(String resourceType, String namespace) {
      return this.generatedResources.dataIds(resourceType, namespace);
   }

   LuaTable generatedDataEntryList(String resourceType, String namespace) {
      return this.generatedResources.dataEntries(resourceType, namespace, false);
   }

   LuaTable generatedDataEntryMap(String resourceType, String namespace) {
      return this.generatedResources.dataEntries(resourceType, namespace, true);
   }

   static boolean isGeneratedDataEndpoint(String name) {
      return "DataPack".equals(name) || "WorldGeneration".equals(name);
   }

   void installGeneratedAssetLookupApi(LuaTable target, GeneratedAssetKind kind) {
      this.generatedResources.installAssetLookupApi(target, kind);
   }

   void installGeneratedDataLookupApi(LuaTable target) {
      this.generatedResources.installDataLookupApi(target);
   }

   void registerGeneratedAssetResource(String owner, String path, JsonObject definition) {
      this.generatedResources.registerAssetResource(owner, path, definition);
   }

   void registerGeneratedAsset(GeneratedAssetKind kind, String owner, String name, JsonObject definition) {
      this.generatedResources.registerAsset(kind, owner, name, definition);
   }

   static String generatedAssetId(String owner, String name) {
      return owner + ":" + name;
   }

   public String name() {
      return "Registry";
   }

   public static void clearOwnedCategories(Object runtimeSession, Side side, String ownerModId) {
      if (!(runtimeSession instanceof MLuaRuntimeSession session) || ownerModId == null || ownerModId.isBlank()) return;
      List<Side> sides = side == Side.BOTH ? List.of(Side.SERVER, Side.CLIENT) : List.of(side);
      for (Side currentSide : sides) {
         MLuaDynamicRegistryCatalog state = session.existingSharedServiceState(
            currentSide, "Registry", "dynamic-categories", MLuaDynamicRegistryCatalog.class);
         if (state == null) continue;
         for (MLuaDynamicRegistryCatalog.Category category : state.categories()) {
            state.unregisterOwner(category.name(), ownerModId);
         }
      }
   }

   public LuaTable toLua() {
      return MLuaRegistryApi.build(this);
   }

   LuaTable registryTable(String name) {
      LuaTable table = this.context.mlua().registration().attachClassInstance(new LuaTable(), "RegistryCategory", null);
      table.set("__side", "Registry");
      table.set("__registry_category_name", LuaValue.valueOf(name));
      return table;
   }

   static Varargs invokeCategoryQuery(LuaTable target, String category, String method, LuaValue argument) {
      return MLuaRegistryTableSupport.invokeCategoryQuery(target, category, method, argument);
   }

   static void describeRegistryMethod(LuaTable target, String method, String description) {
      MLuaRegistryTableSupport.describeRegistryMethod(target, method, description);
   }

   static void installEntryLookupApi(LuaTable target, String category) {
      MLuaRegistryTableSupport.installEntryLookupApi(target, category);
   }

   LuaValue containerType(String id) {
      dev.mlua.core.MLuaContext callbackContext =
         dev.mlua.core.engine.MLuaLuaBindings.boundContext(this.context.mlua().globals());
      MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getContainerType(id, callbackContext);
      if (definition == null) return LuaValue.NIL;
      return MLuaContainerType.create(this.context.mlua().registration(), id, definition);
   }

   Object projectileRuntimeOwner() {
      return this.context.mlua().registration() instanceof dev.mlua.core.MLuaRuntime runtime
         ? runtime.session() : null;
   }

   LuaValue projectileType(String id) {
      var location = MLuaResourceLocations.tryParseOrNull(id);
      if (location == null) return LuaValue.NIL;
      Object runtimeOwner = this.projectileRuntimeOwner();
      String namespace = MLuaResourceLocations.namespace(location);
      String name = MLuaResourceLocations.path(location);
      if (MLuaProjectileDefinition.get(runtimeOwner, namespace, name) == null) return LuaValue.NIL;
      return MLuaProjectileDefinition.createTypeLuaTable(runtimeOwner, namespace, name,
         this.context.mlua().registration());
   }

   void installDynamicCategory(LuaTable registry, MLuaDynamicRegistryCatalog.Category category) {
      this.dynamicCategoryInstaller.install(registry, category);
   }

   void installDynamicCategoryRegistration(LuaTable registry) {
      this.dynamicCategoryInstaller.installCategoryRegistration(registry);
   }

   static void documentRegistryTable(LuaTable target, String category) {
      MLuaRegistryTableSupport.documentRegistryTable(target, category);
   }

   void installContentTypeApi(LuaTable target, String domain,
                                      List<RegistryEndpoint.TypedDefinition> definitions) {
      this.contentTypes.install(target, domain, definitions);
   }

   void installRootContentTypeApi(LuaTable target) {
      this.contentTypes.installRootApi(target);
   }

   void wrapTypedDefinition(LuaTable target, String domain,
                            List<RegistryEndpoint.TypedDefinition> definitions) {
      this.contentTypes.wrapTypedDefinition(target, domain, definitions);
   }

   void installRegistryEntryApi(LuaTable target, RegistryKind kind) {
      MLuaRegistryEntryApi.install(target, kind, this);
   }

   void ensureRegistryPhase() {
      this.requirePhase(RegistrationPhase.REGISTRY,
         "Native content and registry-phase data require a .registry.lua script before Minecraft registries freeze. Current script phase: "
            + this.context.mlua().phase().name() + ".");
   }

   String registerTag(String kind, String name, LuaValue definition) {
      return this.tagOperations.register(kind, name, definition);
   }

   void registerMiningProperties(String owner, String name, MLuaRegistryMiningProperties properties) {
      this.tagOperations.registerMiningProperties(owner, name, properties);
   }

   String appendTagValues(String kind, String name, LuaValue valuesValue) {
      return this.tagOperations.append(kind, name, valuesValue);
   }

   void replaceBehaviors(String domain, String key, Map<String, LuaValue> requested) {
      this.behaviorOperations.replaceBehaviors(domain, key, requested);
   }

   void setEntityAIInterval(String key, int interval) {
      this.behaviorOperations.setEntityAIInterval(key, interval);
   }

   void replaceBlockContainer(dev.mlua.module.MLuaRegistration registration, String key,
                              MLuaBehaviorRegistry.ContainerDefinition requested) {
      this.behaviorOperations.replaceBlockContainer(registration, key, requested);
   }

   void registerContentClass(String domain, String key, String className) {
      this.behaviorOperations.registerContentClass(domain, key, className);
   }

   void updateVisualMetadata(RegistryKind kind, String owner, String name,
                             java.util.function.UnaryOperator<MLuaRegistryVisualOperations.Metadata> update) {
      this.visualOperations.update(kind, owner, name, update);
   }

   void registerEntityVisual(String owner, String name, String texture, String model, float shadowRadius) {
      this.visualOperations.registerEntity(owner, name, texture, model, shadowRadius);
   }

}
