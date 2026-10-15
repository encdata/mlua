package dev.mlua.ci;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mlua_ci_smoke", value = Dist.CLIENT)
public final class SmokeClient {
    private static int ticks;

    private SmokeClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 40) return;
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        System.out.println("MLUA_PACKAGED_CLIENT_SMOKE_OK");
        Minecraft.getInstance().stop();
    }
}
