package dev.mlua.runtime.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;

/** Draws GUI primitives and previews; lifecycle and object ownership stay in the render manager. */
final class MLuaHudObjectRenderer {
    private MLuaHudObjectRenderer() { }

    static void renderRect(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        int color = ((int) Math.round((1.0 - object.transparency()) * 255.0) << 24) | (object.color() & 0x00FFFFFF);
        graphics.fill((int) object.x(), (int) object.y(), (int) (object.x() + object.width()), (int) (object.y() + object.height()), color);
    }

    static void renderImage(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        ResourceLocation texture = MLuaClientRenderManager.resolveTexture(object.resource());
        if (texture == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(object.x(), object.y(), 0.0F);
        if (object.rotation() != 0.0) {
            graphics.pose().translate(object.width() / 2.0, object.height() / 2.0, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) object.rotation()));
            graphics.pose().translate(-object.width() / 2.0, -object.height() / 2.0, 0.0F);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, (float) (1.0 - object.transparency()));
        graphics.blit(texture, 0, 0, 0, 0, (int) object.width(), (int) object.height(), (int) object.width(), (int) object.height());
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    static void renderItem(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object, boolean block) {
        ItemStack stack = MLuaClientRenderManager.resolveStack(object.resource(), block);
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(object.x(), object.y(), 0.0F);
        float scale = (float) (Math.min(object.width(), object.height()) / 16.0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    static void renderText(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        String text = object.resource() != null ? object.resource() : "";
        int color = ((int) Math.round((1.0 - object.transparency()) * 255.0) << 24) | (object.color() & 0x00FFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(object.x(), object.y(), 0.0F);
        if (object.rotation() != 0.0) {
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) object.rotation()));
        }
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    static void renderLine2d(GuiGraphics graphics, double x1, double y1, double x2, double y2, MLuaClientRenderManager.RenderObject object) {
        int color = ((int) Math.round((1.0 - object.transparency()) * 255.0) << 24) | (object.color() & 0x00FFFFFF);
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        double thickness = Math.max(1.0, object.thickness());
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int px = (int) Math.round(x1 + dx * t);
            int py = (int) Math.round(y1 + dy * t);
            graphics.fill(px, py, px + (int) Math.round(thickness), py + (int) Math.round(thickness), color);
        }
    }

    static void renderModelPreview(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        if (object.bbmodel() != null && renderBBModel(graphics, object)) {
            return;
        }
        // The mesh vertex backend is installed incrementally per loader. Until a
        // world vertex callback is active, render a visible bounds preview in UI
        // space instead of silently dropping a loaded asset.
        double width = object.width();
        double height = object.height();
        if (object.bbmodel() != null && !object.bbmodel().cubes().isEmpty()) {
            var first = object.bbmodel().cubes().get(0);
            float minX = first.from(0), minY = first.from(1), minZ = first.from(2);
            float maxX = first.to(0), maxY = first.to(1), maxZ = first.to(2);
            for (var cube : object.bbmodel().cubes()) {
                minX = Math.min(minX, cube.from(0)); minY = Math.min(minY, cube.from(1)); minZ = Math.min(minZ, cube.from(2));
                maxX = Math.max(maxX, cube.to(0)); maxY = Math.max(maxY, cube.to(1)); maxZ = Math.max(maxZ, cube.to(2));
            }
            width = Math.max(1.0, (maxX - minX) * object.width() / 16.0);
            height = Math.max(1.0, (maxY - minY) * object.height() / 16.0);
        }
        MLuaClientRenderManager.RenderObject preview = object.copy();
        preview.setTarget(MLuaClientRenderManager.RenderTarget.HUD);
        preview.setPosition(object.x(), object.y());
        preview.setSize(width, height);
        renderRect(graphics, preview);
    }

    static boolean renderBBModel(GuiGraphics graphics, MLuaClientRenderManager.RenderObject object) {
        try {
            Object rawBuffer = graphics.getClass().getMethod("bufferSource").invoke(graphics);
            if (!(rawBuffer instanceof MultiBufferSource buffer)) return false;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(object.x(), object.y(), object.zIndex() * 0.01F);
                float scale = (float)(Math.min(object.width(), object.height()) / 16.0);
                pose.scale(scale, scale, scale);
                Float renderPartialTick = MLuaRenderCallbacks.currentClientPartialTick();
                float animationPartialTick = renderPartialTick == null ? 1.0F : renderPartialTick;
                MLuaClientRenderManager.renderBBModelWorld(pose, buffer, object, 15728880, 0, animationPartialTick);
            } finally {
                pose.popPose();
            }
            try { graphics.getClass().getMethod("flush").invoke(graphics); } catch (ReflectiveOperationException ignored) { }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
