package dev.mlua.minecraft.ui.element;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import dev.mlua.minecraft.ui.UDim2;
import dev.mlua.minecraft.ui.data.Vector2;
import dev.mlua.minecraft.ui.data.Rect;

public class ImageLabel extends GuiObject {
    protected String image = "";
    protected int imageColor3 = 0xFFFFFFFF;
    protected float imageTransparency = 0.0f;
    protected Vector2 imageRectOffset = new Vector2(0, 0);
    protected Vector2 imageRectSize = new Vector2(0, 0);
    protected String resampleMode = "Default";
    protected String scaleType = "Stretch";
    protected Rect sliceCenter = new Rect(0, 0, 0, 0);
    protected float sliceScale = 1.0f;
    protected UDim2 tileSize = new UDim2(new dev.mlua.minecraft.ui.UDim(0, 0), new dev.mlua.minecraft.ui.UDim(0, 0));

    public ImageLabel() { super(); }

    public void setImage(String image) { this.image = image; syncLuaProperty("Image", LuaValue.valueOf(image)); }
    public String getImage() { return image; }
    public void setImageColor3(int color) { this.imageColor3 = color; syncLuaProperty("ImageColor3", LuaValue.valueOf(color)); }
    public int getImageColor3() { return imageColor3; }
    public void setImageTransparency(float transparency) { this.imageTransparency = transparency; syncLuaProperty("ImageTransparency", LuaValue.valueOf(transparency)); }
    public float getImageTransparency() { return imageTransparency; }
    public void setImageRectOffset(Vector2 offset) { this.imageRectOffset = offset; syncLuaProperty("ImageRectOffset", LuaValue.valueOf(offset.x())); }
    public Vector2 getImageRectOffset() { return imageRectOffset; }
    public void setImageRectSize(Vector2 size) { this.imageRectSize = size; syncLuaProperty("ImageRectSize", LuaValue.valueOf(size.x())); }
    public Vector2 getImageRectSize() { return imageRectSize; }
    public void setResampleMode(String mode) { this.resampleMode = mode; syncLuaProperty("ResampleMode", LuaValue.valueOf(mode)); }
    public String getResampleMode() { return resampleMode; }
    public void setScaleType(String type) { this.scaleType = type; syncLuaProperty("ScaleType", LuaValue.valueOf(type)); }
    public String getScaleType() { return scaleType; }
    public void setSliceCenter(Rect rect) { this.sliceCenter = rect; syncLuaProperty("SliceCenter", LuaValue.valueOf(rect.x())); }
    public Rect getSliceCenter() { return sliceCenter; }
    public void setSliceScale(float scale) { this.sliceScale = scale; syncLuaProperty("SliceScale", LuaValue.valueOf(scale)); }
    public float getSliceScale() { return sliceScale; }
    public void setTileSize(UDim2 size) { this.tileSize = size; syncLuaProperty("TileSize", LuaValue.valueOf(size.x().offset())); }
    public UDim2 getTileSize() { return tileSize; }

    @Override
    protected void writeProperties(LuaTable table) {
        super.writeProperties(table);
    }
}

