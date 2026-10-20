package dev.mlua.mcapi.server;

import dev.mlua.mcapi.entity.MCServerPlayer;
import java.util.List;

public interface MCPlayerList {

    List<MCServerPlayer> getPlayers();

    boolean isOp(Object gameProfile);

    int getPlayerCount();

    int getMaxPlayers();

}
