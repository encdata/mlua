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
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        if (!"called".equals(System.getProperty("mlua.ci.service.CLIENT"))) {
            if (++ticks == 200) {
                System.out.println("MLUA_CI_CLIENT_SERVICE_MISSING");
                Minecraft.getInstance().stop();
            }
            return;
        }
        System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
        Minecraft.getInstance().stop();
    }
}
