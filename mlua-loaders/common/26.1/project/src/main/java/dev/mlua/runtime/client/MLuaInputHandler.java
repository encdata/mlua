package dev.mlua.runtime.client;

import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.modules.coregui.service.MLuaGuiService;
import dev.mlua.modules.modal.service.MLuaModalService;
import dev.mlua.minecraft.ui.element.GuiBase;
import dev.mlua.minecraft.ui.element.GuiObject;
import dev.mlua.minecraft.ui.element.ScreenGui;
import dev.mlua.minecraft.ui.element.ScrollingFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaValue;
import org.lwjgl.glfw.GLFW;

public final class MLuaInputHandler {
    private static boolean wasModal = false;

    private MLuaInputHandler() {
    }

    public static void registerHandlers() {
        LoaderApi.client().registerInputHandlers();
    }

    public static boolean handleMouseButton(int button, int action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.screen != null) return false;

        boolean modal = MLuaModalService.isGlobalModal();
        if (!modal && action != 1) return false;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

        List<ScreenGui> guis = collectGuis();

        guis.sort((a, b) -> Integer.compare(b.getDisplayOrder(), a.getDisplayOrder()));

        for (ScreenGui sg : guis) {
            if (sg.isVisible()) {
                if (processClick(sg, mouseX, mouseY, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), button)) {
                    return true;
                }
            }
        }

        return modal;
    }

    public static boolean handleMouseScroll(double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null || mc.screen != null) return false;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

        List<ScreenGui> guis = collectGuis();

        guis.sort((a, b) -> Integer.compare(b.getDisplayOrder(), a.getDisplayOrder()));
        for (ScreenGui sg : guis) {
            if (sg.isVisible() && processScroll(sg, mouseX, mouseY, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), delta)) {
                return true;
            }
        }
        return false;
    }

    public static void handleClientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;

        boolean modal = MLuaModalService.isGlobalModal();
        if (modal && mc.screen == null) {
            long window = mc.getWindow().handle();
            boolean cursorDisabled = GLFW.glfwGetInputMode(window, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED;

            if (!wasModal || cursorDisabled) {
                mc.mouseHandler.releaseMouse();
                GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            }
        }
        wasModal = modal;

        if (mc.player == null || mc.screen != null) return;

        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getHeight();

        for (ScreenGui sg : collectGuis()) {
            if (sg.isVisible()) {
                checkHoverTree(sg, mouseX, mouseY, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            }
        }
    }

    private static List<ScreenGui> collectGuis() {
        List<ScreenGui> guis = new ArrayList<>();
        MLuaGuiService gui = MLuaGuiService.getInstance();
        if (gui != null) {
            for (ScreenGui sg : gui.getScreenGuis()) {
                if (gui.isCoreGuiEnabled(sg.getCoreGuiType())) guis.add(sg);
            }
        }
        return guis;
    }

    private static boolean processClick(GuiBase node, double mouseX, double mouseY, int parentAbsX, int parentAbsY, int parentAbsW, int parentAbsH, int button) {
        List<GuiBase> children = new ArrayList<>(node.getChildren());
        children.sort((a, b) -> Integer.compare((b instanceof GuiObject go) ? go.getZIndex() : 0, (a instanceof GuiObject go) ? go.getZIndex() : 0));

        for (GuiBase child : children) {
            if (child instanceof GuiObject go && go.getVisible()) {
                int absW = go.computeAbsoluteWidth(parentAbsW);
                int absH = go.computeAbsoluteHeight(parentAbsH);
                int absX = go.computeAbsoluteX(parentAbsX, parentAbsW, absW);
                int absY = go.computeAbsoluteY(parentAbsY, parentAbsH, absH);
                int childParentX = absX;
                int childParentY = absY;
                int childParentW = absW;
                int childParentH = absH;
                boolean hitSelf = mouseX >= absX && mouseX <= absX + absW && mouseY >= absY && mouseY <= absY + absH;

                if (go instanceof ScrollingFrame scrollingFrame) {
                    scrollingFrame.updateViewport(absW, absH);
                    int windowW = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().x());
                    int windowH = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().y());
                    hitSelf = mouseX >= absX && mouseX <= absX + windowW && mouseY >= absY && mouseY <= absY + windowH;
                    childParentX = absX - (int) Math.round(scrollingFrame.getCanvasPosition().x());
                    childParentY = absY - (int) Math.round(scrollingFrame.getCanvasPosition().y());
                    childParentW = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().x());
                    childParentH = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().y());
                }

                if (hitSelf && processClick(go, mouseX, mouseY, childParentX, childParentY, childParentW, childParentH, button)) return true;

                if (hitSelf) {
                    if (go instanceof dev.mlua.minecraft.ui.element.GuiButton btn) {
                        if (button == 0) {
                            btn.getMouseButton1Down().fire(LuaValue.NONE);
                            btn.getMouseButton1Click().fire(LuaValue.NONE);
                            btn.getActivated().fire(LuaValue.NONE);
                        } else if (button == 1) {
                            btn.getMouseButton2Down().fire(LuaValue.NONE);
                            btn.getMouseButton2Click().fire(LuaValue.NONE);
                        }
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkHoverTree(GuiBase node, double mouseX, double mouseY, int parentAbsX, int parentAbsY, int parentAbsW, int parentAbsH) {
        for (GuiBase child : node.getChildren()) {
            if (child instanceof GuiObject go && go.getVisible()) {
                int absW = go.computeAbsoluteWidth(parentAbsW);
                int absH = go.computeAbsoluteHeight(parentAbsH);
                int absX = go.computeAbsoluteX(parentAbsX, parentAbsW, absW);
                int absY = go.computeAbsoluteY(parentAbsY, parentAbsH, absH);
                int hoverW = absW;
                int hoverH = absH;
                int childParentX = absX;
                int childParentY = absY;
                int childParentW = absW;
                int childParentH = absH;

                if (go instanceof ScrollingFrame scrollingFrame) {
                    scrollingFrame.updateViewport(absW, absH);
                    hoverW = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().x());
                    hoverH = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().y());
                    childParentX = absX - (int) Math.round(scrollingFrame.getCanvasPosition().x());
                    childParentY = absY - (int) Math.round(scrollingFrame.getCanvasPosition().y());
                    childParentW = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().x());
                    childParentH = (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().y());
                }

                boolean isHovered = mouseX >= absX && mouseX <= absX + hoverW && mouseY >= absY && mouseY <= absY + hoverH;

                if (isHovered && !go.isHovered()) {
                    go.setHovered(true);
                    go.getMouseEnter().fire(LuaValue.NONE);
                } else if (!isHovered && go.isHovered()) {
                    go.setHovered(false);
                    go.getMouseLeave().fire(LuaValue.NONE);
                }

                if (isHovered || !(go instanceof ScrollingFrame)) {
                    checkHoverTree(go, mouseX, mouseY, childParentX, childParentY, childParentW, childParentH);
                }
            }
        }
    }

    private static boolean processScroll(GuiBase node, double mouseX, double mouseY, int parentAbsX, int parentAbsY, int parentAbsW, int parentAbsH, double delta) {
        List<GuiBase> children = new ArrayList<>(node.getChildren());
        children.sort((a, b) -> Integer.compare((b instanceof GuiObject go) ? go.getZIndex() : 0, (a instanceof GuiObject go) ? go.getZIndex() : 0));
        for (GuiBase child : children) {
            if (!(child instanceof GuiObject go) || !go.getVisible()) {
                continue;
            }
            int absW = go.computeAbsoluteWidth(parentAbsW);
            int absH = go.computeAbsoluteHeight(parentAbsH);
            int absX = go.computeAbsoluteX(parentAbsX, parentAbsW, absW);
            int absY = go.computeAbsoluteY(parentAbsY, parentAbsH, absH);

            if (go instanceof ScrollingFrame scrollingFrame) {
                scrollingFrame.updateViewport(absW, absH);
                int windowW = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().x());
                int windowH = (int) Math.round(scrollingFrame.getAbsoluteWindowSize().y());
                boolean hit = mouseX >= absX && mouseX <= absX + windowW && mouseY >= absY && mouseY <= absY + windowH;
                if (!hit) {
                    continue;
                }

                if (processScroll(go, mouseX, mouseY,
                    absX - (int) Math.round(scrollingFrame.getCanvasPosition().x()),
                    absY - (int) Math.round(scrollingFrame.getCanvasPosition().y()),
                    (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().x()),
                    (int) Math.round(scrollingFrame.getAbsoluteCanvasSize().y()),
                    delta)) {
                    return true;
                }

                double amount = -delta * 24.0;
                if (scrollingFrame.canScrollVertically() && scrollingFrame.getMaxScrollY() > 0) {
                    scrollingFrame.scrollBy(0, amount);
                    return true;
                }
                if (scrollingFrame.canScrollHorizontally() && scrollingFrame.getMaxScrollX() > 0) {
                    scrollingFrame.scrollBy(amount, 0);
                    return true;
                }
            } else if (mouseX >= absX && mouseX <= absX + absW && mouseY >= absY && mouseY <= absY + absH) {
                if (processScroll(go, mouseX, mouseY, absX, absY, absW, absH, delta)) {
                    return true;
                }
            }
        }
        return false;
    }
}
