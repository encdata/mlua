package dev.mlua.ci;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    private int ticks;

    public SmokeMod(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by NeoForge");
        }
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++ticks >= 40) {
            System.out.println("MLUA_PACKAGED_SERVER_SMOKE_OK");
            event.getServer().halt(false);
        }
    }
}
