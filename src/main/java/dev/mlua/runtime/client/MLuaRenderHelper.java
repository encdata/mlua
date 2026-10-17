package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.runtime.client.model.MLuaMeshVertex;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class MLuaRenderHelper {
   private static final ResourceLocation DEFAULT_TEXTURE = MLuaResourceLocations.build("minecraft", "textures/block/stone.png");

   private MLuaRenderHelper() {
   }

   static ResourceLocation fallbackTexture() {
      return DEFAULT_TEXTURE;
   }

   static void renderCube(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, int overlay, float scale) {
      renderCube(poseStack, buffer, texture, light, overlay, scale, true, true, true, true, true, true);
   }

   static void renderCube(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, int overlay, float scale, boolean north, boolean south, boolean east, boolean west, boolean up, boolean down) {
      renderCube(poseStack, buffer, texture, light, overlay, scale, north, south, east, west, up, down, light, light, light, light, light, light);
   }

   static void renderCube(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int defaultLight, int overlay, float scale, boolean north, boolean south, boolean east, boolean west, boolean up, boolean down, int northLight, int southLight, int eastLight, int westLight, int upLight, int downLight) {
      PoseStack.Pose pose = poseStack.last();
      Matrix4f poseMatrix = pose.pose();
      Matrix3f normalMatrix = pose.normal();
      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
      float min = 0.0F;
      if (north) {
         face(consumer, poseMatrix, normalMatrix, northLight, overlay, 0.0F, 0.0F, -1.0F, scale, min, min, min, min, min, min, scale, min, scale, scale, min);
      }

      if (south) {
         face(consumer, poseMatrix, normalMatrix, southLight, overlay, 0.0F, 0.0F, 1.0F, min, min, scale, scale, min, scale, scale, scale, scale, min, scale, scale);
      }

      if (down) {
         face(consumer, poseMatrix, normalMatrix, downLight, overlay, 0.0F, -1.0F, 0.0F, min, min, min, scale, min, min, scale, min, scale, min, min, scale);
      }

      if (up) {
         face(consumer, poseMatrix, normalMatrix, upLight, overlay, 0.0F, 1.0F, 0.0F, min, scale, scale, scale, scale, scale, scale, scale, min, min, scale, min);
      }

      if (west) {
         face(consumer, poseMatrix, normalMatrix, westLight, overlay, -1.0F, 0.0F, 0.0F, min, min, min, min, min, scale, min, scale, scale, min, scale, min);
      }

      if (east) {
         face(consumer, poseMatrix, normalMatrix, eastLight, overlay, 1.0F, 0.0F, 0.0F, scale, min, scale, scale, min, min, scale, scale, min, scale, scale, scale);
      }

   }

   static void renderItemCube(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, int overlay) {
      poseStack.pushPose();
      poseStack.translate(-0.5, -0.5, -0.5);
      renderCube(poseStack, buffer, texture, light, overlay, 1.0F);
      poseStack.popPose();
   }

   static void renderItemQuad(PoseStack poseStack, MultiBufferSource buffer, ResourceLocation texture, int light, int overlay, float scale) {
      PoseStack.Pose pose = poseStack.last();
      Matrix4f poseMatrix = pose.pose();
      Matrix3f normalMatrix = pose.normal();
      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
      float min = -scale;
      face(consumer, poseMatrix, normalMatrix, light, overlay, 0.0F, 0.0F, 1.0F, min, min, 0.0F, scale, min, 0.0F, scale, scale, 0.0F, min, scale, 0.0F);
      face(consumer, poseMatrix, normalMatrix, light, overlay, 0.0F, 0.0F, -1.0F, scale, min, 0.0F, min, min, 0.0F, min, scale, 0.0F, scale, scale, 0.0F);
   }

   static void renderMesh(PoseStack poseStack, MultiBufferSource buffer, MLuaMeshDefinition mesh,
                          ResourceLocation texture, int light, int overlay) {
      PoseStack.Pose pose = poseStack.last();
      Matrix4f poseMatrix = pose.pose();
      Matrix3f normalMatrix = pose.normal();
      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
      for (int i = 0; i + 2 < mesh.indices().size(); i += 3) {
         MLuaMeshVertex a = mesh.vertices().get(mesh.indices().get(i));
         MLuaMeshVertex b = mesh.vertices().get(mesh.indices().get(i + 1));
         MLuaMeshVertex c = mesh.vertices().get(mesh.indices().get(i + 2));
         meshVertex(consumer, poseMatrix, normalMatrix, a, light, overlay);
         meshVertex(consumer, poseMatrix, normalMatrix, b, light, overlay);
         meshVertex(consumer, poseMatrix, normalMatrix, c, light, overlay);
      }
   }

   static void renderBBModelCube(PoseStack poseStack, MultiBufferSource buffer, MLuaBBModelAsset.Cube cube,
                                 List<ResourceLocation> textures, int textureWidth, int textureHeight,
                                 ResourceLocation fallback, int light, int overlay) {
      drawBBFace(poseStack, buffer, cube.faces().get("north"), textures, textureWidth, textureHeight, fallback,
         cube.from(0), cube.from(1), cube.from(2), cube.to(0), cube.to(1), cube.from(2), 0, 0, -1, light, overlay);
      drawBBFace(poseStack, buffer, cube.faces().get("south"), textures, textureWidth, textureHeight, fallback,
         cube.to(0), cube.from(1), cube.to(2), cube.from(0), cube.to(1), cube.to(2), 0, 0, 1, light, overlay);
      drawBBFace(poseStack, buffer, cube.faces().get("west"), textures, textureWidth, textureHeight, fallback,
         cube.from(0), cube.from(1), cube.to(2), cube.from(0), cube.to(1), cube.from(2), -1, 0, 0, light, overlay);
      drawBBFace(poseStack, buffer, cube.faces().get("east"), textures, textureWidth, textureHeight, fallback,
         cube.to(0), cube.from(1), cube.from(2), cube.to(0), cube.to(1), cube.to(2), 1, 0, 0, light, overlay);
      drawBBFace(poseStack, buffer, cube.faces().get("up"), textures, textureWidth, textureHeight, fallback,
         cube.from(0), cube.to(1), cube.from(2), cube.to(0), cube.to(1), cube.to(2), 0, 1, 0, light, overlay);
      drawBBFace(poseStack, buffer, cube.faces().get("down"), textures, textureWidth, textureHeight, fallback,
         cube.from(0), cube.from(1), cube.to(2), cube.to(0), cube.from(1), cube.from(2), 0, -1, 0, light, overlay);
   }

   private static void drawBBFace(PoseStack poseStack, MultiBufferSource buffer, MLuaBBModelAsset.Face face,
                                  List<ResourceLocation> textures, int width, int height, ResourceLocation fallback,
                                  float x1, float y1, float z1, float x2, float y2, float z2,
                                  float nx, float ny, float nz, int light, int overlay) {
      ResourceLocation texture = fallback;
      if (face != null && face.texture() >= 0 && face.texture() < textures.size()) texture = textures.get(face.texture());
      float u1 = (face == null || !face.hasUv() ? 0 : face.uv(0)) / Math.max(1, width);
      float v1 = (face == null || !face.hasUv() ? 0 : face.uv(1)) / Math.max(1, height);
      float u2 = (face == null || !face.hasUv() ? width : face.uv(2)) / Math.max(1, width);
      float v2 = (face == null || !face.hasUv() ? height : face.uv(3)) / Math.max(1, height);
      PoseStack.Pose pose = poseStack.last();
      Matrix4f matrix = pose.pose();
      Matrix3f normal = pose.normal();
      VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
      texturedFace(consumer, matrix, normal, light, overlay, nx, ny, nz,
         x1 / 16.0F, y1 / 16.0F, z1 / 16.0F,
         x1 / 16.0F, y2 / 16.0F, z1 / 16.0F,
         x2 / 16.0F, y2 / 16.0F, z2 / 16.0F,
         x2 / 16.0F, y1 / 16.0F, z2 / 16.0F, u1, v1, u2, v2);
   }

   private static void texturedFace(VertexConsumer consumer, Matrix4f matrix, Matrix3f normalMatrix,
                                    int light, int overlay, float nx, float ny, float nz,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    float u1, float v1, float u2, float v2) {
      //--version 1.21.1
      consumer.addVertex(matrix, x1, y1, z1).setColor(1, 1, 1, 1).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x2, y2, z2).setColor(1, 1, 1, 1).setUv(u1, v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x3, y3, z3).setColor(1, 1, 1, 1).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x4, y4, z4).setColor(1, 1, 1, 1).setUv(u2, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      //--endversion
      //--version 26.1
      consumer.addVertex(matrix, x1, y1, z1).setColor(1, 1, 1, 1).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x2, y2, z2).setColor(1, 1, 1, 1).setUv(u1, v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x3, y3, z3).setColor(1, 1, 1, 1).setUv(u2, v2).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(matrix, x4, y4, z4).setColor(1, 1, 1, 1).setUv(u2, v1).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      //--endversion
      //--version 1.20.1
      consumer.vertex(matrix, x1, y1, z1).color(1, 1, 1, 1).uv(u1, v1).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(matrix, x2, y2, z2).color(1, 1, 1, 1).uv(u1, v2).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(matrix, x3, y3, z3).color(1, 1, 1, 1).uv(u2, v2).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(matrix, x4, y4, z4).color(1, 1, 1, 1).uv(u2, v1).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      //--endversion
   }

   private static void meshVertex(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix,
                                  MLuaMeshVertex vertex, int light, int overlay) {
      float alpha = ((vertex.color() >>> 24) & 255) / 255.0F;
      float red = ((vertex.color() >>> 16) & 255) / 255.0F;
      float green = ((vertex.color() >>> 8) & 255) / 255.0F;
      float blue = (vertex.color() & 255) / 255.0F;
      //--version 1.21.1
      consumer.addVertex(poseMatrix, vertex.x(), vertex.y(), vertex.z()).setColor(red, green, blue, alpha)
         .setUv(vertex.u(), vertex.v()).setOverlay(overlay).setLight(light).setNormal(vertex.nx(), vertex.ny(), vertex.nz());
      //--endversion
      //--version 26.1
      consumer.addVertex(poseMatrix, vertex.x(), vertex.y(), vertex.z()).setColor(red, green, blue, alpha)
         .setUv(vertex.u(), vertex.v()).setOverlay(overlay).setLight(light).setNormal(vertex.nx(), vertex.ny(), vertex.nz());
      //--endversion
      //--version 1.20.1
      consumer.vertex(poseMatrix, vertex.x(), vertex.y(), vertex.z()).color(red, green, blue, alpha)
         .uv(vertex.u(), vertex.v()).overlayCoords(overlay).uv2(light).normal(normalMatrix, vertex.nx(), vertex.ny(), vertex.nz()).endVertex();
      //--endversion
   }

   private static void face(VertexConsumer consumer, Matrix4f poseMatrix, Matrix3f normalMatrix, int light, int overlay, float nx, float ny, float nz, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
      //--version 1.21.1
      consumer.addVertex(poseMatrix, x1, y1, z1).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x2, y2, z2).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x3, y3, z3).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x4, y4, z4).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      //--endversion
      //--version 26.1
      consumer.addVertex(poseMatrix, x1, y1, z1).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(0.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x2, y2, z2).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(1.0F, 0.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x3, y3, z3).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(1.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      consumer.addVertex(poseMatrix, x4, y4, z4).setColor(1.0F, 1.0F, 1.0F, 1.0F).setUv(0.0F, 1.0F).setOverlay(overlay).setLight(light).setNormal(nx, ny, nz);
      //--endversion
      //--version 1.20.1
      consumer.vertex(poseMatrix, x1, y1, z1).color(1.0F, 1.0F, 1.0F, 1.0F).uv(0.0F, 0.0F).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(poseMatrix, x2, y2, z2).color(1.0F, 1.0F, 1.0F, 1.0F).uv(1.0F, 0.0F).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(poseMatrix, x3, y3, z3).color(1.0F, 1.0F, 1.0F, 1.0F).uv(1.0F, 1.0F).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      consumer.vertex(poseMatrix, x4, y4, z4).color(1.0F, 1.0F, 1.0F, 1.0F).uv(0.0F, 1.0F).overlayCoords(overlay).uv2(light).normal(normalMatrix, nx, ny, nz).endVertex();
      //--endversion
   }

   static ResourceLocation textureOrFallback(ResourceLocation texture) {
      return texture != null ? texture : fallbackTexture();
   }
}
