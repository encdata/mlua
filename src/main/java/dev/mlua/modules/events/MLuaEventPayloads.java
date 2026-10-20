package dev.mlua.modules.events;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaContainer;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.mc.impl.container.MCAbstractContainerMenuImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.modules.events.service.MLuaEventsService;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Builds payloads for loader events whose data is not already a live MLua instance. */
final class MLuaEventPayloads {
    private MLuaEventPayloads() { }

    static void fireItemStackEvent(MLuaSignal signal, Object playerObj, Object stackObj) {
        if (playerObj instanceof Player player && player.level().isClientSide()) return;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        if (playerObj instanceof Player player) ctx.set("Player", MLuaEventContext.player(player));
        Level level = playerObj instanceof Player player ? player.level() : null;
        if (stackObj instanceof ItemStack stack) ctx.set("Item", MLuaItemWrapper.itemTable(stack, level != null ? MLuaRuntime.registrationForLevel(level) : MLuaRuntime.get()));
        else if (stackObj != null) ctx.set("Item", LuaValue.userdataOf(stackObj));
        MLuaEventContext.fire(signal, level, Side.SERVER, ctx);
    }

    static void fireContainerEvent(MLuaSignal signal, Object playerObj, Object container) {
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        if (playerObj instanceof Player player) ctx.set("Player", MLuaEventContext.player(player));
        Level level = playerObj instanceof Player player ? player.level() : null;
        MLuaRegistration registration = level == null
            ? MLuaRuntime.get() : MLuaRuntime.registrationForLevel(level);
        if (container instanceof AbstractContainerMenu menu) {
            String title = menu.getType() == null ? menu.getClass().getSimpleName() : menu.getType().toString();
            boolean writable = signal == MLuaEventsService.CONTAINER_OPEN;
            ctx.set("Container", MLuaContainer.containerTable(MCAbstractContainerMenuImpl.fromReal(menu),
                MCComponent.literal(title), writable, registration,
                () -> playerObj instanceof Player player && player.containerMenu == menu && !player.isRemoved()));
        }
        MLuaEventContext.fire(signal, level, Side.SERVER, ctx);
    }

    static void fireProjectileHit(Object projectileObj, Object hitResult) {
        if (!(projectileObj instanceof Entity entity) || entity.level().isClientSide()) return;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Projectile", MLuaEventContext.entity(entity));
        if (hitResult != null) ctx.set("HitResult", LuaValue.userdataOf(hitResult));
        MLuaEventContext.fire(MLuaEventsService.PROJECTILE_HIT, entity.level(), Side.SERVER, ctx);
    }

    static void fireExplosion(Object levelObj, Object position) {
        if (!(levelObj instanceof Level level) || level.isClientSide()) return;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(level)));
        if (position instanceof BlockPos pos) {
            ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            ctx.set("Position", MLuaPlayerWrapper.positionTable(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        } else if (position instanceof Vec3 vector) {
            ctx.set("Position", MLuaPlayerWrapper.positionTable(vector.x, vector.y, vector.z));
        }
        MLuaEventContext.fire(MLuaEventsService.EXPLOSION, level, Side.SERVER, ctx);
    }

    static void fireAdvancement(Object playerObj, String advancementId) {
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        if (playerObj instanceof Player player) ctx.set("Player", MLuaEventContext.player(player));
        ctx.set("Advancement", LuaValue.valueOf(advancementId));
        MLuaEventContext.fire(MLuaEventsService.ADVANCEMENT, playerObj instanceof Player player ? player.level() : null, Side.SERVER, ctx);
    }

}
