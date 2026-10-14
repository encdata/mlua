package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.UDim;
import dev.mlua.minecraft.ui.data.Vector2;
import dev.mlua.minecraft.ui.data.Rect;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.engine.MLuaLuaBindings;

public abstract class GuiObject extends GuiBase2d {
    protected boolean visible = true;
    protected boolean hovered = false;
    protected UDim2 position = new UDim2(new UDim(0, 0), new UDim(0, 0));
    protected UDim2 size = new UDim2(new UDim(0, 100), new UDim(0, 100));
    protected int backgroundColor3 = 0xFFFFFFFF;

    protected boolean active = true;
    protected Vector2 anchorPoint = new Vector2(0, 0);
    protected float backgroundTransparency = 0.0f;
    protected int borderColor3 = 0xFF000000;
    protected int borderSizePixel = 1;
    protected boolean clipsDescendants = false;
    protected boolean draggable = false;
    protected boolean interactable = true;
    protected int layoutOrder = 0;
    protected float rotation = 0.0f;
    protected boolean selectable = true;
    protected int selectionOrder = 0;
    protected int zIndex = 0;

    protected GuiObject nextSelectionDown, nextSelectionLeft, nextSelectionRight, nextSelectionUp;
    protected GuiObject selectionImageObject;

    protected String automaticSize = "None";
    protected String borderMode = "Outline";
    protected String guiState = "Idle";
    protected String sizeConstraint = "RelativeXY";

    protected final MLuaSignal inputBegan = new MLuaSignal();
    protected final MLuaSignal inputChanged = new MLuaSignal();
    protected final MLuaSignal inputEnded = new MLuaSignal();
    protected final MLuaSignal mouseWheelForward = new MLuaSignal();
    protected final MLuaSignal mouseWheelBackward = new MLuaSignal();

    protected MLuaSignal mouseEnter = new MLuaSignal();
    protected MLuaSignal mouseLeave = new MLuaSignal();
    protected MLuaSignal mouseMoved = new MLuaSignal();

    protected GuiObjectTween activeTween;

    public MLuaSignal getMouseEnter() { return mouseEnter; }
    public MLuaSignal getMouseLeave() { return mouseLeave; }
    public MLuaSignal getMouseMoved() { return mouseMoved; }
    public MLuaSignal getInputBegan() { return inputBegan; }
    public MLuaSignal getInputChanged() { return inputChanged; }
    public MLuaSignal getInputEnded() { return inputEnded; }
    public MLuaSignal getMouseWheelForward() { return mouseWheelForward; }
    public MLuaSignal getMouseWheelBackward() { return mouseWheelBackward; }

    public void setVisible(boolean visible) { this.visible = visible; syncLuaProperty("Visible", LuaValue.valueOf(visible)); }
    public void setPosition(UDim2 position) { this.position = position; syncLuaProperty("Position", toLuaUDim2(position)); }
    public void setSize(UDim2 size) { this.size = size; syncLuaProperty("Size", toLuaUDim2(size)); }
    public void setBackgroundColor3(int color) { this.backgroundColor3 = color; syncLuaProperty("BackgroundColor3", LuaValue.valueOf(color)); }
    public void setActive(boolean active) { this.active = active; syncLuaProperty("Active", LuaValue.valueOf(active)); }
    public void setAnchorPoint(Vector2 anchorPoint) { this.anchorPoint = anchorPoint; syncLuaProperty("AnchorPoint", toLuaVector2(anchorPoint)); }
    public void setAutomaticSize(String automaticSize) { this.automaticSize = automaticSize; syncLuaProperty("AutomaticSize", LuaValue.valueOf(automaticSize)); }
    public void setBackgroundTransparency(float t) { this.backgroundTransparency = t; syncLuaProperty("BackgroundTransparency", LuaValue.valueOf(t)); }
    public void setBorderColor3(int color) { this.borderColor3 = color; syncLuaProperty("BorderColor3", LuaValue.valueOf(color)); }
    public void setBorderMode(String mode) { this.borderMode = mode; syncLuaProperty("BorderMode", LuaValue.valueOf(mode)); }
    public void setBorderSizePixel(int px) { this.borderSizePixel = px; syncLuaProperty("BorderSizePixel", LuaValue.valueOf(px)); }
    public void setClipsDescendants(boolean clips) { this.clipsDescendants = clips; syncLuaProperty("ClipsDescendants", LuaValue.valueOf(clips)); }
    public void setDraggable(boolean d) { this.draggable = d; syncLuaProperty("Draggable", LuaValue.valueOf(d)); }
    public void setInteractable(boolean i) { this.interactable = i; syncLuaProperty("Interactable", LuaValue.valueOf(i)); }
    public void setLayoutOrder(int order) { this.layoutOrder = order; syncLuaProperty("LayoutOrder", LuaValue.valueOf(order)); }
    public void setRotation(float r) { this.rotation = r; syncLuaProperty("Rotation", LuaValue.valueOf(r)); }
    public void setSelectable(boolean s) { this.selectable = s; syncLuaProperty("Selectable", LuaValue.valueOf(s)); }
    public void setSelectionOrder(int order) { this.selectionOrder = order; syncLuaProperty("SelectionOrder", LuaValue.valueOf(order)); }
    public void setSizeConstraint(String c) { this.sizeConstraint = c; syncLuaProperty("SizeConstraint", LuaValue.valueOf(c)); }
    public void setZIndex(int z) { this.zIndex = z; syncLuaProperty("ZIndex", LuaValue.valueOf(z)); }
    public void setGuiState(String state) { this.guiState = state; syncLuaProperty("GuiState", LuaValue.valueOf(state)); }

    public int getZIndex() { return zIndex; }
    public int getLayoutOrder() { return layoutOrder; }
    public boolean getVisible() { return visible; }
    public boolean isHovered() { return hovered; }
    public boolean isActive() { return active; }
    public boolean isInteractable() { return interactable; }
    public boolean getClipsDescendants() { return clipsDescendants; }
    public void setHovered(boolean hovered) { this.hovered = hovered; }
    public float getBackgroundTransparency() { return backgroundTransparency; }
    public int getBackgroundColor3() { return backgroundColor3; }
    public int getBorderSizePixel() { return borderSizePixel; }
    public int getBorderColor3() { return borderColor3; }
    public float getRotation() { return rotation; }
    public UDim2 getPosition() { return position; }
    public UDim2 getSize() { return size; }
    public Vector2 getAnchorPoint() { return anchorPoint; }

    @Override
    protected void writeProperties(LuaTable table) {
        super.writeProperties(table);
        table.set("InputBegan", MLuaLuaBindings.signalInstance(inputBegan, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("InputChanged", MLuaLuaBindings.signalInstance(inputChanged, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("InputEnded", MLuaLuaBindings.signalInstance(inputEnded, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseWheelForward", MLuaLuaBindings.signalInstance(mouseWheelForward, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseWheelBackward", MLuaLuaBindings.signalInstance(mouseWheelBackward, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseEnter", MLuaLuaBindings.signalInstance(mouseEnter, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseLeave", MLuaLuaBindings.signalInstance(mouseLeave, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseMoved", MLuaLuaBindings.signalInstance(mouseMoved, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
    }

    // --- Absolute position / size computation ---
    public int computeAbsoluteWidth(int parentW) {
        return (int)(parentW * size.x().scale()) + size.x().offset();
    }

    public int computeAbsoluteHeight(int parentH) {
        return (int)(parentH * size.y().scale()) + size.y().offset();
    }

    public int computeAbsoluteX(int parentX, int parentW, int absoluteW) {
        int anchorTargetX = parentX + (int)(parentW * position.x().scale()) + position.x().offset();
        return anchorTargetX - (int)(anchorPoint.x() * absoluteW);
    }

    public int computeAbsoluteY(int parentY, int parentH, int absoluteH) {
        int anchorTargetY = parentY + (int)(parentH * position.y().scale()) + position.y().offset();
        return anchorTargetY - (int)(anchorPoint.y() * absoluteH);
    }

    // --- Tween methods ---
    public boolean tweenPosition(UDim2 endPos, String easingDir, String easingStyle, double time, boolean override, String callback) {
        if (!override && activeTween != null) return false;
        activeTween = new GuiObjectTween(this, endPos, null, easingDir, easingStyle, time, callback);
        return true;
    }

    public boolean tweenSize(UDim2 endSize, String easingDir, String easingStyle, double time, boolean override, String callback) {
        if (!override && activeTween != null) return false;
        activeTween = new GuiObjectTween(this, null, endSize, easingDir, easingStyle, time, callback);
        return true;
    }

    public boolean tweenSizeAndPosition(UDim2 endSize, UDim2 endPos, String easingDir, String easingStyle, double time, boolean override, String callback) {
        if (!override && activeTween != null) return false;
        activeTween = new GuiObjectTween(this, endPos, endSize, easingDir, easingStyle, time, callback);
        return true;
    }

    public void tickTween(double dt) {
        if (activeTween != null) {
            activeTween.tick(dt);
            if (activeTween.done) activeTween = null;
        }
    }

    // --- Internal tween record ---
    public static class GuiObjectTween {
        public final GuiObject target;
        public final UDim2 startPos;
        public final UDim2 startSize;
        public final UDim2 endPos;
        public final UDim2 endSize;
        public final String easingDir;
        public final String easingStyle;
        public final double duration;
        public final String callback;
        public double elapsed;
        public boolean done;

        GuiObjectTween(GuiObject target, UDim2 endPos, UDim2 endSize, String easingDir, String easingStyle, double duration, String callback) {
            this.target = target;
            this.startPos = target.position;
            this.startSize = target.size;
            this.endPos = endPos;
            this.endSize = endSize;
            this.easingDir = easingDir;
            this.easingStyle = easingStyle;
            this.duration = duration;
            this.callback = callback;
            this.elapsed = 0;
            this.done = false;
        }

        void tick(double dt) {
            if (done) return;
            elapsed += dt;
            double t = Math.min(elapsed / duration, 1.0);
            double e = ease(t, easingStyle, easingDir);

            if (endPos != null) {
                UDim x = lerpUDim(startPos.x(), endPos.x(), e);
                UDim y = lerpUDim(startPos.y(), endPos.y(), e);
                target.position = new UDim2(x, y);
                target.syncLuaProperty("Position", toLuaUDim2(target.position));
            }
            if (endSize != null) {
                UDim x = lerpUDim(startSize.x(), endSize.x(), e);
                UDim y = lerpUDim(startSize.y(), endSize.y(), e);
                target.size = new UDim2(x, y);
                target.syncLuaProperty("Size", toLuaUDim2(target.size));
            }

            if (t >= 1.0) {
                done = true;
            }
        }

        private static UDim lerpUDim(UDim a, UDim b, double t) {
            return new UDim(a.scale() + (b.scale() - a.scale()) * t, a.offset() + (int)((b.offset() - a.offset()) * t));
        }

        private static double ease(double t, String style, String dir) {
            boolean out = "Out".equals(dir);
            boolean inOut = "InOut".equals(dir);
            switch (style) {
                case "Linear": return t;
                case "Quad": return inOut ? (t < 0.5 ? 2*t*t : 1-Math.pow(-2*t+2,2)/2) : out ? t*(2-t) : t*t;
                case "Cubic": return inOut ? (t < 0.5 ? 4*t*t*t : 1-Math.pow(-2*t+2,3)/2) : out ? 1-Math.pow(1-t,3) : t*t*t;
                case "Sine": return inOut ? (1-Math.cos(Math.PI*t))/2 : out ? Math.sin(Math.PI*t/2) : 1-Math.cos(Math.PI*t/2);
                default: return t;
            }
        }
    }

    protected static LuaTable toLuaVector2(Vector2 v) {
        LuaTable t = new LuaTable();
        t.set("X", v.x());
        t.set("Y", v.y());
        return t;
    }

    protected static LuaTable toLuaRect(Rect rect) {
        LuaTable t = new LuaTable();
        t.set("X", rect.x());
        t.set("Y", rect.y());
        t.set("Width", rect.width());
        t.set("Height", rect.height());
        return t;
    }

    protected static LuaTable toLuaUDim2(UDim2 udim2) {
        LuaTable t = new LuaTable();
        t.set("X", toLuaUDim(udim2.x()));
        t.set("Y", toLuaUDim(udim2.y()));
        return t;
    }

    private static LuaTable toLuaUDim(UDim udim) {
        LuaTable t = new LuaTable();
        t.set("Scale", udim.scale());
        t.set("Offset", udim.offset());
        return t;
    }
}
