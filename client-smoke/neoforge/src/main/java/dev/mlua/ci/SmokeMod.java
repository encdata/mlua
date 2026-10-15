package dev.mlua.ci;

import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    private int ticks;

    public SmokeMod(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by NeoForge");
        }
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (++ticks >= 40) {
            System.out.println("MLUA_PACKAGED_CLIENT_SMOKE_OK");
            Minecraft.getInstance().stop();
        }
    }
}
