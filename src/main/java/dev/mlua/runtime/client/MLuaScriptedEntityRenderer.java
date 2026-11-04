package dev.mlua.runtime.client;

import dev.mlua.runtime.registry.MLuaEntityVisualDescriptor;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.Locale;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

public final class MLuaScriptedEntityRenderer extends MobRenderer<Mob, HumanoidModel<Mob>> {
   private final ResourceLocation texture;

   public MLuaScriptedEntityRenderer(EntityRendererProvider.Context context, String registryKey) {
      super(context, new HumanoidModel<>(context.bakeLayer(resolveModelLayer(registryKey))), shadowRadius(registryKey));
      this.texture = resolveTexture(registryKey);
   }

   public ResourceLocation getTextureLocation(Mob entity) {
      return this.texture;
   }

   private static ModelLayerLocation resolveModelLayer(String registryKey) {
      MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(registryKey);
      String model = visuals != null ? visuals.normalizedModel() : "zombie";
      return switch (model.trim().toLowerCase(Locale.ROOT)) {
         case "zombie" -> ModelLayers.ZOMBIE;
         case "zombie_inner_armor" -> ModelLayers.ZOMBIE_INNER_ARMOR;
         case "zombie_outer_armor" -> ModelLayers.ZOMBIE_OUTER_ARMOR;
         case "skeleton" -> ModelLayers.SKELETON;
         case "spider" -> ModelLayers.SPIDER;
         case "creeper" -> ModelLayers.CREEPER;
         case "villager" -> ModelLayers.VILLAGER;
         case "player" -> ModelLayers.PLAYER;
         case "player_slim" -> ModelLayers.PLAYER_SLIM;
         default -> ModelLayers.ZOMBIE;
      };
   }

   private static ResourceLocation resolveTexture(String registryKey) {
      MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(registryKey);
      return MLuaResourceLocations.parse(visuals != null ? visuals.normalizedTexture() : "minecraft:textures/entity/zombie/zombie.png");
   }

   private static float shadowRadius(String registryKey) {
      MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(registryKey);
      return visuals != null ? visuals.shadowRadius() : 0.5F;
   }
}
