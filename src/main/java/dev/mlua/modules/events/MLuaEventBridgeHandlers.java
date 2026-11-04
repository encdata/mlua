package dev.mlua.modules.events;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.scripted.MLuaBlockCallbackDispatcher;
import dev.mlua.modules.events.service.MLuaEventsService;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Adapts loader events into MLua signal payloads and applies gameplay event side rules. */
final class MLuaEventBridgeHandlers {
    private MLuaEventBridgeHandlers() { }

    static void register(EventBridge events) {
        List<String> unsupportedEvents = new java.util.ArrayList<>();
        List<String> unavailableSignals = new java.util.ArrayList<>();
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity hurt", "EntityHurt",
            () -> events.onEntityHurtEvent((entity, source, amount) ->
                fireEntityHurt((LivingEntity) entity, (DamageSource) source, amount)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity death", "EntityDeath",
            () -> events.onEntityDeath((entity, source) ->
                fireEntityDeath((LivingEntity) entity, (DamageSource) source)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block break", "BlockBreak",
            () -> events.onBlockBreakEvent((player, level, pos) ->
                player instanceof ServerPlayer serverPlayer
                    && level instanceof Level serverLevel
                    && pos instanceof BlockPos blockPos
                        ? fireBlockBreak(serverPlayer, serverLevel, blockPos)
                        : EventBridge.CancellableResult.pass()));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block place", "BlockPlace",
            () -> events.onBlockPlace((entity, level, pos) ->
                fireBlockPlace((Level) level, (Entity) entity, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item use", "ItemUse",
            () -> events.onItemUseEvent((player, hand, itemStack) ->
                fireItemUse((Player) player, hand, (ItemStack) itemStack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity interaction", "EntityInteract",
            () -> events.onEntityInteractEvent((player, hand, target) ->
                fireEntityInteract((Player) player, hand, (Entity) target)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "player respawn", "PlayerRespawn",
            () -> events.onPlayerRespawn(MLuaEventBridgeHandlers::firePlayerRespawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity spawn", "EntitySpawn",
            () -> events.onEntitySpawn(MLuaEventBridgeHandlers::fireEntitySpawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "client entity unload", "EntityUnload",
            () -> events.onClientEntityUnload(MLuaEventBridgeHandlers::fireClientEntityUnload));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world load", "WorldLoad",
            () -> events.onWorldLoad(MLuaEventBridgeHandlers::fireWorldLoad));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world unload", "WorldUnload",
            () -> events.onWorldUnload(MLuaEventBridgeHandlers::fireWorldUnload));
        if (dev.mlua.loaderapi.LoaderApi.platform().isClient()) {
            registerLoaderEvent(unsupportedEvents, "client world load",
                () -> events.onClientWorldLoad(MLuaEventBridgeHandlers::fireWorldLoad));
            registerLoaderEvent(unsupportedEvents, "client world unload",
                () -> events.onClientWorldUnload(MLuaEventBridgeHandlers::fireWorldUnload));
        }
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block right click", "BlockRightClick",
            () -> events.onBlockRightClick((player, hand, level, pos) ->
                fireBlockRightClick((Player) player, hand, (Level) level, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item drop", "ItemDrop",
            () -> events.onItemDrop((player, stack) ->
                MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_DROP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item pickup", "ItemPickup",
            () -> events.onItemPickup((player, stack) ->
                MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_PICKUP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item craft", "ItemCraft",
            () -> events.onItemCraft((player, stack) ->
                MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_CRAFT, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container open", "ContainerOpen",
            () -> events.onContainerOpen((player, container) ->
                MLuaEventPayloads.fireContainerEvent(MLuaEventsService.CONTAINER_OPEN, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container close", "ContainerClose",
            () -> events.onContainerClose((player, container) ->
                MLuaEventPayloads.fireContainerEvent(MLuaEventsService.CONTAINER_CLOSE, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "projectile hit", "ProjectileHit",
            () -> events.onProjectileHit(MLuaEventPayloads::fireProjectileHit));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "explosion", "Explosion",
            () -> events.onExplosion(MLuaEventPayloads::fireExplosion));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "advancement", "AdvancementCompleted",
            () -> events.onAdvancement(MLuaEventPayloads::fireAdvancement));
        registerLoaderEvent(unsupportedEvents, "server level tick",
            () -> events.onServerLevelTickEnd(MLuaPlayerStepTracker::track));
        MLuaEventsService.setUnavailableSignals(unavailableSignals);
        if (!unsupportedEvents.isEmpty()) {
            MLuaLogger.LOGGER.warn("MLua event hooks unavailable on this loader: {}", String.join(", ", unsupportedEvents));
        }
    }

    private static void registerLoaderEvent(List<String> unsupportedEvents, List<String> unavailableSignals,
            String eventName, String signalName, Runnable registration) {
        try {
            registration.run();
        } catch (UnsupportedOperationException unsupported) {
            unsupportedEvents.add(eventName);
            unavailableSignals.add(signalName);
        }
    }

    private static void registerLoaderEvent(List<String> unsupportedEvents, String eventName, Runnable registration) {
        try {
            registration.run();
        } catch (UnsupportedOperationException unsupported) {
            unsupportedEvents.add(eventName);
        }
    }

    static EventBridge.CancellableResult fireEntityHurt(LivingEntity entity, DamageSource source, float amount) {
        if (entity.level().isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        ctx.set("Entity", MLuaEventContext.entity(entity));
        ctx.set("Amount", LuaValue.valueOf(amount));
        MLuaEventContext.addDamageSource(ctx, entity.level(), source);
        MLuaEventContext.fire(MLuaEventsService.ENTITY_HURT, entity.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onHurt(entity, ctx);
        return MLuaEventContext.result(ctx);
    }

    static void fireEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) {
            return;
        }
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Entity", MLuaEventContext.entity(entity));
        MLuaEventContext.addDamageSource(ctx, entity.level(), source);
        MLuaEventContext.fire(MLuaEventsService.ENTITY_DEATH, entity.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onDeath(entity, ctx);
    }

    static EventBridge.CancellableResult fireBlockBreak(ServerPlayer player, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        MLuaBlockCallbackDispatcher.fireOnBreak(level, pos, player);
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        ctx.set("Player", MLuaEventContext.player(player));
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        MLuaEventContext.fire(MLuaEventsService.BLOCK_BREAK, level, Side.SERVER, ctx);
        return MLuaEventContext.result(ctx);
    }

    static void fireBlockPlace(Level level, Entity entity, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        if (entity instanceof Player player) {
            MLuaBlockCallbackDispatcher.fireOnPlace(level, pos, player);
        }
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        if (entity instanceof ServerPlayer player) {
            ctx.set("Player", MLuaEventContext.player(player));
        } else if (entity instanceof LivingEntity living) {
            ctx.set("Entity", MLuaEventContext.entity(living));
        }
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        MLuaEventContext.fire(MLuaEventsService.BLOCK_PLACE, level, Side.SERVER, ctx);
    }

    static EventBridge.CancellableResult fireItemUse(Player player, Object handObj, ItemStack itemStack) {
        if (player.level().isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaEventContext.player(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            LuaValue playerInstance = MLuaPlayerWrapper.localPlayerTable(
                MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level()));
            ctx.set("Player", playerInstance);
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Item", MLuaItemWrapper.itemTable(itemStack, MLuaRuntime.registrationForLevel(player.level())));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.ITEM_USE, player.level(), Side.SERVER, ctx);
        return MLuaEventContext.result(ctx);
    }

    static EventBridge.CancellableResult fireEntityInteract(Player player, Object handObj, Entity target) {
        if (player.level().isClientSide() || !(target instanceof LivingEntity living)) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaEventContext.player(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            LuaValue playerInstance = MLuaPlayerWrapper.localPlayerTable(
                MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level()));
            ctx.set("Player", playerInstance);
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Entity", MLuaEventContext.entity(living));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.ENTITY_INTERACT, player.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onInteract(living, ctx);
        return MLuaEventContext.result(ctx);
    }

    static void firePlayerRespawn(Object playerObj) {
        if (!(playerObj instanceof Player player)) return;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Player", MLuaEventContext.player(player));
        MLuaEventContext.fire(MLuaEventsService.PLAYER_RESPAWN, player.level(), Side.SERVER, ctx);
    }

    static void fireEntitySpawn(Object entityObj) {
        if (!(entityObj instanceof Entity entity)) return;
        LuaTable ctx = MLuaEventContext.create(entity.level(), false);
        ctx.set("Entity", MLuaEventContext.entity(entity));
        Side side = entity.level().isClientSide() ? Side.CLIENT : Side.SERVER;
        MLuaEventContext.fire(MLuaEventsService.ENTITY_SPAWN, entity.level(), side, ctx);
    }

    static void fireClientEntityUnload(Object entityObj) {
        if (!(entityObj instanceof Entity entity) || !entity.level().isClientSide()) return;
        try {
            LuaTable ctx = MLuaEventContext.create(Side.CLIENT, false);
            ctx.set("Entity", MLuaEventContext.entity(entity));
            ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(entity.level())));
            MLuaEventContext.fire(MLuaEventsService.ENTITY_UNLOAD, entity.level(), Side.CLIENT, ctx);
        } finally {
            MLuaEntityWrapper.removeFromCache(entity);
        }
    }

    static void fireWorldLoad(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        LuaTable ctx = MLuaEventContext.create(level, false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(level)));
        MLuaEventContext.fire(MLuaEventsService.WORLD_LOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        MLuaRuntime.get().runWorldLifecycleEntrypoint(level, true);
    }

    static void fireWorldUnload(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        if (!level.isClientSide()) {
            EventsModule.forgetWorldStepPositions(dev.mlua.minecraft.MLuaDimensionId.from(level), MLuaRuntime.sessionForLevel(level));
        }
        LuaTable ctx = MLuaEventContext.create(level, false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(level)));
        MLuaEventContext.fire(MLuaEventsService.WORLD_UNLOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        MLuaRuntime.get().runWorldLifecycleEntrypoint(level, false);
    }

    static EventBridge.BlockInteractionResult fireBlockRightClick(Player player, Object handObj, Level level, BlockPos pos) {
        if (player == null || level == null || pos == null) return EventBridge.BlockInteractionResult.pass();
        LuaTable ctx = MLuaEventContext.create(level, true);
        ctx.set("Player", MLuaEventContext.player(player));
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Hand", LuaValue.valueOf(String.valueOf(handObj).toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.BLOCK_RIGHT_CLICK, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        return MLuaEventContext.isCancelled(ctx) ? EventBridge.BlockInteractionResult.cancelled() : EventBridge.BlockInteractionResult.pass();
    }
}
