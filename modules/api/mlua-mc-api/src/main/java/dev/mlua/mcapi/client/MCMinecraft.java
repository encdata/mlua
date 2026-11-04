package dev.mlua.mcapi.client;

import dev.mlua.mcapi.entity.MCEntity;
import dev.mlua.mcapi.entity.MCLocalPlayer;
import dev.mlua.mcapi.world.MCGameType;
import dev.mlua.mcapi.world.MCLevel;

public interface MCMinecraft {
    MCLocalPlayer getPlayer();
    MCLevel getLevel();
    MCOptions getOptions();
    MCMouseHandler getMouseHandler();
    MCEntity getCameraEntity();
    MCGameType getGameMode();
    Object getPacketListener();
    void setScreen(Object screen);
    Object getTextureManager();
    Object getResourceManager();
    Object getFont();
    int getFps();
}
