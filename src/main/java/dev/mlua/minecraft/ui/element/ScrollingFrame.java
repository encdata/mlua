package dev.mlua.minecraft.ui.element;

import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.data.Vector2;
import org.luaj.vm2.LuaValue;

public class ScrollingFrame extends Frame {
    private Vector2 canvasPosition = new Vector2(0, 0);
    private UDim2 canvasSize = new UDim2(new UDim(0, 0), new UDim(0, 0));
    private Vector2 absoluteCanvasSize = new Vector2(0, 0);
    private Vector2 absoluteWindowSize = new Vector2(0, 0);
    private String automaticCanvasSize = "None";
    private int scrollBarThickness = 12;
    private int scrollBarImageColor3 = 0xFF000000;
    private float scrollBarImageTransparency = 0.0f;
    private String scrollingDirection = "XY";
    private boolean scrollingEnabled = true;
    private boolean draggingVerticalThumb = false;
    private boolean draggingHorizontalThumb = false;
    private double dragOffset = 0.0;

    public Vector2 getCanvasPosition() { return canvasPosition; }
    public void setCanvasPosition(Vector2 canvasPosition) {
        this.canvasPosition = clampCanvasPosition(canvasPosition);
        syncLuaProperty("CanvasPosition", toLuaVector2(this.canvasPosition));
    }

    public UDim2 getCanvasSize() { return canvasSize; }
    public void setCanvasSize(UDim2 canvasSize) {
        this.canvasSize = canvasSize;
        syncLuaProperty("CanvasSize", toLuaUDim2(canvasSize));
    }

    public Vector2 getAbsoluteCanvasSize() { return absoluteCanvasSize; }
    public void setAbsoluteCanvasSize(Vector2 absoluteCanvasSize) {
        this.absoluteCanvasSize = absoluteCanvasSize;
        syncLuaProperty("AbsoluteCanvasSize", toLuaVector2(absoluteCanvasSize));
    }

    public Vector2 getAbsoluteWindowSize() { return absoluteWindowSize; }
    public void setAbsoluteWindowSize(Vector2 absoluteWindowSize) {
        this.absoluteWindowSize = absoluteWindowSize;
        syncLuaProperty("AbsoluteWindowSize", toLuaVector2(absoluteWindowSize));
    }

    public String getAutomaticCanvasSize() { return automaticCanvasSize; }
    public void setAutomaticCanvasSize(String automaticCanvasSize) {
        this.automaticCanvasSize = automaticCanvasSize;
        syncLuaProperty("AutomaticCanvasSize", LuaValue.valueOf(automaticCanvasSize));
    }

    public int getScrollBarThickness() { return scrollBarThickness; }
    public void setScrollBarThickness(int scrollBarThickness) {
        this.scrollBarThickness = Math.max(0, scrollBarThickness);
        syncLuaProperty("ScrollBarThickness", LuaValue.valueOf(this.scrollBarThickness));
    }

    public int getScrollBarImageColor3() { return scrollBarImageColor3; }
    public void setScrollBarImageColor3(int scrollBarImageColor3) {
        this.scrollBarImageColor3 = scrollBarImageColor3;
        syncLuaProperty("ScrollBarImageColor3", LuaValue.valueOf(scrollBarImageColor3));
    }

    public float getScrollBarImageTransparency() { return scrollBarImageTransparency; }
    public void setScrollBarImageTransparency(float scrollBarImageTransparency) {
        this.scrollBarImageTransparency = Math.max(0.0f, Math.min(1.0f, scrollBarImageTransparency));
        syncLuaProperty("ScrollBarImageTransparency", LuaValue.valueOf(this.scrollBarImageTransparency));
    }

    public String getScrollingDirection() { return scrollingDirection; }
    public void setScrollingDirection(String scrollingDirection) {
        this.scrollingDirection = scrollingDirection;
        syncLuaProperty("ScrollingDirection", LuaValue.valueOf(scrollingDirection));
    }

    public boolean getScrollingEnabled() { return scrollingEnabled; }
    public void setScrollingEnabled(boolean scrollingEnabled) {
        this.scrollingEnabled = scrollingEnabled;
        syncLuaProperty("ScrollingEnabled", LuaValue.valueOf(scrollingEnabled));
    }

    public boolean canScrollHorizontally() {
        return scrollingEnabled && ("XY".equals(scrollingDirection) || "X".equals(scrollingDirection));
    }

    public boolean canScrollVertically() {
        return scrollingEnabled && ("XY".equals(scrollingDirection) || "Y".equals(scrollingDirection));
    }

    public int getMaxScrollX() {
        return Math.max(0, (int) Math.round(absoluteCanvasSize.x() - absoluteWindowSize.x()));
    }

    public int getMaxScrollY() {
        return Math.max(0, (int) Math.round(absoluteCanvasSize.y() - absoluteWindowSize.y()));
    }

    public void scrollBy(double dx, double dy) {
        setCanvasPosition(new Vector2(canvasPosition.x() + dx, canvasPosition.y() + dy));
    }

    public void updateViewport(int frameWidth, int frameHeight) {
        int windowWidth = frameWidth;
        int windowHeight = frameHeight;

        int contentWidth = Math.max((int) Math.round(frameWidth * canvasSize.x().scale()) + canvasSize.x().offset(), computeChildrenExtentX(frameWidth, frameHeight));
        int contentHeight = Math.max((int) Math.round(frameHeight * canvasSize.y().scale()) + canvasSize.y().offset(), computeChildrenExtentY(frameWidth, frameHeight));

        boolean needsVertical = canScrollVertically() && contentHeight > windowHeight;
        boolean needsHorizontal = canScrollHorizontally() && contentWidth > windowWidth;

        if (needsVertical) {
            windowWidth = Math.max(0, windowWidth - scrollBarThickness);
        }
        if (needsHorizontal) {
            windowHeight = Math.max(0, windowHeight - scrollBarThickness);
        }

        if (!needsVertical && canScrollVertically() && contentHeight > windowHeight) {
            needsVertical = true;
            windowWidth = Math.max(0, windowWidth - scrollBarThickness);
        }
        if (!needsHorizontal && canScrollHorizontally() && contentWidth > windowWidth) {
            needsHorizontal = true;
            windowHeight = Math.max(0, windowHeight - scrollBarThickness);
        }

        setAbsoluteWindowSize(new Vector2(windowWidth, windowHeight));
        setAbsoluteCanvasSize(new Vector2(Math.max(contentWidth, windowWidth), Math.max(contentHeight, windowHeight)));
        setCanvasPosition(canvasPosition);
    }

    public boolean isDraggingVerticalThumb() { return draggingVerticalThumb; }
    public void setDraggingVerticalThumb(boolean draggingVerticalThumb) { this.draggingVerticalThumb = draggingVerticalThumb; }
    public boolean isDraggingHorizontalThumb() { return draggingHorizontalThumb; }
    public void setDraggingHorizontalThumb(boolean draggingHorizontalThumb) { this.draggingHorizontalThumb = draggingHorizontalThumb; }
    public double getDragOffset() { return dragOffset; }
    public void setDragOffset(double dragOffset) { this.dragOffset = dragOffset; }

    public Vector2 clampCanvasPosition(Vector2 desired) {
        double x = canScrollHorizontally() ? Math.max(0, Math.min(desired.x(), getMaxScrollX())) : 0;
        double y = canScrollVertically() ? Math.max(0, Math.min(desired.y(), getMaxScrollY())) : 0;
        return new Vector2(x, y);
    }

    private int computeChildrenExtentX(int frameWidth, int frameHeight) {
        if (!"X".equals(automaticCanvasSize) && !"XY".equals(automaticCanvasSize)) {
            return 0;
        }
        int max = 0;
        for (GuiBase child : getChildren()) {
            if (child instanceof GuiObject go && go.getVisible()) {
                int childWidth = go.computeAbsoluteWidth(frameWidth);
                int childX = go.computeAbsoluteX(0, frameWidth, childWidth);
                max = Math.max(max, childX + childWidth);
            }
        }
        return max;
    }

    private int computeChildrenExtentY(int frameWidth, int frameHeight) {
        if (!"Y".equals(automaticCanvasSize) && !"XY".equals(automaticCanvasSize)) {
            return 0;
        }
        int max = 0;
        for (GuiBase child : getChildren()) {
            if (child instanceof GuiObject go && go.getVisible()) {
                int childHeight = go.computeAbsoluteHeight(frameHeight);
                int childY = go.computeAbsoluteY(0, frameHeight, childHeight);
                max = Math.max(max, childY + childHeight);
            }
        }
        return max;
    }
}
