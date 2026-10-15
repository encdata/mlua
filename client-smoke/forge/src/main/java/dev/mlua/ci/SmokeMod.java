package dev.mlua.ci;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    private int ticks;

    public SmokeMod() {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks >= 40) {
            System.out.println("MLUA_PACKAGED_SERVER_SMOKE_OK");
            ServerLifecycleHooks.getCurrentServer().halt(false);
        }
    }
}
