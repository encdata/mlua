package dev.mlua.modules.events;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Builds the shared Lua event payload contract and dispatches it to the owning runtime. */
final class MLuaEventContext {
    private MLuaEventContext() { }

    static LuaTable create(Side side, boolean canCancel) {
        return create(side == Side.CLIENT ? "Client" : "Server", canCancel);
    }

    static LuaTable create(Level level, boolean canCancel) {
        return create(level.isClientSide() ? Side.CLIENT : Side.SERVER, canCancel);
    }

    static LuaTable create(String side, boolean canCancel) {
        LuaTable context = new LuaTable();
        context.set("Side", LuaValue.valueOf(side));
        context.set("Cancelled", LuaValue.FALSE);
        context.set("CanCancel", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(canCancel); }
        });
        context.set("IsCancelled", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(isCancelled(context)); }
        });
        context.set("Cancel", new ZeroArgFunction() {
            @Override public LuaValue call() {
                if (canCancel) context.set("Cancelled", LuaValue.TRUE);
                return LuaValue.NONE;
            }
        });
        return context;
    }

    static boolean isCancelled(LuaTable context) {
        return context.get("Cancelled").toboolean();
    }

    static EventBridge.CancellableResult result(LuaTable context) {
        return isCancelled(context) ? EventBridge.CancellableResult.cancelled() : EventBridge.CancellableResult.pass();
    }

    static LuaTable player(Player player) {
        var registration = MLuaRuntime.registrationForLevel(player.level());
        if (player instanceof ServerPlayer serverPlayer) {
            return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration);
        }
        if (player instanceof LocalPlayer localPlayer) {
            return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), registration);
        }
        return MLuaEntityWrapper.entityTable(player, registration);
    }

    static LuaTable entity(Entity entity) {
        return MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    static String dimension(Level level) {
        return level.dimension().location().toString();
    }

    static void fire(MLuaSignal signal, Level level, Side side, LuaTable context) {
        var session = MLuaRuntime.sessionForLevel(level);
        LuaValue canCancel = context.get("CanCancel");
        if (canCancel.isfunction() && canCancel.call().toboolean()) {
            // These events return their cancellation result to Minecraft immediately.
            // Running a coroutine here makes the owner thread wait while Lua calls back
            // into the same world, so dispatch them on the thread that owns that world.
            signal.fireInlineOnSession(session, side, context);
            return;
        }
        signal.fireOnSession(session, side, context);
    }

    static void addDamageSource(LuaTable context, Level level, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            context.set("Player", player(player));
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            context.set("Attacker", entity(attacker));
        }
        //--version 26.1
        var sourceKey = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getKey(source.type());
        //--endversion
        //--version 1.20.1,1.21.1
        var sourceKey = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getKey(source.type());
        //--endversion
        context.set("Source", LuaValue.valueOf(sourceKey != null ? sourceKey.toString() : "generic"));
    }
}
