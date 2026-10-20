package dev.mlua.runtime.registry;

import dev.mlua.runtime.client.MLuaVisualBlockEntity;
import dev.mlua.minecraft.scripted.MLuaScriptedBlock;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class MLuaBlockFactory {
   private static final MLuaRegisteredTypeCatalog<TypeProvider> REGISTERED_TYPES = new MLuaRegisteredTypeCatalog<>();
   private static final MLuaBlockProperties BLOCK_PROPERTIES = new MLuaBlockProperties();

   /** Registers a Java block implementation selected by {@code type = "typeName"} in Lua. */
   public static void registerType(String name, String description, TypeProvider provider) {
      registerType("mlua", name, description, provider);
   }

   /** Registers an addon-owned Java block type so it can be removed when that addon unloads. */
   public static void registerType(String owner, String name, String description, TypeProvider provider) {
      registerTypeHandle(owner, name, description, provider);
   }

   /** Registers a type and returns an identity-checked handle for deterministic addon cleanup. */
   public static dev.mlua.api.MLuaApi.Registration registerTypeHandle(
         String owner, String name, String description, TypeProvider provider) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      if ("default".equals(key)) throw new IllegalArgumentException("[MLua] Block type 'default' is reserved by MLua");
      if (provider == null) throw new IllegalArgumentException("[MLua] Block type provider is required");
      REGISTERED_TYPES.register(owner, key, description, provider, "Block");
      return () -> REGISTERED_TYPES.remove(owner, key, "Block", provider);
   }

   /** Removes a block type only when it is still owned by the supplied addon. */
   public static boolean unregisterType(String owner, String name) {
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      if ("default".equals(key)) return false;
      return REGISTERED_TYPES.remove(owner, key, "Block");
   }

   /** Removes all block types registered by one addon. */
   public static int unregisterTypes(String owner) {
      return REGISTERED_TYPES.removeOwner(owner);
   }

   public static Map<String, String> registeredTypes() {
      Map<String, String> descriptions = new java.util.TreeMap<>();
      descriptions.put("default", "A standard block with configurable Minecraft properties and scripted callbacks.");
      return REGISTERED_TYPES.descriptions(descriptions);
   }

   /** Values accepted by the {@code sound} block property on this Minecraft version. */
   public static java.util.List<String> soundNames() {
      return MLuaBlockProperties.soundNames();
   }

   /** Values accepted by the {@code mapColor} block property on this Minecraft version. */
   public static java.util.List<String> mapColorNames() {
      return MLuaBlockProperties.mapColorNames();
   }

   /** Validate the selected implementation during Lua registration. */
   public static void validateTypeDefinition(Map<String, Object> properties) {
      TypeProvider extension = registeredType(properties);
      validateType(properties, extension);
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType != null && !(rawType instanceof String)) {
         throw new IllegalArgumentException("[MLua] Block type must be a string");
      }
      MLuaBlockProperties.validateCopyDefinition(properties);
      MLuaBlockProperties.validateBlockProperties(properties);
      if (extension != null) extension.validateDefinition(MLuaDefinitionSnapshot.freeze(properties));
   }

   public Block create(Map<String, Object> properties, String registryName, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      TypeProvider extension = registeredType(properties);
      validateType(properties, extension);
      MLuaBlockProperties.validateCopyDefinition(properties);
      if (extension != null) {
         Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
            properties, extension::validateDefinition);
         Block block = extension.createNamed(definition,
            this.withRegistryId(this.makeProperties(properties), registryName), registryName, blockEntityType);
         if (block == null) throw new IllegalStateException("[MLua] Block type provider returned null for '" + registryName + "'");
         return block;
      }
      return new MLuaNamedBlock(this.withRegistryId(this.makeProperties(properties), registryName), registryName, blockEntityType);
   }

   public Block createScripted(Map<String, Object> properties, String behaviorKey, Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType) {
      TypeProvider extension = registeredType(properties);
      validateType(properties, extension);
      MLuaBlockProperties.validateCopyDefinition(properties);
      if (extension != null) {
         Map<String, Object> definition = MLuaDefinitionSnapshot.validateAndFreeze(
            properties, extension::validateDefinition);
         Block block = extension.createScripted(definition,
            this.withRegistryId(this.makeProperties(properties), behaviorKey), behaviorKey, blockEntityType);
         if (block == null) throw new IllegalStateException("[MLua] Block type provider returned null for scripted block '" + behaviorKey + "'");
         return block;
      }
      return MLuaScriptedBlock.create(this.withRegistryId(this.makeProperties(properties), behaviorKey), behaviorKey, blockEntityType);
   }

   private BlockBehaviour.Properties withRegistryId(BlockBehaviour.Properties properties, String registryName) {
      //--version 26.1
      properties.setId(ResourceKey.create(Registries.BLOCK, MLuaResourceLocations.parse(registryName)));
      //--endversion
      return properties;
   }

   private static TypeProvider registeredType(Map<String, Object> properties) {
      Object type = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (!(type instanceof String name)) return null;
      String key = MLuaRegisteredTypeCatalog.normalizeName(name, "Block");
      return "default".equals(key) ? null : REGISTERED_TYPES.get(key, "Block");
   }

   private static void validateType(Map<String, Object> properties, TypeProvider registered) {
      Object rawType = properties.containsKey("type") ? properties.get("type") : properties.get("Type");
      if (rawType instanceof String name && !name.isBlank() && !"default".equals(MLuaRegisteredTypeCatalog.normalizeName(name, "Block")) && registered == null) {
         throw new IllegalArgumentException("[MLua] Unknown block type: " + name);
      }
   }

   public interface TypeProvider {
      /**
       * Validates provider-specific Lua fields during registration and again before construction.
       * Keep this method side-effect-free; throw {@link IllegalArgumentException} with the invalid field
       * and expected value so the registry can report an actionable script error.
       */
      default void validateDefinition(Map<String, Object> definition) { }
      Block createNamed(Map<String, Object> definition, BlockBehaviour.Properties properties, String registryName,
                        Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
      Block createScripted(Map<String, Object> definition, BlockBehaviour.Properties properties, String behaviorKey,
                           Supplier<BlockEntityType<MLuaVisualBlockEntity>> blockEntityType);
   }

   public BlockBehaviour.Properties makeProperties(Map<String, Object> properties) {
      return BLOCK_PROPERTIES.makeProperties(properties);
   }

}
