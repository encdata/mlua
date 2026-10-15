package dev.mlua.ci;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

public final class SmokeClient {
    private static int ticks;

    private SmokeClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SmokeClient::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (!ModList.get().isLoaded("mlua")) {
            throw new IllegalStateException("Packaged MLua JAR was not discovered by NeoForge");
        }
        if (!"called".equals(System.getProperty("mlua.ci.service.CLIENT"))) {
            if (++ticks >= 200) {
                throw new IllegalStateException("MLua client script did not call the Java contributed service");
            }
            return;
        }
        System.out.println("MLUA_PACKAGED_CLIENT_BOOT_OK");
        Minecraft.getInstance().stop();
    }
}
