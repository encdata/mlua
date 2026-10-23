package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mlua.math.MLuaCFrame;
import net.minecraft.client.renderer.texture.OverlayTexture;
import dev.mlua.minecraft.animation.MLuaBBModelAsset;

/** Shared 26.1 vertex and face emission for mesh and Blockbench render targets. */
final class MLuaRenderVertexEmitter {
    private MLuaRenderVertexEmitter() { }

    static void submitBBFace(VertexConsumer out, PoseStack.Pose pose, String direction,
                             MLuaBBModelAsset.Cube cube, MLuaBBModelAsset.Face face,
                             int textureWidth, int textureHeight, int light) {
        submitBBFace(out, pose, direction, cube, face, textureWidth, textureHeight,
            light, OverlayTexture.NO_OVERLAY);
    }

    static void submitBBFace(VertexConsumer out, PoseStack.Pose pose, String direction,
                             MLuaBBModelAsset.Cube cube, MLuaBBModelAsset.Face face,
                             int textureWidth, int textureHeight, int light, int overlay) {
        float x1 = cube.from(0) / 16.0F, y1 = cube.from(1) / 16.0F, z1 = cube.from(2) / 16.0F;
        float x2 = cube.to(0) / 16.0F, y2 = cube.to(1) / 16.0F, z2 = cube.to(2) / 16.0F;
        float nx = 0, ny = 0, nz = 0;
        switch (direction) {
            case "north" -> nz = -1;
            case "south" -> nz = 1;
            case "west" -> nx = -1;
            case "east" -> nx = 1;
            case "up" -> ny = 1;
            case "down" -> ny = -1;
            default -> { return; }
        }
        float u1 = (face.hasUv() ? face.uv(0) : 0) / Math.max(1, textureWidth);
        float v1 = (face.hasUv() ? face.uv(1) : 0) / Math.max(1, textureHeight);
        float u2 = (face.hasUv() ? face.uv(2) : textureWidth) / Math.max(1, textureWidth);
        float v2 = (face.hasUv() ? face.uv(3) : textureHeight) / Math.max(1, textureHeight);
        int turns = Math.floorMod(face.rotation(), 360) / 90;
        for (int i = 0; i < 4; i++) {
            float x;
            float y;
            float z;
            switch (direction) {
                case "north" -> { x = i == 0 || i == 3 ? x1 : x2; y = i < 2 ? y1 : y2; z = z1; }
                case "south" -> { x = i == 0 || i == 3 ? x2 : x1; y = i < 2 ? y1 : y2; z = z2; }
                case "west" -> { x = x1; y = i < 2 ? y1 : y2; z = i == 0 || i == 3 ? z2 : z1; }
                case "east" -> { x = x2; y = i < 2 ? y1 : y2; z = i == 0 || i == 3 ? z1 : z2; }
                case "up" -> { x = i == 0 || i == 3 ? x1 : x2; y = y2; z = i < 2 ? z1 : z2; }
                case "down" -> { x = i == 0 || i == 3 ? x1 : x2; y = y1; z = i < 2 ? z2 : z1; }
                default -> throw new IllegalStateException("Validated BBModel face direction changed");
            }
            int uv = Math.floorMod(i - turns, 4);
            float u = uv == 0 || uv == 3 ? u1 : u2;
            float v = uv < 2 ? v2 : v1;
            submitVertex(out, pose, x, y, z, u, v, 0xFFFFFFFF, light, overlay, nx, ny, nz);
        }
    }

    static void submitVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                             float u, float v, int color, int light) {
        submitVertex(out, pose, x, y, z, u, v, color, light, 0, 1, 0);
    }

    static void submitVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                             float u, float v, int color, int light, float nx, float ny, float nz) {
        submitVertex(out, pose, x, y, z, u, v, color, light, OverlayTexture.NO_OVERLAY, nx, ny, nz);
    }

    static void submitVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                             float u, float v, int color, int light, int overlay,
                             float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
            .setColor((color >>> 16) & 255, (color >>> 8) & 255, color & 255, (color >>> 24) & 255)
            .setUv(u, v).setOverlay(overlay).setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    static void applyTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        double[] angles = transform.toEulerAnglesXYZ();
        pose.mulPose(Axis.XP.rotation((float) angles[0]));
        pose.mulPose(Axis.YP.rotation((float) angles[1]));
        pose.mulPose(Axis.ZP.rotation((float) angles[2]));
    }
}
