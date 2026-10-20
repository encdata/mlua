package dev.mlua.modules.registry.service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import dev.mlua.api.MLuaLuaErrors;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import net.minecraft.resources.ResourceLocation;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.binding.MLuaContainerType;
import dev.mlua.minecraft.binding.MLuaRegistryDefinition;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.minecraft.scripted.MLuaBehaviorRegistry;
import dev.mlua.modules.combat.MLuaProjectileDefinition;
import dev.mlua.runtime.config.MLuaConfigRegistry;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaCreativeTabFactory;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;
import dev.mlua.runtime.registry.MLuaItemFactory;

public final class MLuaRegistryService extends MLuaServiceBase {
   final RegistryEndpointCatalog registryEndpoints = new RegistryEndpointCatalog();
   final Set<String> dynamicCategoryEndpoints = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
      this.generatedResources = new MLuaRegistryGeneratedResources(context, this::registryId);
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

   static String requiredLuaString(Varargs args, int index, String method, String parameter) {
      LuaValue value = args.arg(index);
      if (value.type() != LuaValue.TSTRING || value.tojstring().isBlank()) {
         throw new LuaError("[MLua] " + method + " expects a non-empty string for " + parameter + ".");
      }
      return value.tojstring();
   }

   static LuaError registryValidationError(IllegalArgumentException invalid) {
      return MLuaLuaErrors.javaFailure("Registry definition", invalid);
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
      LuaValue query = target.get(method);
      if (!query.isfunction()) {
         throw new LuaError("[MLua] Registry category '" + category + "' does not support " + method);
      }
      return query.invoke(LuaValue.varargsOf(new LuaValue[]{target, argument}));
   }

   static void describeRegistryMethod(LuaTable target, String method, String description) {
      LuaValue metadata = target.rawget("__mlua_doc_methods");
      if (!metadata.istable() || !target.get(method).isfunction()) return;
      metadata.checktable().rawset(method, LuaValue.valueOf(description));
   }

   static void installEntryLookupApi(LuaTable target, String category) {
      LuaValue get = target.get("Get");
      if (!target.get("GetEntry").isfunction() && get.isfunction()) {
         target.set("GetEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
               String id = args.checkjstring(2);
               return get.invoke(LuaValue.varargsOf(new LuaValue[]{target, LuaValue.valueOf(id)}));
            }
         });
      }
      LuaValue getIds = target.get("GetIds");
      LuaValue getEntry = target.get("GetEntry");
      if (target.get("GetEntries").isfunction() || !getIds.isfunction() || !getEntry.isfunction()) return;
      target.set("GetEntries", new VarArgFunction() {
         @Override public Varargs invoke(Varargs args) {
            LuaValue idsValue = getIds.invoke(LuaValue.varargsOf(new LuaValue[]{target, args.arg(2)})).arg1();
            if (!idsValue.istable()) {
               throw new LuaError("[MLua] Registry." + category + ":GetIds must return an array of identifiers");
            }
            LuaTable ids = idsValue.checktable();
            LuaTable entries = new LuaTable();
            int entryIndex = 1;
            for (int index = 1; index <= ids.length(); index++) {
               LuaValue entry = getEntry.invoke(LuaValue.varargsOf(new LuaValue[]{target, ids.get(index)})).arg1();
               if (!entry.isnil()) entries.set(entryIndex++, entry);
            }
            return entries;
         }
      });
   }

   LuaValue containerType(String id) {
      MLuaBehaviorRegistry.ContainerDefinition definition = MLuaBehaviorRegistry.getContainerType(id);
      if (definition == null) return LuaValue.NIL;
      return MLuaContainerType.create(this.context.mlua().registration(), id, definition);
   }

   Object projectileRuntimeOwner() {
      return this.context.mlua().registration() instanceof dev.mlua.core.MLuaRuntime runtime
         ? runtime.session() : null;
   }

   LuaValue projectileType(String id) {
      ResourceLocation location = MLuaResourceLocations.tryParseOrNull(id);
      if (location == null) return LuaValue.NIL;
      Object runtimeOwner = this.projectileRuntimeOwner();
      String namespace = MLuaResourceLocations.namespace(location);
      String name = MLuaResourceLocations.path(location);
      if (MLuaProjectileDefinition.get(runtimeOwner, namespace, name) == null) return LuaValue.NIL;
      return MLuaProjectileDefinition.createTypeLuaTable(runtimeOwner, namespace, name,
         this.context.mlua().registration());
   }

   LuaValue registryEntry(RegistryKind kind, String owner, String name) {
      return MLuaRegistryEntry.create(this.context.mlua().registration(), kind, owner + ":" + name);
   }

   LuaValue registryEntryFor(RegistryEndpoint endpoint, String id, LuaTable registry) {
      if (endpoint.registryKind() != null) {
         if (!MLuaRegistryEntry.isKnown(endpoint.registryKind(), id)) return LuaValue.NIL;
         return MLuaRegistryEntry.create(this.context.mlua().registration(), endpoint.registryKind(), id);
      }
      if (this.dynamicRegistry.category(endpoint.table()) != null) {
         LuaValue entry = MLuaRegistryDefinition.create(
            this.context.mlua().registration(), this.dynamicRegistry, endpoint.table(), id);
         return entry == null ? LuaValue.NIL : entry;
      }
      LuaTable target = registry.get(endpoint.table()).checktable();
      LuaValue getter = target.get("GetEntry");
      if (!getter.isfunction()) getter = target.get("Get");
      if (!getter.isfunction()) return LuaValue.NIL;
      return getter.invoke(LuaValue.varargsOf(new LuaValue[]{target, LuaValue.valueOf(id)})).arg1();
   }

   void installDynamicCategory(LuaTable registry, MLuaDynamicRegistryCatalog.Category category) {
      this.dynamicCategoryInstaller.install(registry, category);
   }

   void installDynamicCategoryRegistration(LuaTable registry) {
      this.dynamicCategoryInstaller.installCategoryRegistration(registry);
   }

   static String registryCategory(String value) {
      String candidate = value == null ? "" : value.trim();
      if (candidate.length() > MLuaDynamicRegistryCatalog.MAX_CATEGORY_NAME_LENGTH) {
         throw new LuaError("[MLua] Registry category names cannot exceed "
            + MLuaDynamicRegistryCatalog.MAX_CATEGORY_NAME_LENGTH + " characters");
      }
      ResourceLocation id = candidate.contains(":")
         ? MLuaResourceLocations.tryParseOrNull(candidate) : null;
      if (id == null || !id.toString().equals(candidate)) {
         throw new LuaError("[MLua] Registry category must be a valid lowercase namespaced identifier, such as 'addon:weapons'");
      }
      return id.toString();
   }

   static String dynamicContentTypeDomain(String owner, String category) {
      String encodedCategory = HexFormat.of().formatHex(category.getBytes(StandardCharsets.UTF_8));
      return "addon." + owner + ".category." + encodedCategory;
   }

   static void documentRegistryTable(LuaTable target, String category) {
      target.rawset("__mlua_doc_description", LuaValue.valueOf(
         "Registry".equals(category)
            ? "The central registry service for built-in Minecraft content and addon-owned categories."
            : "Registry." + category + " provides the registration and lookup operations available for this content category."));
      MLuaServiceMetadata.attachMethodDescriptions(target, RegistryServiceMethods.methodDescriptions());
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

   LuaTable registeredIds(RegistryKind kind, String namespace) {
      LuaTable result = new LuaTable();
      int index = 1;
      for (String id : MLuaRegistryEntryQuery.addonRegisteredIds(kind, namespace)) result.set(index++, LuaValue.valueOf(id));
      return result;
   }

   LuaTable registeredEntries(RegistryKind kind, String namespace) {
      LuaTable result = new LuaTable();
      int index = 1;
      for (String id : registeredIdList(kind, namespace)) {
         result.set(index++, MLuaRegistryEntry.create(this.context.mlua().registration(), kind, id));
      }
      return result;
   }

   LuaTable registeredEntryMap(RegistryKind kind, String namespace) {
      LuaTable result = new LuaTable();
      for (String id : registeredIdList(kind, namespace)) {
         result.set(id, MLuaRegistryEntry.create(this.context.mlua().registration(), kind, id));
      }
      return result;
   }

   LuaTable dynamicRegistryEntryMap(String category, String namespace) {
      LuaTable result = new LuaTable();
      for (String id : this.dynamicRegistry.ids(category, namespace)) {
         LuaTable entry = MLuaRegistryDefinition.create(
            this.context.mlua().registration(), this.dynamicRegistry, category, id);
         if (entry != null) result.set(id, entry);
      }
      return result;
   }

   static List<String> registeredIdList(RegistryKind kind, String namespace) {
      return MLuaRegistryEntryQuery.registeredIdList(kind, namespace);
   }

   static String optionalNamespace(LuaValue value) {
      return MLuaRegistryLuaValues.optionalNamespace(value);
   }

   static LuaTable stringArray(List<String> values) {
      return MLuaRegistryLuaValues.stringArray(values);
   }

   static LuaTable enumNames(Enum<?>[] values) {
      return MLuaRegistryLuaValues.enumNames(values);
   }

   String registryId(String name) {
      String candidate = name.trim();
      if (!candidate.contains(":")) candidate = this.context.mlua().ownerModId() + ":" + candidate;
      ResourceLocation id = MLuaResourceLocations.tryParseOrNull(candidate);
      if (id == null) throw new LuaError("[MLua] Invalid registry identifier: " + name);
      return id.toString();
   }

   String ownedRegistryId(String name, String owner, String category) {
      String id = this.registryId(name);
      String namespace = MLuaResourceLocations.namespace(MLuaResourceLocations.parse(id));
      if (!owner.equals(namespace)) {
         throw new LuaError("[MLua] Registry." + category + ":Register can only add entries in the calling addon's namespace '"
            + owner + "'; got '" + id + "'");
      }
      return id;
   }

   RegistryEndpoint resolveEndpoint(String category, String purpose, LuaTable registry) {
      String key = category.trim().toLowerCase(java.util.Locale.ROOT);
      MLuaDynamicRegistryCatalog.Category dynamicCategory = this.dynamicRegistry.category(key);
      RegistryEndpoint endpoint;
      if (dynamicCategory != null) {
         endpoint = new RegistryEndpoint(dynamicCategory.name(), "Register", dynamicCategory.domain(),
            dynamicCategory.name(), null, 2, List.of());
         if (!endpoint.equals(this.registryEndpoints.get(key))) {
            this.registryEndpoints.remove(key);
            registry.set(dynamicCategory.name(), LuaValue.NIL);
            this.installDynamicCategory(registry, dynamicCategory);
         }
         endpoint = this.registryEndpoints.get(key);
      } else {
         endpoint = this.registryEndpoints.builtIn(key);
         RegistryEndpoint active = this.registryEndpoints.get(key);
         if (endpoint == null) {
            RegistryEndpoint stale = this.registryEndpoints.remove(key);
            if (stale != null) registry.set(stale.table(), LuaValue.NIL);
            this.dynamicCategoryEndpoints.remove(key);
         } else if (active != endpoint) {
            this.registryEndpoints.putIfAbsent(key, endpoint);
         }
      }
      if (endpoint == null) throw new LuaError("[MLua] Unknown or unsupported Registry " + purpose + " '" + category + "'");
      return endpoint;
   }

   void refreshDynamicCategoryEndpoints(LuaTable registry) {
      for (String key : List.copyOf(this.dynamicCategoryEndpoints)) {
         if (this.dynamicRegistry.category(key) != null) continue;
         RegistryEndpoint stale = this.registryEndpoints.remove(key);
         this.dynamicCategoryEndpoints.remove(key);
         if (stale != null) registry.set(stale.table(), LuaValue.NIL);
      }
   }

   void ensureRegistryPhase() {
      this.requirePhase(RegistrationPhase.REGISTRY,
         "Native content and registry-phase data require a .registry.lua script before Minecraft registries freeze. Current script phase: "
            + this.context.mlua().phase().name() + ".");
   }

   String registerTag(String kind, String name, LuaValue definition) {
      return this.tagOperations.register(kind, name, definition);
   }

   void registerMiningProperties(String owner, String name, MLuaRegistryDefinitions.MiningProperties properties) {
      this.tagOperations.registerMiningProperties(owner, name, properties);
   }

   String appendTagValues(String kind, String name, LuaValue valuesValue) {
      return this.tagOperations.append(kind, name, valuesValue);
   }

   String qualifyOwnBlock(String blockName) {
      return blockName.contains(":") ? blockName : this.context.mlua().ownerModId() + ":" + blockName;
   }

   static LuaTable typeCatalog(Map<String, String> types) {
      LuaTable result = new LuaTable();
      types.forEach((name, description) -> result.set(name, description));
      return result;
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
