package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import dev.mlua.runtime.registry.MLuaTextureDescriptor;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.client.MLuaCameraController;
import dev.mlua.minecraft.client.MLuaMinecraftClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class MLuaVisualItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static MLuaVisualItemRenderer INSTANCE;

   public static MLuaVisualItemRenderer instance() {
      if (INSTANCE == null) {
         Minecraft mc = MLuaMinecraftClient.instance();
         BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
         EntityModelSet models = mc.getEntityModels();
         INSTANCE = new MLuaVisualItemRenderer(dispatcher, models);
      }

      return INSTANCE;
   }

   private MLuaVisualItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
      super(dispatcher, models);
   }

    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
       ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
       if (id != null) {
          ResourceLocation texture = this.resolveTexture(stack, id);
          int renderLight = context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND && context != ItemDisplayContext.FIXED ? light : 15728880;
          poseStack.pushPose();
          applyViewmodelOffset(poseStack, context);
         if (stack.getItem() instanceof BlockItem) {
            if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND && context != ItemDisplayContext.FIXED) {
               if (context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                  if (context != ItemDisplayContext.FIRST_PERSON_LEFT_HAND && context != ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
                     poseStack.scale(0.7F, 0.7F, 0.7F);
                  } else {
                     poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
                     poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
                     poseStack.scale(0.7F, 0.7F, 0.7F);
                  }
               } else {
                  poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
                  poseStack.mulPose(Axis.YP.rotationDegrees(45.0F));
                  poseStack.scale(0.65F, 0.65F, 0.65F);
               }
            } else {
               poseStack.translate(0.5, 0.5, 0.5);
               poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
               poseStack.mulPose(Axis.YP.rotationDegrees(225.0F));
               poseStack.scale(0.625F, 0.625F, 0.625F);
            }

            MLuaRenderHelper.renderItemCube(poseStack, buffer, texture, renderLight, overlay);
         } else if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND && context != ItemDisplayContext.FIXED) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(35.0F));
            poseStack.scale(0.7F, 0.7F, 0.7F);
            MLuaRenderHelper.renderItemQuad(poseStack, buffer, texture, renderLight, overlay, 0.42F);
         } else {
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(0.85F, 0.85F, 0.85F);
            MLuaRenderHelper.renderItemQuad(poseStack, buffer, texture, renderLight, overlay, 0.45F);
         }

         poseStack.popPose();
      }
   }

   private void applyViewmodelOffset(PoseStack poseStack, ItemDisplayContext context) {
      MLuaCFrame offset;
      if (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
         offset = MLuaCameraController.getHeldItemLeftOffset();
      } else if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
         offset = MLuaCameraController.getHeldItemOffset();
      } else {
         return;
      }
      if (offset == null) return;
      poseStack.translate(offset.x(), offset.y(), offset.z());
      double[] euler = offset.toEulerAnglesXYZ();
      if (euler[0] != 0.0) poseStack.mulPose(Axis.XP.rotation((float)euler[0]));
      if (euler[1] != 0.0) poseStack.mulPose(Axis.YP.rotation((float)euler[1]));
      if (euler[2] != 0.0) poseStack.mulPose(Axis.ZP.rotation((float)euler[2]));
   }

   private ResourceLocation resolveTexture(ItemStack stack, ResourceLocation id) {
      String namespace = MLuaResourceLocations.namespace(id);
      String path = MLuaResourceLocations.path(id);
      MLuaTextureDescriptor texture;
      String resolved;
      if (stack.getItem() instanceof BlockItem) {
         texture = MLuaLoaderRegistry.getBlockTexture(namespace, path);
         if (texture.isEmpty()) {
            resolved = MLuaLoaderRegistry.getBlockCopy(namespace, path);
            if (resolved != null && !resolved.isBlank()) {
               return MLuaAssetResolver.resolveTextureFile(namespace, resolved, "block");
            }
         }
      } else {
         texture = MLuaLoaderRegistry.getItemTexture(namespace, path);
         if (texture.isEmpty()) {
            resolved = MLuaLoaderRegistry.getItemCopy(namespace, path);
            if (resolved != null && !resolved.isBlank()) {
               return MLuaAssetResolver.resolveTextureFile(namespace, resolved, "item");
            }
         }
      }

      resolved = texture.all();
      if (resolved == null) {
         resolved = texture.faces().values().stream().findFirst().orElse(null);
      }

      if (resolved == null) {
         return MLuaRenderHelper.fallbackTexture();
      } else {
         ResourceLocation location = MLuaAssetResolver.resolveTextureFile(namespace, resolved, "item");
         return MLuaMinecraftClient.instance().getResourceManager().getResource(location).isPresent() ? location : MLuaRenderHelper.fallbackTexture();
      }
   }
}
