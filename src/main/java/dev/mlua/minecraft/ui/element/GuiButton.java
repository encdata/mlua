package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.engine.MLuaLuaBindings;

public abstract class GuiButton extends GuiObject {
    protected MLuaSignal activated = new MLuaSignal();
    protected MLuaSignal mouseButton1Click = new MLuaSignal();
    protected MLuaSignal mouseButton1Down = new MLuaSignal();
    protected MLuaSignal mouseButton1Up = new MLuaSignal();
    protected MLuaSignal mouseButton2Click = new MLuaSignal();
    protected MLuaSignal mouseButton2Down = new MLuaSignal();
    protected MLuaSignal mouseButton2Up = new MLuaSignal();

    public MLuaSignal getActivated() { return activated; }
    public MLuaSignal getMouseButton1Click() { return mouseButton1Click; }
    public MLuaSignal getMouseButton1Down() { return mouseButton1Down; }
    public MLuaSignal getMouseButton1Up() { return mouseButton1Up; }
    public MLuaSignal getMouseButton2Click() { return mouseButton2Click; }
    public MLuaSignal getMouseButton2Down() { return mouseButton2Down; }
    public MLuaSignal getMouseButton2Up() { return mouseButton2Up; }

    @Override
    protected void writeProperties(LuaTable table) {
        super.writeProperties(table);
        table.set("Activated", MLuaLuaBindings.signalInstance(activated, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton1Click", MLuaLuaBindings.signalInstance(mouseButton1Click, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton1Down", MLuaLuaBindings.signalInstance(mouseButton1Down, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton1Up", MLuaLuaBindings.signalInstance(mouseButton1Up, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton2Click", MLuaLuaBindings.signalInstance(mouseButton2Click, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton2Down", MLuaLuaBindings.signalInstance(mouseButton2Down, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
        table.set("MouseButton2Up", MLuaLuaBindings.signalInstance(mouseButton2Up, this.ownerGlobals, this.scheduler(), this.ownerModId, this.ownerSide));
    }
}
