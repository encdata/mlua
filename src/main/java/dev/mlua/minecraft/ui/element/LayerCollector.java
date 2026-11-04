package dev.mlua.minecraft.ui.element;

public abstract class LayerCollector extends GuiBase2d {
    protected boolean enabled = true;
    protected boolean resetOnSpawn = true;
    protected String zIndexBehavior = "Sibling";

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setResetOnSpawn(boolean reset) { this.resetOnSpawn = reset; }
    public void setZIndexBehavior(String behavior) { this.zIndexBehavior = behavior; }
    public boolean getEnabled() { return enabled; }
    public boolean getResetOnSpawn() { return resetOnSpawn; }
    public String getZIndexBehavior() { return zIndexBehavior; }

}
