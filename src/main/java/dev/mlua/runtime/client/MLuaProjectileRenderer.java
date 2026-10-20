package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class MLuaProjectileRenderer extends EntityRenderer<MLuaScriptedProjectile> {
   private static final ResourceLocation TEXTURE = MLuaResourceLocations.build("minecraft", "textures/misc/white.png");

   public MLuaProjectileRenderer(EntityRendererProvider.Context context) {
      super(context);
   }

   @Override
   public void render(MLuaScriptedProjectile entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
      poseStack.pushPose();
      poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      poseStack.scale(0.3F, 0.3F, 0.3F);

      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
      PoseStack.Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      Matrix3f normal = pose.normal();

      renderQuad(consumer, matrix, normal, packedLight);
      poseStack.popPose();
      super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
   }

   private static void renderQuad(VertexConsumer consumer, Matrix4f matrix, Matrix3f normal, int light) {
      //--version 1.21.1
      consumer.addVertex(matrix, -0.5F, -0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, 0.5F, -0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, 0.5F, 0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, -0.5F, 0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      //--endversion
      //--version 26.1
      consumer.addVertex(matrix, -0.5F, -0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, 0.5F, -0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 1.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, 0.5F, 0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(1.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      consumer.addVertex(matrix, -0.5F, 0.5F, 0.0F).setColor(255, 255, 255, 255).setUv(0.0F, 0.0F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0.0F, 1.0F, 0.0F);
      //--endversion
      //--version 1.20.1
      consumer.vertex(matrix, -0.5F, -0.5F, 0.0F).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
      consumer.vertex(matrix, 0.5F, -0.5F, 0.0F).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
      consumer.vertex(matrix, 0.5F, 0.5F, 0.0F).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
      consumer.vertex(matrix, -0.5F, 0.5F, 0.0F).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normal, 0.0F, 1.0F, 0.0F).endVertex();
      //--endversion
   }

   @Override
   public ResourceLocation getTextureLocation(MLuaScriptedProjectile entity) {
      return TEXTURE;
   }
}
