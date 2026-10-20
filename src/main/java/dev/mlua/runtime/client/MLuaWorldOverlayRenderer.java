package dev.mlua.runtime.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Projects world-space overlay objects into the GUI layer. */
final class MLuaWorldOverlayRenderer {
    private MLuaWorldOverlayRenderer() { }

    private record Projection(double x, double y, double scale) { }

    static void renderWorld(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        if (object.type() == MLuaClientRenderManager.RenderType.LINE || object.type() == MLuaClientRenderManager.RenderType.BEAM) {
            renderWorldLine(graphics, object);
            return;
        }
        if (object.type() == MLuaClientRenderManager.RenderType.BOX) {
            renderWorldBox(graphics, object);
            return;
        }
        Projection projection = project(object.worldX(), object.worldY(), object.worldZ(), graphics.guiWidth(), graphics.guiHeight());
        if (object.followEntityScope() != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope());
            if (entity == null || !entity.isAlive()) {
                return;
            }
            projection = project(
                entity.getX() + object.worldX(),
                entity.getY() + entity.getBbHeight() * 0.5 + object.worldY(),
                entity.getZ() + object.worldZ(),
                graphics.guiWidth(),
                graphics.guiHeight()
            );
        }
        if (projection == null) {
            return;
        }

        double drawWidth = object.width();
        double drawHeight = object.height();
        if (object.scaleWithDistance()) {
            drawWidth *= projection.scale;
            drawHeight *= projection.scale;
        }

        MLuaClientRenderManager.RenderObject projected = object.copy();
        projected.setTarget(MLuaClientRenderManager.RenderTarget.HUD);
        projected.setPosition(projection.x - drawWidth / 2.0, projection.y - drawHeight / 2.0);
        projected.setSize(Math.max(1.0, drawWidth), Math.max(1.0, drawHeight));
        MLuaClientRenderManager.render(graphics, projected);
    }

    static void renderWorldLine(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        double startX = object.worldX();
        double startY = object.worldY();
        double startZ = object.worldZ();
        if (object.followEntityScope() != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope());
            if (entity == null || !entity.isAlive()) {
                return;
            }
            startX = entity.getX() + object.worldX();
            startY = entity.getY() + entity.getBbHeight() * 0.5 + object.worldY();
            startZ = entity.getZ() + object.worldZ();
        }
        Projection start = project(startX, startY, startZ, graphics.guiWidth(), graphics.guiHeight());
        Projection end = project(object.endWorldX(), object.endWorldY(), object.endWorldZ(), graphics.guiWidth(), graphics.guiHeight());
        if (start == null || end == null) {
            return;
        }
        MLuaHudObjectRenderer.renderLine2d(graphics, start.x, start.y, end.x, end.y, object);
    }

    static void renderWorldBox(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        double centerX = object.worldX();
        double centerY = object.worldY();
        double centerZ = object.worldZ();
        if (object.followEntityScope() != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope());
            if (entity == null || !entity.isAlive()) {
                return;
            }
            centerX = entity.getX() + object.worldX();
            centerY = entity.getY() + entity.getBbHeight() * 0.5 + object.worldY();
            centerZ = entity.getZ() + object.worldZ();
        }
        double halfWidth = object.width() / 2.0;
        double halfHeight = object.height() / 2.0;
        double halfDepth = object.depth() / 2.0;
        Projection[] corners = new Projection[] {
            project(centerX - halfWidth, centerY - halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY - halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY + halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY + halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY - halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY - halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY + halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY + halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight())
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            Projection a = corners[edge[0]];
            Projection b = corners[edge[1]];
            if (a != null && b != null) {
                MLuaHudObjectRenderer.renderLine2d(graphics, a.x, a.y, b.x, b.y, object);
            }
        }
    }

    private static Projection project(double worldX, double worldY, double worldZ, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return null;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 look = MLuaClientRenderManager.cameraLook(camera);
        var upVector = camera.getUpVector();
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        Vec3 right = up.cross(look);
        Vec3 rel = new Vec3(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);

        double camX = rel.dot(right);
        double camY = rel.dot(up);
        double camZ = rel.dot(look);
        if (camZ <= 0.05) {
            return null;
        }

        double fov = Math.toRadians(Math.max(1.0, mc.options.fov().get()));
        double focal = (screenHeight / 2.0) / Math.tan(fov / 2.0);
        double x = (screenWidth / 2.0) + (camX * focal / camZ);
        double y = (screenHeight / 2.0) - (camY * focal / camZ);
        double scale = focal / camZ / 32.0;
        if (x < -256 || x > screenWidth + 256 || y < -256 || y > screenHeight + 256) {
            return null;
        }
        return new Projection(x, y, scale);
    }
}
