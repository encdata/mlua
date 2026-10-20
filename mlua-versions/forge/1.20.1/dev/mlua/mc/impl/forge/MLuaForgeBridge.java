package dev.mlua.mc.impl.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.nio.file.Path;

public final class MLuaForgeBridge {

    private MLuaForgeBridge() {}

    public static MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public static IEventBus getModEventBus() {
        return FMLJavaModLoadingContext.get().getModEventBus();
    }

    public static IEventBus getForgeEventBus() {
        return MinecraftForge.EVENT_BUS;
    }

    public static Dist getDist() {
        return FMLLoader.getDist();
    }

    public static boolean isProduction() {
        return FMLLoader.isProduction();
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static ModList getModList() {
        return ModList.get();
    }
}
