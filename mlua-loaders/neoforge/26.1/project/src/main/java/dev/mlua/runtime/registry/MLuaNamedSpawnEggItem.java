package dev.mlua.runtime.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;

/** A named spawn egg using the 26.1 item property that supplies its entity type. */
public final class MLuaNamedSpawnEggItem extends Item {
    public MLuaNamedSpawnEggItem(EntityType<? extends Mob> type, int primaryColor, int secondaryColor, Item.Properties properties, String modId, String name) {
        super(properties.spawnEgg(type));
    }
}
