package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mcapi.sound.MCSoundSource;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.mc.impl.core.MCVec3Impl;
import dev.mlua.mc.impl.item.MCItemStackImpl;
import dev.mlua.mc.impl.sound.MCSoundSourceImpl;
import dev.mlua.mc.impl.world.MCGameTypeImpl;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class MCPlayerImpl extends MCLivingEntityImpl implements MCPlayer {
    protected final Player inner;

    public MCPlayerImpl(Player inner) {
        super((net.minecraft.world.entity.LivingEntity) inner);
        this.inner = inner;
    }

    public static MCPlayerImpl fromReal(Player inner) {
        return inner == null ? null : new MCPlayerImpl(inner);
    }

    @Override
    public MCInventory getInventory() {
        Inventory inv = inner.getInventory();
        return new MCInventory() {
            @Override
            public int getContainerSize() { return inv.getContainerSize(); }

            @Override
            public MCItemStack getItem(int slot) { return MCItemStackImpl.fromReal(inv.getItem(slot)); }

            @Override
            public void setItem(int slot, MCItemStack stack) {
                if (stack == null || stack == MCItemStack.EMPTY || stack.isEmpty()) {
                    inv.setItem(slot, ItemStack.EMPTY);
                } else if (stack instanceof MCItemStackImpl wrapped) {
                    inv.setItem(slot, wrapped.inner());
                } else {
                    throw new IllegalArgumentException("Unsupported MCItemStack implementation: " + stack.getClass().getName());
                }
            }

            @Override
            public void clearContent() { inv.clearContent(); }

            @Override
            public int getSelected() { return inv.selected; }

            @Override
            public void setSelected(int slot) { inv.selected = slot; }

            @Override
            public MCItemStack getArmor(int index) {
                return MCItemStackImpl.fromReal(inv.getArmor(index));
            }

            @Override
            public MCItemStack getOffhand() {
                return MCItemStackImpl.fromReal(inv.offhand.get(0));
            }
        };
    }

    @Override
    public MCFoodData getFoodData() {
        var fd = inner.getFoodData();
        return new MCFoodData() {
            @Override
            public int getFoodLevel() { return fd.getFoodLevel(); }
            @Override
            public void setFoodLevel(int level) { fd.setFoodLevel(level); }
            @Override
            public float getSaturationLevel() { return fd.getSaturationLevel(); }
            @Override
            public void setSaturation(float saturation) { fd.setSaturation(saturation); }
        };
    }

    @Override
    public Object getConnection() {
        if (inner instanceof ServerPlayer sp) {
            return sp.connection;
        }
        return null;
    }

    @Override
    public int experienceLevel() {
        return inner.experienceLevel;
    }

    @Override
    public void setExperienceLevel(int level) {
        inner.experienceLevel = level;
    }

    @Override
    public float experienceProgress() {
        return inner.experienceProgress;
    }

    @Override
    public void setExperienceProgress(float progress) {
        inner.experienceProgress = progress;
    }

    @Override
    public boolean addItem(MCItemStack stack) {
        return inner.getInventory().add(((MCItemStackImpl) stack).inner());
    }

    @Override
    public void giveExperiencePoints(int points) {
        inner.giveExperiencePoints(points);
    }

    @Override
    public void giveExperienceLevels(int levels) {
        inner.giveExperienceLevels(levels);
    }

    @Override
    public boolean isCreative() {
        return inner.isCreative();
    }

    @Override
    public boolean isSpectator() {
        return inner.isSpectator();
    }

    @Override
    public boolean isSleeping() {
        return inner.isSleeping();
    }

    @Override
    public boolean isCrouching() {
        return inner.isCrouching();
    }

    @Override
    public boolean isSprinting() {
        return inner.isSprinting();
    }

    @Override
    public boolean isSwimming() {
        return inner.isSwimming();
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        inner.teleportTo(x, y, z);
    }

    @Override
    public void setGameMode(MCGameType gameMode) {
        if (inner instanceof ServerPlayer sp) {
            sp.setGameMode(MCGameTypeImpl.toReal(gameMode));
        }
    }

    @Override
    public boolean hasPermissions(int level) {
        return inner.hasPermissions(level);
    }

    @Override
    public MCMinecraftServer getServer() {
        return null;
    }

    @Override
    public MCCompoundTag getPersistentData() {
        return null;
    }

    @Override
    public void displayClientMessage(Object component, boolean actionBar) {
        if (component instanceof net.minecraft.network.chat.Component c) {
            inner.displayClientMessage(c, actionBar);
        }
    }

    @Override
    public void playNotifySound(MCSoundEvent sound, MCSoundSource source, float volume, float pitch) {
    }

    @Override
    public boolean isHurtMarked() {
        return inner.hurtMarked;
    }

    @Override
    public void setHurtMarked(boolean hurtMarked) {
        inner.hurtMarked = hurtMarked;
    }
}
