package dev.mlua.ci;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    private int serverTicks;
    private boolean mixinProbeCompleted;

    public SmokeMod() {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by Forge");
        }
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!"called".equals(System.getProperty("mlua.ci.service.SERVER"))) {
            if (++serverTicks == 200) {
                System.out.println("MLUA_CI_SERVER_SERVICE_MISSING");
                ServerLifecycleHooks.getCurrentServer().halt(false);
            }
            return;
        }
        if (!mixinProbeCompleted) {
            runMixinProbe();
            mixinProbeCompleted = true;
        }
        System.out.println("MLUA_PACKAGED_SERVER_BOOT_OK");
        if (!Boolean.parseBoolean(System.getenv("MLUA_CI_PLAYER_TEST"))) {
            ServerLifecycleHooks.getCurrentServer().halt(false);
        }
    }

    private static void runMixinProbe() {
        try {
            Class.forName("dev.mlua.ci.SmokeTarget", true, SmokeMod.class.getClassLoader())
                .getMethod("probe").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not invoke the Lua mixin smoke target", exception);
        }
    }
}
