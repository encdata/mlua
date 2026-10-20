package dev.mlua.ci;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mlua_ci_smoke", value = Dist.CLIENT)
public final class SmokeClient {
    private static int serviceTicks;
    private static int lifecycleStartupTicks;
    private static int lifecycleTicks;
    private static boolean booted;

    private SmokeClient() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        if (!"called".equals(System.getProperty("mlua.ci.service.CLIENT"))) {
            if (++serviceTicks == 200) {
                System.out.println("MLUA_CI_CLIENT_SERVICE_MISSING");
                Minecraft.getInstance().stop();
            }
            return;
        }
        if (!"complete".equals(System.getProperty("mlua.ci.native_screen_lifecycle"))) {
            lifecycleStartupTicks++;
            String screenClass = Minecraft.getInstance().screen == null ? "none" : Minecraft.getInstance().screen.getClass().getName();
            if (screenClass.equals("dev.mlua.modules.coregui.service.MLuaScriptScreen")) {
                lifecycleTicks++;
            } else {
                lifecycleTicks = 0;
            }
            if (lifecycleTicks == 1) System.out.println("MLUA_CI_NATIVE_SCREEN_OBSERVED");
            if (lifecycleStartupTicks == 2400 || lifecycleTicks == 200) {
                System.out.println("MLUA_CI_ACTIVE_SCREEN=" + screenClass);
                System.out.println("MLUA_CI_NATIVE_SCREEN_LIFECYCLE_MISSING");
                Minecraft.getInstance().stop();
            }
            return;
        }
        if (booted) return;
        PlayerTestServerConnector.connectIfRequested(Minecraft.getInstance());
        booted = true;
        System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
        if (!Boolean.parseBoolean(System.getenv("MLUA_CI_PLAYER_TEST"))) Minecraft.getInstance().stop();
    }
}
