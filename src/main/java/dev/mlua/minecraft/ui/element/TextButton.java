package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class TextButton extends GuiButton {
    protected String text = "";
    protected int textColor3 = 0xFF000000;
    protected float textTransparency = 0.0f;
    protected int textSize = 14;
    protected String textXAlignment = "Center";
    protected String textYAlignment = "Center";
    protected boolean textFits = true;
    protected boolean textScaled = false;
    protected boolean textWrapped = false;

    public TextButton() {
        super();
    }

    public void setText(String text) { this.text = text; syncLuaProperty("Text", LuaValue.valueOf(text)); }
    public String getText() { return text; }
    public void setTextColor3(int color) { this.textColor3 = color; syncLuaProperty("TextColor3", LuaValue.valueOf(color)); }
    public int getTextColor3() { return textColor3; }
    public void setTextTransparency(float transparency) { this.textTransparency = transparency; syncLuaProperty("TextTransparency", LuaValue.valueOf(transparency)); }
    public float getTextTransparency() { return textTransparency; }
    public void setTextSize(int size) { this.textSize = size; syncLuaProperty("TextSize", LuaValue.valueOf(size)); }
    public int getTextSize() { return textSize; }
    public void setTextXAlignment(String align) { this.textXAlignment = align; syncLuaProperty("TextXAlignment", LuaValue.valueOf(align)); }
    public String getTextXAlignment() { return textXAlignment; }
    public void setTextYAlignment(String align) { this.textYAlignment = align; syncLuaProperty("TextYAlignment", LuaValue.valueOf(align)); }
    public String getTextYAlignment() { return textYAlignment; }
    
    public boolean getTextFits() { return textFits; }
    public void setTextFits(boolean fits) { this.textFits = fits; syncLuaProperty("TextFits", LuaValue.valueOf(fits)); }
    public boolean getTextScaled() { return textScaled; }
    public void setTextScaled(boolean scaled) { this.textScaled = scaled; syncLuaProperty("TextScaled", LuaValue.valueOf(scaled)); }
    public boolean getTextWrapped() { return textWrapped; }
    public void setTextWrapped(boolean wrapped) { this.textWrapped = wrapped; syncLuaProperty("TextWrapped", LuaValue.valueOf(wrapped)); }

    @Override
    protected void writeProperties(LuaTable table) {
        super.writeProperties(table);
    }
}
