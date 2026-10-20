package dev.mlua.mcapi.entity;

import dev.mlua.mcapi.core.MCVec3;
import dev.mlua.mcapi.item.MCItemStack;
import dev.mlua.mcapi.nbt.MCCompoundTag;
import dev.mlua.mcapi.server.MCMinecraftServer;
import dev.mlua.mcapi.network.MCConnection;
import dev.mlua.mcapi.sound.MCSoundEvent;
import dev.mlua.mcapi.sound.MCSoundSource;
import dev.mlua.mcapi.world.MCGameType;

public interface MCPlayer extends MCLivingEntity {
    MCInventory getInventory();
    MCFoodData getFoodData();
    MCConnection getConnection();
    int getExperienceLevel();
    void setExperienceLevel(int level);
    float getExperienceProgress();
    void setExperienceProgress(float progress);
    boolean addItem(MCItemStack stack);
    void giveExperiencePoints(int points);
    void giveExperienceLevels(int levels);
    boolean isCreative();
    boolean isSpectator();
    boolean isSleeping();
    boolean isCrouching();
    boolean isSprinting();
    boolean isSwimming();
    void teleportTo(double x, double y, double z);
    void setGameMode(MCGameType gameMode);
    boolean hasPermissions(int level);
    MCMinecraftServer getServer();
    MCCompoundTag getPersistentData();
    void displayClientMessage(Object component, boolean actionBar);
    void playNotifySound(MCSoundEvent sound, MCSoundSource source, float volume, float pitch);
    boolean isHurtMarked();
    void setHurtMarked(boolean hurtMarked);
    void setDeltaMovement(MCVec3 v);

    interface MCInventory {
        int getContainerSize();
        MCItemStack getItem(int slot);
        void setItem(int slot, MCItemStack stack);
        void clearContent();
        int getSelected();
        void setSelected(int slot);
        MCItemStack getArmor(int index);
        MCItemStack getOffhand();
    }

    interface MCFoodData {
        int getFoodLevel();
        void setFoodLevel(int level);
        float getSaturationLevel();
        void setSaturation(float saturation);
        void addExhaustion(float exhaustion);
    }
}
