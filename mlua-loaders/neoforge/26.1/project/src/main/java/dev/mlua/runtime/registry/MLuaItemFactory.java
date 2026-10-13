package dev.mlua.runtime.registry;

import java.util.Map;
import net.minecraft.world.item.Item;
import dev.mlua.minecraft.scripted.MLuaScriptedItem;

public final class MLuaItemFactory {
    public Item createNamed(Map<String, Object> properties, String registryName) { return create(properties); }
    public Item createScripted(Map<String, Object> properties, String behaviorKey) {
        return new MLuaScriptedItem(itemProperties(properties), behaviorKey);
    }
    private Item create(Map<String, Object> properties) { return new Item(itemProperties(properties)); }
    public Item.Properties makeProperties(Map<String, Object> properties) { return itemProperties(properties); }
    private Item.Properties itemProperties(Map<String, Object> properties) {
        Item.Properties result = new Item.Properties();
        if (properties != null && properties.get("maxStackSize") instanceof Number number) result.stacksTo(Math.max(1, number.intValue()));
        if (properties != null && properties.get("durability") instanceof Number number && number.intValue() > 0) result.durability(number.intValue());
        return result;
    }
}
