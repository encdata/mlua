package dev.mlua.ci;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    private int ticks;

    public SmokeMod() {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && ++ticks >= 40) {
            System.out.println("MLUA_PACKAGED_CLIENT_SMOKE_OK");
            Minecraft.getInstance().stop();
        }
    }
}
