package dev.mlua.mc.impl.client;

import dev.mlua.mcapi.client.MCMinecraft;
import dev.mlua.mcapi.client.MCMouseHandler;
import dev.mlua.mcapi.client.MCOptions;
import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.mcapi.world.MCLevel;
import dev.mlua.mc.impl.entity.MCEntityImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.world.MCLevelImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public class MCMinecraftImpl implements MCMinecraft {
    private final Minecraft inner;

    public MCMinecraftImpl(Minecraft inner) {
        this.inner = inner;
    }

    public static MCMinecraftImpl fromReal(Minecraft inner) {
        return inner == null ? null : new MCMinecraftImpl(inner);
    }

    public Minecraft inner() {
        return inner;
    }

    @Override
    public MCLocalPlayer getPlayer() {
        LocalPlayer player = inner.player;
        if (player != null) {
            return MCLocalPlayerImpl.fromReal(player);
        }
        return null;
    }

    @Override
    public MCLevel getLevel() {
        net.minecraft.client.multiplayer.ClientLevel level = inner.level;
        if (level != null) {
            return new MCLevelImpl(level);
        }
        return null;
    }

    @Override
    public MCOptions getOptions() {
        return MCOptionsImpl.fromReal(inner.options);
    }

    @Override
    public MCMouseHandler getMouseHandler() {
        return MCMouseHandlerImpl.fromReal(inner.mouseHandler);
    }

    @Override
    public MCEntity getCameraEntity() {
        Entity entity = inner.getCameraEntity();
        if (entity != null) {
            return new MCEntityImpl(entity);
        }
        return null;
    }

    @Override
    public MCGameType getGameMode() {
        if (inner.gameMode != null) {
            net.minecraft.world.level.GameType gt = inner.gameMode.getPlayerMode();
            return MCGameType.valueOf(gt.name());
        }
        return null;
    }

    @Override
    public Object getPacketListener() {
        if (inner.getConnection() != null) {
            return inner.getConnection();
        }
        return null;
    }

    @Override
    public void setScreen(Object screen) {
        if (screen instanceof net.minecraft.client.gui.screens.Screen s) {
            inner.setScreen(s);
        }
    }

    @Override
    public Object getTextureManager() {
        return inner.getTextureManager();
    }

    @Override
    public Object getResourceManager() {
        return inner.getResourceManager();
    }

    @Override
    public Object getFont() {
        return inner.font;
    }

    @Override
    public int getFps() {
        return inner.getFps();
    }
}
