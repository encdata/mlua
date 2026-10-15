package dev.mlua.runtime.client;

import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.TextButton;
import dev.mlua.minecraft.ui.element.TextLabel;
import dev.mlua.modules.coregui.Service.MLuaGuiService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.mlua.loaderapi.LoaderApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MLuaGuiRenderer {
    private MLuaGuiRenderer() {}

    public static void registerOverlay() {
        LoaderApi.client().registerOverlay("mlua_gui", graphics -> {
            if (!(graphics instanceof GuiGraphicsExtractor extractor)) return;
            Minecraft minecraft = Minecraft.getInstance();
            renderAll(extractor, extractor.guiWidth(), extractor.guiHeight());
        });
    }

    public static void onClientTickEnd() {
        Minecraft minecraft = Minecraft.getInstance();
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui == null) return;
        for (ScreenGui screen : gui.getScreenGuis()) {
            tickTweenTree(screen, 0.05);
        }
    }

    private static void tickTweenTree(GuiBase node, double deltaSeconds) {
        if (node instanceof GuiObject object) object.tickTween(deltaSeconds);
        for (GuiBase child : node.getChildren()) tickTweenTree(child, deltaSeconds);
    }

    public static void renderAll(Object graphics, int width, int height) {
        if (!(graphics instanceof GuiGraphicsExtractor extractor)) return;
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui == null) return;

        List<ScreenGui> screenGuis = new ArrayList<>();
        for (ScreenGui screen : gui.getScreenGuis()) {
            if (screen.isVisible() && screen.targetsScreen(Minecraft.getInstance().screen)
                && gui.isCoreGuiEnabled(screen.getCoreGuiType())) screenGuis.add(screen);
        }
        screenGuis.sort(Comparator.comparingInt(ScreenGui::getDisplayOrder));
        for (ScreenGui screen : screenGuis) renderChildren(extractor, screen, 0, 0, width, height);
    }

    public static void renderScreenLayer(Object screen, GuiGraphicsExtractor graphics, boolean before) {
        if (screen == null || graphics == null) return;
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui == null) return;
        String layer = before ? "Before" : "After";
        List<ScreenGui> screenGuis = new ArrayList<>();
        for (ScreenGui candidate : gui.getScreenGuis()) {
            if (candidate.isVisible() && candidate.targetsScreen(screen)
                && candidate.getRenderLayer().equals(layer)
                && gui.isCoreGuiEnabled(candidate.getCoreGuiType())) screenGuis.add(candidate);
        }
        screenGuis.sort(Comparator.comparingInt(ScreenGui::getDisplayOrder));
        for (ScreenGui candidate : screenGuis) {
            renderChildren(graphics, candidate, 0, 0, graphics.guiWidth(), graphics.guiHeight());
        }
    }

    private static void renderChildren(GuiGraphicsExtractor graphics, GuiBase parent,
                                      int parentX, int parentY, int parentWidth, int parentHeight) {
        List<GuiBase> children = new ArrayList<>(parent.getChildren());
        children.sort(Comparator.comparingInt(child -> child instanceof GuiObject object ? object.getZIndex() : 0));
        for (GuiBase child : children) {
            if (!(child instanceof GuiObject object) || !object.getVisible()) continue;
            int width = object.computeAbsoluteWidth(parentWidth);
            int height = object.computeAbsoluteHeight(parentHeight);
            int x = object.computeAbsoluteX(parentX, parentWidth, width);
            int y = object.computeAbsoluteY(parentY, parentHeight, height);
            float transparency = object.getBackgroundTransparency();
            if (transparency < 1.0f && width > 0 && height > 0) {
                int color = (((int) ((1.0f - transparency) * 255.0f)) << 24)
                    | (object.getBackgroundColor3() & 0x00ffffff);
                graphics.fill(x, y, x + width, y + height, color);
            }
            String text = object instanceof TextLabel label ? label.getText()
                : object instanceof TextButton button ? button.getText() : null;
            if (text != null && !text.isEmpty()) {
                int color = object instanceof TextLabel label ? label.getTextColor3()
                    : ((TextButton) object).getTextColor3();
                int baseline = y + Math.max(0, (height - Minecraft.getInstance().font.lineHeight) / 2);
                graphics.text(Minecraft.getInstance().font, text, x + 4, baseline, color);
            }
            renderChildren(graphics, object, x, y, width, height);
        }
    }
}
