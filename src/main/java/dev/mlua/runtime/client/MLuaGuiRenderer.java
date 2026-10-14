package dev.mlua.runtime.client;

import com.mojang.math.Axis;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ImageButton;
import dev.mlua.minecraft.ui.element.ImageLabel;
import dev.mlua.minecraft.ui.element.ScrollingFrame;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.TextButton;
import dev.mlua.minecraft.ui.element.TextLabel;
import dev.mlua.modules.coregui.Service.MLuaGuiService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class MLuaGuiRenderer {
    private MLuaGuiRenderer() {
    }

    public static void registerOverlay() {
        LoaderApi.client().registerOverlay("mlua_gui", obj -> {
            GuiGraphics graphics = (GuiGraphics) obj;
            Minecraft mc = Minecraft.getInstance();
            renderAll(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        });
    }

    public static void renderAll(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        renderMatching(graphics, screenWidth, screenHeight, null, "Before");
        renderMatching(graphics, screenWidth, screenHeight, null, "After");
    }

    /** Renders addon screens on a native screen's requested before/after layer. */
    public static void renderScreenLayer(Object screen, GuiGraphics graphics, boolean before) {
        if (screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        String layer = before ? "Before" : "After";
        renderMatching(graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), screen, layer);
    }

    private static void renderMatching(GuiGraphics graphics, int screenWidth, int screenHeight, Object screen, String layer) {
        List<ScreenGui> screenGuis = new ArrayList<>();
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui != null && gui.isEnabled()) {
            for (ScreenGui sg : gui.getScreenGuis()) {
                if (sg.isVisible() && sg.targetsScreen(screen) && sg.getRenderLayer().equals(layer)
                    && gui.isCoreGuiEnabled(sg.getCoreGuiType())) screenGuis.add(sg);
            }
        }

        screenGuis.sort(Comparator.comparingInt(ScreenGui::getDisplayOrder));

        for (ScreenGui sg : screenGuis) {
            renderScreenGui(sg, graphics, screenWidth, screenHeight);
        }
    }

    private static void renderScreenGui(ScreenGui sg, GuiGraphics graphics, int screenW, int screenH) {
        List<GuiBase> children = new ArrayList<>(sg.getChildren());
        children.sort(Comparator.comparingInt((GuiBase child) -> child instanceof GuiObject go ? go.getZIndex() : 0)
            .thenComparingInt(child -> child instanceof GuiObject go ? go.getLayoutOrder() : 0));
        for (GuiBase child : children) {
            if (child instanceof GuiObject go && go.getVisible()) {
                renderGuiObject(go, graphics, 0, 0, screenW, screenH);
            }
        }
    }

    private static void renderGuiObject(GuiObject obj, GuiGraphics graphics, int parentAbsX, int parentAbsY, int parentAbsW, int parentAbsH) {
        int absW = obj.computeAbsoluteWidth(parentAbsW);
        int absH = obj.computeAbsoluteHeight(parentAbsH);
        int absX = obj.computeAbsoluteX(parentAbsX, parentAbsW, absW);
        int absY = obj.computeAbsoluteY(parentAbsY, parentAbsH, absH);

        graphics.pose().pushPose();
        graphics.pose().translate(absX, absY, 0);

        if (obj.getRotation() != 0) {
            graphics.pose().translate(absW / 2.0, absH / 2.0, 0);
            graphics.pose().mulPose(Axis.ZP.rotationDegrees(obj.getRotation()));
            graphics.pose().translate(-absW / 2.0, -absH / 2.0, 0);
        }

        float bgTrans = obj.getBackgroundTransparency();
        if (bgTrans < 1.0f) {
            int alpha = (int)((1.0f - bgTrans) * 255);
            int baseColor = obj.getBackgroundColor3();
            
            if (obj.isHovered() && (obj instanceof dev.mlua.minecraft.ui.element.GuiButton)) {
                baseColor = darkenColor(baseColor, 0.8f);
            }

            int color = (alpha << 24) | (baseColor & 0x00FFFFFF);
            graphics.fill(0, 0, absW, absH, color);
        }

        if (obj instanceof TextLabel label) {
            drawText(graphics, label.getText(), label.getTextColor3(), label.getTextTransparency(), label.getTextShadow(), absW, absH, label.getTextSize(), label.getTextXAlignment(), label.getTextYAlignment(), label.getTextScaled(), label.getTextWrapped(), label::setTextFits);
        } else if (obj instanceof TextButton button) {
            drawText(graphics, button.getText(), button.getTextColor3(), button.getTextTransparency(), button.getTextShadow(), absW, absH, button.getTextSize(), button.getTextXAlignment(), button.getTextYAlignment(), button.getTextScaled(), button.getTextWrapped(), button::setTextFits);
        } else if (obj instanceof ImageLabel label) {
            drawImage(graphics, label.getImage(), label.getImageColor3(), label.getImageTransparency(), absW, absH);
        } else if (obj instanceof ImageButton button) {
            drawImage(graphics, button.getImage(), button.getImageColor3(), button.getImageTransparency(), absW, absH);
        }

        int bp = obj.getBorderSizePixel();
        if (bp > 0) {
            int borderColor = obj.getBorderColor3();
            graphics.fill(0, 0, absW, bp, borderColor);
            graphics.fill(0, absH - bp, absW, absH, borderColor);
            graphics.fill(0, 0, bp, absH, borderColor);
            graphics.fill(absW - bp, 0, absW, absH, borderColor);
        }
        
        graphics.pose().popPose();

        List<GuiBase> children = sortedChildren(obj);
        if (obj instanceof ScrollingFrame scrollingFrame) {
            scrollingFrame.updateViewport(absW, absH);
            int windowW = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().x());
            int windowH = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().y());
            int canvasW = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().x());
            int canvasH = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().y());
            int offsetX = (int) Math.round(scrollingFrame.getCanvasPosition().x());
            int offsetY = (int) Math.round(scrollingFrame.getCanvasPosition().y());

            if (windowW > 0 && windowH > 0) {
                graphics.enableScissor(absX, absY, absX + windowW, absY + windowH);
                for (GuiBase child : children) {
                    if (child instanceof GuiObject go && go.getVisible()) {
                        renderGuiObject(go, graphics, absX - offsetX, absY - offsetY, canvasW, canvasH);
                    }
                }
                graphics.disableScissor();
            }
            renderScrollBars(scrollingFrame, graphics, absX, absY, absW, absH);
            return;
        }

        boolean clipsDescendants = obj.getClipsDescendants();
        if (clipsDescendants) graphics.enableScissor(absX, absY, absX + absW, absY + absH);
        try {
            for (GuiBase child : children) {
                if (child instanceof GuiObject go && go.getVisible()) {
                    renderGuiObject(go, graphics, absX, absY, absW, absH);
                }
            }
        } finally {
            if (clipsDescendants) graphics.disableScissor();
        }
    }

    private static List<GuiBase> sortedChildren(GuiBase parent) {
        List<GuiBase> children = new ArrayList<>(parent.getChildren());
        children.sort(Comparator.comparingInt((GuiBase child) -> child instanceof GuiObject go ? go.getZIndex() : 0)
            .thenComparingInt(child -> child instanceof GuiObject go ? go.getLayoutOrder() : 0));
        return children;
    }

    private static void renderScrollBars(ScrollingFrame frame, GuiGraphics graphics, int absX, int absY, int absW, int absH) {
        int thickness = frame.getScrollBarThickness();
        if (thickness <= 0 || !frame.getScrollingEnabled()) {
            return;
        }

        int alpha = (int) ((1.0f - frame.getScrollBarImageTransparency()) * 255.0f);
        int color = (alpha << 24);
        int windowW = (int) Math.round(frame.getAbsoluteWindowSize().x());
        int windowH = (int) Math.round(frame.getAbsoluteWindowSize().y());
        int canvasW = (int) Math.round(frame.getAbsoluteCanvasSize().x());
        int canvasH = (int) Math.round(frame.getAbsoluteCanvasSize().y());

        if (frame.canScrollVertically() && frame.getMaxScrollY() > 0 && windowH > 0) {
            int thumbHeight = Math.max(20, Math.round((windowH * (float) windowH) / Math.max(1, canvasH)));
            thumbHeight = Math.min(windowH, thumbHeight);
            int trackHeight = Math.max(1, windowH - thumbHeight);
            int thumbY = absY + Math.round(trackHeight * ((float) frame.getCanvasPosition().y() / Math.max(1, frame.getMaxScrollY())));
            int thumbX = absX + windowW;
            graphics.fill(thumbX, thumbY, thumbX + thickness, thumbY + thumbHeight, color);
        }

        if (frame.canScrollHorizontally() && frame.getMaxScrollX() > 0 && windowW > 0) {
            int thumbWidth = Math.max(20, Math.round((windowW * (float) windowW) / Math.max(1, canvasW)));
            thumbWidth = Math.min(windowW, thumbWidth);
            int trackWidth = Math.max(1, windowW - thumbWidth);
            int thumbX = absX + Math.round(trackWidth * ((float) frame.getCanvasPosition().x() / Math.max(1, frame.getMaxScrollX())));
            int thumbY = absY + windowH;
            graphics.fill(thumbX, thumbY, thumbX + thumbWidth, thumbY + thickness, color);
        }
    }

    private static int darkenColor(int color, float factor) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return ((int)(r * factor) << 16) | ((int)(g * factor) << 8) | (int)(b * factor);
    }

    private static void drawImage(GuiGraphics graphics, String imagePath, int color, float transparency, int boxW, int boxH) {
        if (imagePath == null || imagePath.isEmpty()) return;
        ResourceLocation loc = MLuaResourceLocations.parse(imagePath.contains(":") ? imagePath : "mlua:" + imagePath);
        
        if (Minecraft.getInstance().getResourceManager().getResource(loc).isEmpty()) {
            return;
        }

        int alpha = (int)((1.0f - transparency) * 255);
        float red = ((color >> 16) & 0xFF) / 255.0f;
        float green = ((color >> 8) & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        graphics.setColor(red, green, blue, (float)alpha / 255.0f);
        graphics.blit(loc, 0, 0, 0, 0, boxW, boxH, boxW, boxH);
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void drawText(GuiGraphics graphics, String text, int color, float textTransparency, boolean textShadow, int boxW, int boxH, int textSize, String xAlign, String yAlign, boolean textScaled, boolean textWrapped, java.util.function.Consumer<Boolean> textFitsSink) {
        int alpha = (int)Math.round(((color >>> 24) & 0xFF) * (1.0f - Math.max(0.0f, Math.min(1.0f, textTransparency))));
        color = (alpha << 24) | (color & 0x00FFFFFF);
        var font = Minecraft.getInstance().font;
        int padX = 4;
        int padY = 2;
        int contentW = Math.max(0, boxW - padX * 2);
        int contentH = Math.max(0, boxH - padY * 2);
        List<String> lines;
        float baseScale = textSize / 9.0f;
        if (textWrapped) {
            lines = new ArrayList<>();
            String[] words = text.split(" ");
            String currentLine = "";
            for (String word : words) {
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (font.width(candidate) <= contentW / baseScale) currentLine = candidate;
                else { lines.add(currentLine); currentLine = word; }
            }
            lines.add(currentLine);
        } else lines = List.of(text);
        float maxLineWidth = 0;
        for (String line : lines) maxLineWidth = Math.max(maxLineWidth, font.width(line));
        float totalLineHeight = lines.size() * font.lineHeight;
        float scale = baseScale;
        if (textScaled) {
            float widthScale = contentW / Math.max(1.0f, maxLineWidth);
            float heightScale = contentH / Math.max(1.0f, totalLineHeight);
            scale = Math.min(widthScale, heightScale);
        }
        float localContentW = contentW / scale;
        float localContentH = contentH / scale;
        int lineHeight = font.lineHeight;
        float startY = switch (yAlign) {
            case "Bottom" -> localContentH - totalLineHeight;
            case "Center" -> ((localContentH - totalLineHeight) / 2.0f) - 1.0f;
            default -> 0.0f;
        };
        graphics.pose().pushPose();
        graphics.pose().translate(padX, padY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            float x = switch (xAlign) {
                case "Right" -> localContentW - font.width(line);
                case "Center" -> (localContentW - font.width(line)) / 2.0f;
                default -> 0.0f;
            };
            graphics.drawString(font, line, Math.round(x), Math.round(startY + i * lineHeight), color, textShadow);
        }
        graphics.pose().popPose();
        textFitsSink.accept(maxLineWidth * scale <= contentW && totalLineHeight * scale <= contentH);
    }

    public static void onClientTickEnd() {
        double dt = 0.05;
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui != null) for (ScreenGui sg : gui.getScreenGuis()) tickTweenTree(sg, dt);
    }

    private static void tickTweenTree(GuiBase node, double dt) {
        if (node instanceof GuiObject go) go.tickTween(dt);
        for (GuiBase child : node.getChildren()) tickTweenTree(child, dt);
    }
}
