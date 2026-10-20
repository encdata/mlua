package dev.mlua.modules.events;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.scripted.MLuaBlockCallbackDispatcher;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.MLuaLogger;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.events.service.MLuaEventsService;
import dev.mlua.modules.players.classes.MLuaEntityWrapper;
import dev.mlua.modules.players.classes.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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

public class EventsModule implements MLuaModule {
    @Override
    public List<String> dependencies() {
        return List.of("core-classes");
    }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaSignalLimitsBinding.register(registration);
    }

    public static void clearRuntime(Object runtimeOwner) {
        MLuaPlayerStepTracker.clearRuntime(runtimeOwner);
    }

    public static void clearRuntime(Object runtimeOwner, Side side) {
        if (side == Side.SERVER || side == Side.BOTH) clearRuntime(runtimeOwner);
    }

    @Override
    public void clearRuntimeState(MLuaRegistration runtime, Object runtimeSession, Side side) {
        clearRuntime(runtimeSession, side);
    }

    @Override
    public void afterRuntimeTick(MLuaRegistration runtime, Object runtimeSession, Side side, double deltaTime) {
        if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
        if (side == Side.SERVER) {
            MLuaEventsService.SERVER_TICK.fireOnSession(session, side, LuaValue.valueOf(deltaTime));
        } else if (side == Side.CLIENT) {
            MLuaEventsService.CLIENT_TICK.fireOnSession(session, side, LuaValue.valueOf(deltaTime));
        }
    }

    @Override
    public void afterRenderFrame(MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        if (!(runtimeSession instanceof MLuaRuntimeSession session)) return;
        MLuaEventsService.PRE_RENDER.fireOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
        MLuaEventsService.RENDER_STEPPED.fireOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
    }

    @Override
    public void onRenderFrameEnd(MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        if (runtimeSession instanceof MLuaRuntimeSession session) {
            MLuaEventsService.POST_RENDER.fireOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
        }
    }

    @Override
    public String id() { return "events"; }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Events", MLuaEventsService::new)
            .reflective(MLuaEventsService.class)
            .build());
    }

    @Override
    public void contributeDocs(dev.mlua.module.MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:GetSignalLimits()", "SignalLimits"));
        docs.register(MLuaDocBuilder.Registration.clazz("SignalLimits", "MLuaEventsService",
            "Point-in-time signal counts and per-signal registration limits.",
            "classes/signal-limits.html", "Instance"));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "ActiveCustomSignals", "number", "Custom signals currently registered across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "AddonSignals", "number", "Custom signals owned by the current addon."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxCustomSignals", "number", "Maximum custom signals across MLua."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxCustomSignalsPerAddon", "number", "Maximum custom signals owned by one addon."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "ActiveListeners", "number", "Listeners currently connected across all live signals."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "AddonListeners", "number", "Listeners owned by the current addon across all live signals."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxListenersPerSignal", "number", "Maximum listeners connected to one signal."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxListenersPerAddonPerSignal", "number", "Maximum listeners one addon can connect to one signal."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "ActiveWaiters", "number", "Waiters currently suspended across all live signals."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "AddonWaiters", "number", "Waiters owned by the current addon across all live signals."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxWaitersPerSignal", "number", "Maximum waiters suspended on one signal."));
        docs.register(MLuaDocBuilder.Registration.field("SignalLimits", "MaxWaitersPerAddonPerSignal", "number", "Maximum waiters one addon can suspend on one signal."));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:BlockBreak(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:BlockPlace(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityHurt(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityDeath(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityInteract(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:ItemUse(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.service("Events", "MLuaEventsService",
            "Provides built-in lifecycle and gameplay signals, plus addon-owned signals. Each built-in signal documents its own payload and side.",
            "services/events.html"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.Explosion", "event: { Level: Level, Dimension: string, Position: Vector3 }",
            "Fires when an explosion starts on the server. Position is the explosion center in world coordinates.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.EntityUnload", "event: { Entity: Entity, Dimension: string }",
            "Fires on the client when an entity leaves the loaded client world, such as when its chunk unloads or it changes dimensions. Use EntityDeath for server-side death; an unload does not mean the entity died.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.ContainerOpen", "event: { Player: Player, Container: Container }",
            "Fires after a player opens a menu. Container is a live wrapper; its slot mutation methods are available during this event.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.ContainerClose", "event: { Player: Player, Container: Container }",
            "Fires while a player closes a menu. Container is a read-only wrapper so scripts can inspect its final slots without changing a closed menu.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.ServerTick", "deltaTime: number",
            "Fires after each server tick. The callback receives the simulation step duration in seconds directly, not an event table.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.ClientTick", "deltaTime: number",
            "Fires after each client tick. The callback receives the simulation step duration in seconds directly, not an event table.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.RenderStepped", "deltaTime: number",
            "Fires at the start of each client render frame. The callback receives frame duration in seconds directly; this is distinct from Minecraft's partial-tick interpolation fraction.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.PreRender", "deltaTime: number",
            "Fires at the start of a client render frame immediately before RenderStepped. The callback receives the frame duration in seconds.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.PostRender", "deltaTime: number",
            "Fires after Minecraft finishes drawing the client render frame. The callback receives that frame's duration in seconds.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Events", "Find event signals available on this loader",
            "GetSignalNames lists registered signals. GetUnavailableSignals lists names that this loader cannot currently provide.",
            "local Events = Minecraft:GetService(\"Events\")\nlocal unavailable = {}\n\nfor _, name in ipairs(Events:GetUnavailableSignals()) do\n    unavailable[name] = true\nend\n\nfor _, name in ipairs(Events:GetSignalNames()) do\n    if not unavailable[name] then\n        print(name)\n    end\nend"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Events", "Handle client entity unload",
            "EntityUnload runs when the client removes an entity from its loaded world. This includes chunk unloads and dimension changes; use EntityDeath for server-side death.",
            "local Events = Minecraft:GetService(\"Events\")\n\nEvents.EntityUnload:Connect(function(event)\n    print(event.Entity.Name, \"left\", event.Dimension)\nend)"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Events", "Register an addon event signal",
            "Register the signal in a registry script. Other scripts can connect to it or fire it through the shared Events service. MLua removes it when this registry script rolls back.",
            "local Events = Minecraft:GetService(\"Events\")\nlocal FearChanged = Signal.new()\n\nEvents:RegisterSignal(\"FearChanged\", FearChanged, \"Fires when the addon changes the player's fear level.\")\n\n-- In another script:\nEvents.FearChanged:Connect(function(level)\n    print(\"Fear is now\", level)\nend)"));
    }

    @Override
    public void registerEvents(EventBridge events) {
        List<String> unsupportedEvents = new java.util.ArrayList<>();
        List<String> unavailableSignals = new java.util.ArrayList<>();
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity hurt", "EntityHurt", () -> events.onEntityHurtEvent((entity, source, amount) -> fireEntityHurt((LivingEntity) entity, (DamageSource) source, amount)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity death", "EntityDeath", () -> events.onEntityDeath((entity, source) -> fireEntityDeath((LivingEntity) entity, (DamageSource) source)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block break", "BlockBreak", () -> events.onBlockBreakEvent((player, level, pos) ->
            player instanceof ServerPlayer serverPlayer && level instanceof Level serverLevel && pos instanceof BlockPos blockPos
                ? fireBlockBreak(serverPlayer, serverLevel, blockPos) : EventBridge.CancellableResult.pass()));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block place", "BlockPlace", () -> events.onBlockPlace((entity, level, pos) -> fireBlockPlace((Level) level, (Entity) entity, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item use", "ItemUse", () -> events.onItemUseEvent((player, hand, itemStack) -> fireItemUse((Player) player, hand, (ItemStack) itemStack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity interaction", "EntityInteract", () -> events.onEntityInteractEvent((player, hand, target) -> fireEntityInteract((Player) player, hand, (Entity) target)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "player respawn", "PlayerRespawn", () -> events.onPlayerRespawn(EventsModule::firePlayerRespawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity spawn", "EntitySpawn", () -> events.onEntitySpawn(EventsModule::fireEntitySpawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "client entity unload", "EntityUnload", () -> events.onClientEntityUnload(EventsModule::fireClientEntityUnload));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world load", "WorldLoad", () -> events.onWorldLoad(EventsModule::fireWorldLoad));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world unload", "WorldUnload", () -> events.onWorldUnload(EventsModule::fireWorldUnload));
        if (dev.mlua.loaderapi.LoaderApi.platform().isClient()) {
            registerLoaderEvent(unsupportedEvents, "client world load", () -> events.onClientWorldLoad(EventsModule::fireWorldLoad));
            registerLoaderEvent(unsupportedEvents, "client world unload", () -> events.onClientWorldUnload(EventsModule::fireWorldUnload));
        }
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block right click", "BlockRightClick", () -> events.onBlockRightClick((player, hand, level, pos) -> fireBlockRightClick((Player) player, hand, (Level) level, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item drop", "ItemDrop", () -> events.onItemDrop((player, stack) -> MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_DROP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item pickup", "ItemPickup", () -> events.onItemPickup((player, stack) -> MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_PICKUP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item craft", "ItemCraft", () -> events.onItemCraft((player, stack) -> MLuaEventPayloads.fireItemStackEvent(MLuaEventsService.ITEM_CRAFT, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container open", "ContainerOpen", () -> events.onContainerOpen((player, container) -> MLuaEventPayloads.fireContainerEvent(MLuaEventsService.CONTAINER_OPEN, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container close", "ContainerClose", () -> events.onContainerClose((player, container) -> MLuaEventPayloads.fireContainerEvent(MLuaEventsService.CONTAINER_CLOSE, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "projectile hit", "ProjectileHit", () -> events.onProjectileHit(MLuaEventPayloads::fireProjectileHit));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "explosion", "Explosion", () -> events.onExplosion(MLuaEventPayloads::fireExplosion));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "advancement", "AdvancementCompleted", () -> events.onAdvancement(MLuaEventPayloads::fireAdvancement));
        registerLoaderEvent(unsupportedEvents, "server level tick", () -> events.onServerLevelTickEnd(MLuaPlayerStepTracker::track));
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

    public static EventBridge.CancellableResult fireEntityHurt(LivingEntity entity, DamageSource source, float amount) {
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

    public static void fireEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) {
            return;
        }
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Entity", MLuaEventContext.entity(entity));
        MLuaEventContext.addDamageSource(ctx, entity.level(), source);
        MLuaEventContext.fire(MLuaEventsService.ENTITY_DEATH, entity.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onDeath(entity, ctx);
    }

    public static EventBridge.CancellableResult fireBlockBreak(ServerPlayer player, Level level, BlockPos pos) {
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

    public static void fireBlockPlace(Level level, Entity entity, BlockPos pos) {
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

    public static EventBridge.CancellableResult fireItemUse(Player player, Object handObj, ItemStack itemStack) {
        if (player.level().isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaEventContext.player(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level())));
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Item", MLuaItemWrapper.itemTable(itemStack, MLuaRuntime.registrationForLevel(player.level())));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.ITEM_USE, player.level(), Side.SERVER, ctx);
        return MLuaEventContext.result(ctx);
    }

    public static EventBridge.CancellableResult fireEntityInteract(Player player, Object handObj, Entity target) {
        if (player.level().isClientSide() || !(target instanceof LivingEntity living)) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", MLuaEventContext.player(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level())));
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Entity", MLuaEventContext.entity(living));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.ENTITY_INTERACT, player.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onInteract(living, ctx);
        return MLuaEventContext.result(ctx);
    }

    public static void firePlayerRespawn(Object playerObj) {
        if (!(playerObj instanceof Player player)) return;
        LuaTable ctx = MLuaEventContext.create(Side.SERVER, false);
        ctx.set("Player", MLuaEventContext.player(player));
        MLuaEventContext.fire(MLuaEventsService.PLAYER_RESPAWN, player.level(), Side.SERVER, ctx);
    }

    public static void forgetPlayerStepPositions(UUID playerId) {
        MLuaPlayerStepTracker.forgetPlayer(playerId);
    }

    public static void forgetPlayerStepPositions(UUID playerId, Object runtimeOwner) {
        MLuaPlayerStepTracker.forgetPlayer(playerId, runtimeOwner);
    }

    static void forgetWorldStepPositions(ResourceLocation dimension, Object runtimeOwner) {
        MLuaPlayerStepTracker.forgetWorld(dimension, runtimeOwner);
    }

    public static void fireEntitySpawn(Object entityObj) {
        if (!(entityObj instanceof Entity entity)) return;
        LuaTable ctx = MLuaEventContext.create(entity.level(), false);
        ctx.set("Entity", MLuaEventContext.entity(entity));
        MLuaEventContext.fire(MLuaEventsService.ENTITY_SPAWN, entity.level(), entity.level().isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
    }

    public static void fireClientEntityUnload(Object entityObj) {
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

    public static void fireWorldLoad(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        LuaTable ctx = MLuaEventContext.create(level, false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(level)));
        MLuaEventContext.fire(MLuaEventsService.WORLD_LOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        MLuaRuntime.get().runWorldLifecycleEntrypoint(level, true);
    }

    public static void fireWorldUnload(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        if (!level.isClientSide()) {
            forgetWorldStepPositions(level.dimension().location(), MLuaRuntime.sessionForLevel(level));
        }
        LuaTable ctx = MLuaEventContext.create(level, false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(MLuaEventContext.dimension(level)));
        MLuaEventContext.fire(MLuaEventsService.WORLD_UNLOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        MLuaRuntime.get().runWorldLifecycleEntrypoint(level, false);
    }

    public static EventBridge.BlockInteractionResult fireBlockRightClick(Player player, Object handObj, Level level, BlockPos pos) {
        if (player == null || level == null || pos == null) return EventBridge.BlockInteractionResult.pass();
        LuaTable ctx = MLuaEventContext.create(level, true);
        ctx.set("Player", MLuaEventContext.player(player));
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Hand", LuaValue.valueOf(String.valueOf(handObj).toLowerCase()));
        MLuaEventContext.fire(MLuaEventsService.BLOCK_RIGHT_CLICK, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        return MLuaEventContext.isCancelled(ctx) ? EventBridge.BlockInteractionResult.cancelled() : EventBridge.BlockInteractionResult.pass();
    }

}
