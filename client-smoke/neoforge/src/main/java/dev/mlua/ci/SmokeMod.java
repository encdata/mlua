package dev.mlua.ci;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    public SmokeMod(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by NeoForge");
        }
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        System.out.println("MLUA_PACKAGED_SERVER_BOOT_OK");
        event.getServer().halt(false);
    }
}
