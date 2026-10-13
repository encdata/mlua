package dev.mlua.minecraft.scripted;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
public class MLuaScriptedEntity extends PathfinderMob {
    public MLuaScriptedEntity(EntityType<? extends MLuaScriptedEntity> type, Level level, String behaviorKey) { super(type, level); }
}
