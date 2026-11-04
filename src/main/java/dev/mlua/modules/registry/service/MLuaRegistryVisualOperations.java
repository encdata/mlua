package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.function.UnaryOperator;

import dev.mlua.loaderapi.EntityVisualInfo;
import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.runtime.registry.MLuaEntityVisualDescriptor;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;

/** Owns item, block, and entity visual metadata together with registration rollback. */
final class MLuaRegistryVisualOperations {
   private final MLuaRegistryService service;

   MLuaRegistryVisualOperations(MLuaRegistryService service) {
      this.service = service;
   }

   void update(RegistryKind kind, String owner, String name, UnaryOperator<Metadata> update) {
      Metadata previous = read(kind, owner, name);
      Metadata requested = update.apply(previous);
      try {
         write(kind, owner, name, requested);
      } catch (RuntimeException | Error failure) {
         write(kind, owner, name, previous);
         throw failure;
      }
      Metadata installed = read(kind, owner, name);
      service.apiContext().mlua().registration().trackRollback(() -> {
         if (read(kind, owner, name).equals(installed)) write(kind, owner, name, previous);
      });
   }

   void registerEntity(String owner, String name, String texture, String model, float shadowRadius) {
      String key = owner + ":" + name;
      MLuaEntityVisualDescriptor previous = MLuaLoaderRegistry.getEntityVisual(key);
      MLuaLoaderRegistry.setEntityVisual(owner, name, texture, model, shadowRadius);
      EntityVisualInfo expected = new EntityVisualInfo(texture, model, shadowRadius);
      EntityVisualInfo before = previous == null ? null
         : new EntityVisualInfo(previous.texture(), previous.model(), previous.shadowRadius());
      service.apiContext().mlua().registration().trackRollback(
         () -> MLuaLoaderRegistry.restoreEntityVisual(owner, name, expected, before));
   }

   private static Metadata read(RegistryKind kind, String owner, String name) {
      return switch (kind) {
         case ITEM -> new Metadata(MLuaLoaderRegistry.getItemCopy(owner, name),
            MLuaLoaderRegistry.getItemCopyModel(owner, name), MLuaLoaderRegistry.getItemDescription(owner, name),
            new MLuaTextureDescriptor(MLuaLoaderRegistry.getItemTexture(owner, name).faces()),
            MLuaLoaderRegistry.getItemModel(owner, name));
         case BLOCK -> new Metadata(MLuaLoaderRegistry.getBlockCopy(owner, name),
            MLuaLoaderRegistry.getBlockCopyModel(owner, name), MLuaLoaderRegistry.getBlockDescription(owner, name),
            new MLuaTextureDescriptor(MLuaLoaderRegistry.getBlockTexture(owner, name).faces()),
            MLuaLoaderRegistry.getBlockModel(owner, name));
         default -> throw new IllegalArgumentException("Visual metadata is supported only for items and blocks");
      };
   }

   private static void write(RegistryKind kind, String owner, String name, Metadata metadata) {
      switch (kind) {
         case ITEM -> {
            MLuaLoaderRegistry.setItemCopy(owner, name, metadata.copy());
            MLuaLoaderRegistry.setItemCopyModel(owner, name, metadata.copyModel());
            MLuaLoaderRegistry.setItemDescription(owner, name, metadata.description());
            MLuaLoaderRegistry.setItemVisuals(owner, name, metadata.texture(), metadata.model());
         }
         case BLOCK -> {
            MLuaLoaderRegistry.setBlockCopy(owner, name, metadata.copy());
            MLuaLoaderRegistry.setBlockCopyModel(owner, name, metadata.copyModel());
            MLuaLoaderRegistry.setBlockDescription(owner, name, metadata.description());
            MLuaLoaderRegistry.setBlockVisuals(owner, name, metadata.texture(), metadata.model());
         }
         default -> throw new IllegalArgumentException("Visual metadata is supported only for items and blocks");
      }
   }

   record Metadata(String copy, boolean copyModel, List<String> description,
                   MLuaTextureDescriptor texture, String model) {
      Metadata {
         if (copy != null && copy.isBlank()) copy = null;
         if (description != null) description = List.copyOf(description);
         texture = texture == null ? MLuaTextureDescriptor.empty() : texture;
         if (model != null && model.isBlank()) model = null;
      }
   }
}
