package dev.mlua.mcapi.boss;

import dev.mlua.mcapi.entity.MCPlayer;
import dev.mlua.mcapi.MCApiRuntime;

import java.util.Collection;

public interface MCServerBossEvent extends MCBossEvent {
    void addPlayer(MCPlayer player);
    void removePlayer(MCPlayer player);
    void removeAllPlayers();
    Collection<MCPlayer> getPlayers();

    static MCServerBossEvent create() {
        return (MCServerBossEvent) MCApiRuntime.invokeStaticNoArgs(
            "dev.mlua.mc.impl.boss.MCServerBossEventImpl",
            "create"
        );
    }
}
