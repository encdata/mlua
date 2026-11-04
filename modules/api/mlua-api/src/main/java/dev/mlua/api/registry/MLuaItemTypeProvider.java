package dev.mlua.api.registry;

import java.util.Map;
import net.minecraft.world.item.Item;

/** Java implementation for an addon-defined item kind selectable from a Lua item definition. */
public interface MLuaItemTypeProvider {
   /** Validates addon-specific fields. The map is a detached snapshot and must not be retained or mutated. */
   default void validateDefinition(Map<String, Object> definition) { }

   /** Creates the item registered at {@code registryName} using MLua's validated common properties. */
   Item createNamed(Map<String, Object> definition, Item.Properties properties, String registryName);

   /** Creates the scripted item using MLua's validated common properties and behavior key. */
   Item createScripted(Map<String, Object> definition, Item.Properties properties, String behaviorKey);
}
