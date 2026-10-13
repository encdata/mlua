package dev.mlua.runtime.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mlua.core.MLuaLogger;
import dev.mlua.loaderapi.GeneratedAssetKind;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.repository.PackSource;
import java.util.Optional;

public final class MLuaGeneratedAssetPack {
   private static final String PACK_ID = "mlua_generated_assets";
   private static final boolean LEGACY_DATA_DIRECTORIES = false;
   private static final PackLocationInfo PACK_INFO = new PackLocationInfo(PACK_ID, Component.literal("MLua Generated Assets"), PackSource.BUILT_IN, Optional.empty());
   private static volatile Map<String, byte[]> DATA = Map.of();

   private MLuaGeneratedAssetPack() {
   }

   public static void generate() {
      Map<String, byte[]> resources = new HashMap<>();

      try {
         resources.put("pack.mcmeta", packMeta().getBytes(StandardCharsets.UTF_8));
         copyRootResource(resources, "pack.png", "/Icon.png");
         writeDefaults(resources);
         writeItems(resources);
         writeBlocks(resources);
         writeRecipes(resources);
         writeLootTables(resources);
         writeBlockStates(resources);
         writeTags(resources);
         DATA = hasAssetDefinitions() ? Map.copyOf(resources) : Map.of();
         MLuaLogger.LOGGER.info("Generated MLua pack with {} resources (items={}, blocks={}, recipes={}, lootTables={}, blockstates={}, tags={})", new Object[]{resources.size(), MLuaLoaderRegistry.getRegisteredIds(RegistryKind.ITEM).size(), MLuaLoaderRegistry.getRegisteredIds(RegistryKind.BLOCK).size(), MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.RECIPE).size(), MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.LOOT_TABLE).size(), MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.BLOCKSTATE).size(), MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.ITEM_TAG).size() + MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.BLOCK_TAG).size() + MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.ENTITY_TYPE_TAG).size()});
      } catch (IOException error) {
         DATA = Map.of();
         MLuaLogger.LOGGER.warn("Failed to generate MLua pack in memory", error);
      }

   }

   public static boolean hasGeneratedContent() {
      return !DATA.isEmpty();
   }

   private static boolean hasAssetDefinitions() {
      if (!MLuaLoaderRegistry.getRegisteredIds(RegistryKind.ITEM).isEmpty()
          || !MLuaLoaderRegistry.getRegisteredIds(RegistryKind.BLOCK).isEmpty()) {
         return true;
      }
      for (GeneratedAssetKind kind : GeneratedAssetKind.values()) {
         if (!MLuaLoaderRegistry.getGeneratedAssets(kind).isEmpty()) return true;
      }
      return false;
   }

   private static void writeDefaults(Map<String, byte[]> resources) {
      JsonObject item = new JsonObject();
      item.addProperty("parent", "minecraft:item/generated");
      JsonObject itemTextures = new JsonObject();
      itemTextures.addProperty("layer0", "minecraft:item/barrier");
      item.add("textures", itemTextures);
      put(resources, "assets/mlua/models/item/default.json", item.toString());
      JsonObject block = new JsonObject();
      block.addProperty("parent", "minecraft:block/cube_all");
      JsonObject blockTextures = new JsonObject();
      blockTextures.addProperty("all", "minecraft:block/stone");
      block.add("textures", blockTextures);
      put(resources, "assets/mlua/models/block/default.json", block.toString());
      JsonObject state = new JsonObject();
      JsonObject variants = new JsonObject();
      JsonObject variant = new JsonObject();
      variant.addProperty("model", "mlua:block/default");
      variants.add("", variant);
      state.add("variants", variants);
      put(resources, "assets/mlua/blockstates/default.json", state.toString());
   }

   public static PackResourcesImpl createPack() {
      return new PackResourcesImpl(DATA);
   }

   private static String packMeta() {
      JsonObject meta = new JsonObject();
      JsonObject pack = new JsonObject();
      pack.addProperty("min_format", 84);
      pack.addProperty("max_format", 101);
      pack.addProperty("description", "MLua generated assets");
      meta.add("pack", pack);
      return meta.toString();
   }

   private static void writeItems(Map<String, byte[]> resources) {
      Set<String> blockIds = MLuaLoaderRegistry.getRegisteredIds(RegistryKind.BLOCK);
      for (String entryId : MLuaLoaderRegistry.getRegisteredIds(RegistryKind.ITEM)) {
         String namespace = namespaceOf(entryId);
         String path = pathOf(entryId);
         String key = namespace + ":" + path;
         JsonObject json = new JsonObject();
         MLuaTextureDescriptor texture = MLuaLoaderRegistry.getItemTexture(namespace, path);
         String model = MLuaLoaderRegistry.getItemModel(namespace, path);
         String copy = MLuaLoaderRegistry.getItemCopy(namespace, path);
         boolean copyModel = MLuaLoaderRegistry.getItemCopyModel(namespace, path);
         if (model != null && !model.isBlank()) {
            json.addProperty("parent", model);
         } else if (blockIds.contains(key)) {
            json.addProperty("parent", namespace + ":block/" + path);
         } else if (!texture.isEmpty()) {
            json.addProperty("parent", "minecraft:item/generated");
            writeItemTexture(json, texture);
         } else if (copyModel && copy != null && !copy.isBlank()) {
            json.addProperty("parent", MLuaAssetResolver.resolveModelString(namespace, copy, "item"));
         } else {
            json.addProperty("parent", "minecraft:item/generated");
            writePlaceholderItemTexture(json);
         }
         put(resources, "assets/" + namespace + "/models/item/" + path + ".json", json.toString());
      }

   }

   private static void writeBlocks(Map<String, byte[]> resources) {
      Map<String, JsonObject> blockStates = MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.BLOCKSTATE);
      for (String keyId : MLuaLoaderRegistry.getRegisteredIds(RegistryKind.BLOCK)) {
         String namespace = namespaceOf(keyId);
         String path = pathOf(keyId);
         String key = namespace + ":" + path;
         JsonObject model = new JsonObject();
         MLuaTextureDescriptor texture = MLuaLoaderRegistry.getBlockTexture(namespace, path);
         String parent = MLuaLoaderRegistry.getBlockModel(namespace, path);
         String copy = MLuaLoaderRegistry.getBlockCopy(namespace, path);
         if (!texture.isEmpty()) {
            parent = texture.all() != null ? "minecraft:block/cube_all" : "minecraft:block/cube";
            model.addProperty("parent", parent);
            writeBlockTextures(model, texture);
         } else if (parent != null && !parent.isBlank()) {
            model.addProperty("parent", parent);
         } else if (copy != null && !copy.isBlank()) {
            model.addProperty("parent", MLuaAssetResolver.resolveModelString(namespace, copy, "block"));
         } else {
            model.addProperty("parent", "minecraft:block/cube_all");
            writePlaceholderBlockTextures(model);
         }

         put(resources, "assets/" + namespace + "/models/block/" + path + ".json", model.toString());
         JsonObject state = blockStates.getOrDefault(key, defaultBlockState(namespace, path)).deepCopy();
         put(resources, "assets/" + namespace + "/blockstates/" + path + ".json", state.toString());
      }

   }

   private static void writeRecipes(Map<String, byte[]> resources) {
      writeDataObjects(resources, LEGACY_DATA_DIRECTORIES ? "recipes" : "recipe", MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.RECIPE));
   }

   private static void writeLootTables(Map<String, byte[]> resources) {
      writeDataObjects(resources, LEGACY_DATA_DIRECTORIES ? "loot_tables" : "loot_table", MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.LOOT_TABLE));
   }

   private static void writeBlockStates(Map<String, byte[]> resources) {
      MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.BLOCKSTATE).forEach((id, json) ->
         put(resources, "assets/" + namespaceOf(id) + "/blockstates/" + pathOf(id) + ".json", json.toString()));
   }

   private static void writeTags(Map<String, byte[]> resources) {
      writeDataObjects(resources, LEGACY_DATA_DIRECTORIES ? "tags/items" : "tags/item", MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.ITEM_TAG));
      writeDataObjects(resources, LEGACY_DATA_DIRECTORIES ? "tags/blocks" : "tags/block", MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.BLOCK_TAG));
      writeDataObjects(resources, LEGACY_DATA_DIRECTORIES ? "tags/entity_types" : "tags/entity_type", MLuaLoaderRegistry.getGeneratedAssets(GeneratedAssetKind.ENTITY_TYPE_TAG));
   }

   private static void writeDataObjects(Map<String, byte[]> resources, String folder, Map<String, JsonObject> definitions) {
      definitions.forEach((id, json) -> {
         put(resources, "data/" + namespaceOf(id) + "/" + folder + "/" + pathOf(id) + ".json", json.toString());
      });
   }

   private static void writeBlockTextures(JsonObject model, MLuaTextureDescriptor texture) {
      JsonObject textures = new JsonObject();
      if (texture.all() != null) {
         textures.addProperty("all", texture.all());
      } else {
         String fallback = texture.faces().getOrDefault("all", "minecraft:block/stone");
         for (String face : new String[] {"north", "south", "east", "west", "up", "down"}) {
            textures.addProperty(face, texture.faces().getOrDefault(face, fallback));
         }
         textures.addProperty("particle", texture.faces().getOrDefault("particle", firstTexture(texture, fallback)));
      }

      model.add("textures", textures);
   }

   private static void writePlaceholderBlockTextures(JsonObject model) {
      JsonObject textures = new JsonObject();
      textures.addProperty("all", "minecraft:block/stone");
      model.add("textures", textures);
   }

   private static void writeItemTexture(JsonObject model, MLuaTextureDescriptor texture) {
      JsonObject textures = new JsonObject();
      textures.addProperty("layer0", texture.all() != null ? texture.all() : firstTexture(texture, "minecraft:item/barrier"));
      model.add("textures", textures);
   }

   private static void writePlaceholderItemTexture(JsonObject model) {
      JsonObject textures = new JsonObject();
      textures.addProperty("layer0", "minecraft:item/barrier");
      model.add("textures", textures);
   }

   private static String firstTexture(MLuaTextureDescriptor texture, String fallback) {
      return texture.faces().values().stream().findFirst().orElse(fallback);
   }

   private static JsonObject defaultBlockState(String namespace, String path) {
      JsonObject state = new JsonObject();
      JsonObject variants = new JsonObject();
      JsonObject variant = new JsonObject();
      variant.addProperty("model", namespace + ":block/" + path);
      variants.add("", variant);
      state.add("variants", variants);
      return state;
   }

   private static void put(Map<String, byte[]> resources, String path, String json) {
      resources.put(path, json.getBytes(StandardCharsets.UTF_8));
   }

   private static String namespaceOf(String id) {
      int split = id.indexOf(':');
      return split >= 0 ? id.substring(0, split) : "minecraft";
   }

   private static String pathOf(String id) {
      int split = id.indexOf(':');
      return split >= 0 ? id.substring(split + 1) : id;
   }

   private static String namespaceOf(ResourceLocation id) {
      return namespaceOf(id.toString());
   }

   private static String pathOf(ResourceLocation id) {
      return pathOf(id.toString());
   }

   private static void copyRootResource(Map<String, byte[]> resources, String targetPath, String classpathPath) throws IOException {
      try (InputStream input = MLuaGeneratedAssetPack.class.getResourceAsStream(classpathPath)) {
         if (input != null) {
            resources.put(targetPath, input.readAllBytes());
         }
      }
   }

   public static final class PackResourcesImpl extends AbstractPackResources {
      private final Map<String, byte[]> resources;

      public PackResourcesImpl(Map<String, byte[]> resources) {
         super(PACK_INFO);
         this.resources = resources;
      }

      public IoSupplier<InputStream> getRootResource(String... elements) {
         String path = String.join("/", elements);
         byte[] data = resources.get(path);
         return data == null ? null : () -> new ByteArrayInputStream(data);
      }

      public IoSupplier<InputStream> getResource(PackType packType, ResourceLocation location) {
         String root = switch (packType) {
            case CLIENT_RESOURCES -> "assets/";
            case SERVER_DATA -> "data/";
            default -> null;
         };
         if (root == null) {
            return null;
         }

         String path = root + MLuaGeneratedAssetPack.namespaceOf(location) + "/" + MLuaGeneratedAssetPack.pathOf(location);
         byte[] bytes = resources.get(path);
         return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
      }

      public void listResources(PackType packType, String namespace, String prefix, PackResources.ResourceOutput output) {
         String root = switch (packType) {
            case CLIENT_RESOURCES -> "assets/";
            case SERVER_DATA -> "data/";
            default -> null;
         };
         if (root == null) {
            return;
         }

         String search = root + namespace + "/" + prefix;
         resources.forEach((path, bytes) -> {
            if (path.startsWith(search)) {
               String relative = path.substring((root + namespace + "/").length());
               ResourceLocation id = MLuaResourceLocations.build(namespace, relative);
               output.accept(id, () -> new ByteArrayInputStream(bytes));
            }
         });
      }

      public Set<String> getNamespaces(PackType packType) {
         String root = switch (packType) {
            case CLIENT_RESOURCES -> "assets/";
            case SERVER_DATA -> "data/";
            default -> null;
         };
         if (root == null) {
            return Set.of();
         }

         Set<String> namespaces = new HashSet<>();
         for (String path : resources.keySet()) {
            if (path.startsWith(root)) {
               String[] parts = path.split("/");
               if (parts.length > 1) {
                  namespaces.add(parts[1]);
               }
            }
         }

         return Set.copyOf(namespaces);
      }

      public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
         byte[] metadata = resources.get("pack.mcmeta");
         if (metadata == null) return null;
         JsonObject root = JsonParser.parseString(new String(metadata, StandardCharsets.UTF_8)).getAsJsonObject();
         var section = root.get(serializer.name());
         return section == null ? null : serializer.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, section).result().orElse(null);
      }

      public String packId() {
         return PACK_ID;
      }

      public void close() {
      }
   }
}
