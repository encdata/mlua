package dev.mlua.ci;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("mlua_ci_smoke")
public final class SmokeMod {
    public SmokeMod(IEventBus modEventBus) {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by NeoForge");
        }
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        if (isClientEnvironment()) {
            registerClientProbe();
        }
    }

    private static boolean isClientEnvironment() {
        try {
            Object distribution = FMLEnvironment.class.getMethod("getDist").invoke(null);
            return "CLIENT".equals(String.valueOf(distribution));
        } catch (NoSuchMethodException ignored) {
            try {
                Object distribution = FMLEnvironment.class.getField("dist").get(null);
                return "CLIENT".equals(String.valueOf(distribution));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not detect the NeoForge distribution", exception);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not detect the NeoForge distribution", exception);
        }
    }

    private void registerClientProbe() {
        try {
            Class<?> probe = Class.forName("dev.mlua.ci.SmokeClient", true, getClass().getClassLoader());
            probe.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not register the client smoke probe", exception);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        System.out.println("MLUA_PACKAGED_SERVER_BOOT_OK");
        event.getServer().halt(false);
    }
}
