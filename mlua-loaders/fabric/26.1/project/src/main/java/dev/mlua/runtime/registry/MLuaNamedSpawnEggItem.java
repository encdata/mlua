package dev.mlua.runtime.registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
public class MLuaNamedSpawnEggItem extends Item { public MLuaNamedSpawnEggItem(EntityType<? extends Mob> type,int primary,int secondary,Item.Properties properties,String modId,String name){super(properties);} }
