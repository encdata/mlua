package dev.mlua.mc.impl.entity;

import dev.mlua.mcapi.entity.MCLocalPlayer;
import net.minecraft.client.player.LocalPlayer;

public class MCLocalPlayerImpl extends MCPlayerImpl implements MCLocalPlayer {

    public MCLocalPlayerImpl(LocalPlayer inner) {
        super(inner);
    }

    private LocalPlayer localPlayer() {
        return (LocalPlayer) inner;
    }

    public static MCLocalPlayerImpl fromReal(LocalPlayer inner) {
        return inner == null ? null : new MCLocalPlayerImpl(inner);
    }
}
