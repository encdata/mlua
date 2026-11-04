package dev.mlua.modules.world;

import dev.mlua.module.MLuaRegistration;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Binary/source compatibility facade for the pre-consolidation 26.1 helper. */
public final class MLuaWorldGameRules26 {
    private MLuaWorldGameRules26() { }

    public static LuaTable list(Level level, MLuaRegistration registration,
                                Supplier<MinecraftServer> serverSupplier) {
        return MLuaWorldGameRules.list(level, registration, serverSupplier);
    }

    public static LuaValue get(Level level, String name) {
        return MLuaWorldGameRules.get(level, name);
    }

    public static LuaTable entry(Level level, String name, MLuaRegistration registration,
                                 Supplier<MinecraftServer> serverSupplier) {
        return MLuaWorldGameRules.entry(level, name, registration, serverSupplier);
    }

    public static void set(ServerLevel level, String name, LuaValue value, MinecraftServer server) {
        MLuaWorldGameRules.set(level, name, value, server);
    }
}
