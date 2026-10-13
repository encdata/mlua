package dev.mlua.mc.impl.command;

import dev.mlua.mcapi.command.MCCommandSourceStack;
import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCPlayerImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class MCCommandSourceStackImpl implements MCCommandSourceStack {
    private final CommandSourceStack inner;

    public MCCommandSourceStackImpl(CommandSourceStack inner) {
        this.inner = inner;
    }

    public static MCCommandSourceStackImpl fromReal(CommandSourceStack inner) {
        return inner == null ? null : new MCCommandSourceStackImpl(inner);
    }

    public CommandSourceStack inner() {
        return inner;
    }

    @Override
    public MCLevel getLevel() {
        return new MCLevelImpl(inner.getLevel());
    }

    @Override
    public MCPlayer getPlayer() {
        Entity entity = inner.getEntity();
        if (entity instanceof net.minecraft.server.level.ServerPlayer player) {
            return MCPlayerImpl.fromReal(player);
        }
        return null;
    }

    @Override
    public MCEntity getEntity() {
        Entity entity = inner.getEntity();
        if (entity != null) {
            return new MCEntityImpl(entity);
        }
        return null;
    }

    @Override
    public MCVec3 getPosition() {
        return MCVec3Impl.fromReal(inner.getPosition());
    }

    @Override
    public boolean hasPermission(int level) {
        return inner.hasPermission(level);
    }

    @Override
    public Object getDisplayName() {
        return inner.getDisplayName();
    }
}
