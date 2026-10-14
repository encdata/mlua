package dev.mlua.modules.events;

import dev.mlua.module.MLuaDocBuilder;

import dev.mlua.minecraft.binding.MLuaBlockWrapper;
import dev.mlua.minecraft.binding.MLuaItemWrapper;
import dev.mlua.minecraft.binding.MLuaLevelWrapper;
import dev.mlua.minecraft.scripted.MLuaBlockCallbackDispatcher;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.runtime.entity.MLuaEntityComponentManager;
import dev.mlua.core.signal.MLuaSignal;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.core.MLuaLogger;
import dev.mlua.common.enums.Side;
import dev.mlua.modules.events.Service.MLuaEventsService;
import dev.mlua.modules.players.Class.MLuaEntityWrapper;
import dev.mlua.modules.players.Class.MLuaPlayerWrapper;
import dev.mlua.mc.impl.entity.MCServerPlayerImpl;
import dev.mlua.mc.impl.entity.MCLocalPlayerImpl;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

public class EventsModule implements MLuaModule {
    record PlayerStepKey(Object runtimeOwner, UUID playerId, ResourceLocation dimension) { }
    static final Map<PlayerStepKey, Long> LAST_PLAYER_STEP_POSITIONS = new ConcurrentHashMap<>();

    public static void clearRuntime(Object runtimeOwner) {
        LAST_PLAYER_STEP_POSITIONS.keySet().removeIf(key -> key.runtimeOwner() == runtimeOwner);
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
        MLuaEventsService.PRE_RENDER.fireOnSession(session, Side.CLIENT, LuaValue.NONE);
        MLuaEventsService.RENDER_STEPPED.fireOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
    }

    @Override
    public void onRenderFrameEnd(MLuaRegistration runtime, Object runtimeSession) {
        if (runtimeSession instanceof MLuaRuntimeSession session) {
            MLuaEventsService.POST_RENDER.fireOnSession(session, Side.CLIENT, LuaValue.NONE);
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
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:BlockBreak(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:BlockPlace(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityHurt(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityDeath(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:EntityInteract(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.signature("Events", "Events:ItemUse(callback: function)", null));
        docs.register(MLuaDocBuilder.Registration.service("Events", "MLuaEventsService",
            "Provides lifecycle and gameplay signals. Each signal receives one event table containing the values available for that event.",
            "services/events.html"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.Explosion", "event: { Level: Level, Dimension: string, Position: Vector3 }",
            "Fires when an explosion starts on the server. Position is the explosion center in world coordinates.",
            "server:Server"));
        docs.register(MLuaDocBuilder.Registration.guideStep("Events", "Find event signals available on this loader",
            "GetSignalNames lists registered signals. GetUnavailableSignals lists names that this loader cannot currently provide.",
            "local Events = Minecraft:GetService(\"Events\")\nlocal unavailable = {}\n\nfor _, name in ipairs(Events:GetUnavailableSignals()) do\n    unavailable[name] = true\nend\n\nfor _, name in ipairs(Events:GetSignalNames()) do\n    if not unavailable[name] then\n        print(name)\n    end\nend"));
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
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block break", "BlockBreak", () -> events.onBlockBreakEvent((player, level, pos) -> fireBlockBreak((ServerPlayer) player, (Level) level, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block place", "BlockPlace", () -> events.onBlockPlace((entity, level, pos) -> fireBlockPlace((Level) level, (Entity) entity, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item use", "ItemUse", () -> events.onItemUseEvent((player, hand, itemStack) -> fireItemUse((Player) player, hand, (ItemStack) itemStack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity interaction", "EntityInteract", () -> events.onEntityInteractEvent((player, hand, target) -> fireEntityInteract((Player) player, hand, (Entity) target)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "player respawn", "PlayerRespawn", () -> events.onPlayerRespawn(EventsModule::firePlayerRespawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "entity spawn", "EntitySpawn", () -> events.onEntitySpawn(EventsModule::fireEntitySpawn));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world load", "WorldLoad", () -> events.onWorldLoad(EventsModule::fireWorldLoad));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "world unload", "WorldUnload", () -> events.onWorldUnload(EventsModule::fireWorldUnload));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "block right click", "BlockRightClick", () -> events.onBlockRightClick((player, hand, level, pos) -> fireBlockRightClick((Player) player, hand, (Level) level, (BlockPos) pos)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item drop", "ItemDrop", () -> events.onItemDrop((player, stack) -> fireItemStackEvent(MLuaEventsService.ITEM_DROP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item pickup", "ItemPickup", () -> events.onItemPickup((player, stack) -> fireItemStackEvent(MLuaEventsService.ITEM_PICKUP, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "item craft", "ItemCraft", () -> events.onItemCraft((player, stack) -> fireItemStackEvent(MLuaEventsService.ITEM_CRAFT, player, stack)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container open", "ContainerOpen", () -> events.onContainerOpen((player, container) -> fireContainerEvent(MLuaEventsService.CONTAINER_OPEN, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "container close", "ContainerClose", () -> events.onContainerClose((player, container) -> fireContainerEvent(MLuaEventsService.CONTAINER_CLOSE, player, container)));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "projectile hit", "ProjectileHit", () -> events.onProjectileHit(EventsModule::fireProjectileHit));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "explosion", "Explosion", () -> events.onExplosion(EventsModule::fireExplosion));
        registerLoaderEvent(unsupportedEvents, unavailableSignals, "advancement", "AdvancementCompleted", () -> events.onAdvancement(EventsModule::fireAdvancement));
        registerLoaderEvent(unsupportedEvents, "server level tick", () -> events.onServerLevelTickEnd(levelObj -> trackPlayerStepOn(levelObj)));
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
        LuaTable ctx = eventContext("Server", true);
        ctx.set("Entity", entityTable(entity));
        ctx.set("Amount", LuaValue.valueOf(amount));
        addSourceContext(ctx, entity.level(), source);
        fireSignal(MLuaEventsService.ENTITY_HURT, entity.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onHurt(entity, ctx);
        return result(ctx);
    }

    public static void fireEntityDeath(LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) {
            return;
        }
        LuaTable ctx = eventContext("Server", false);
        ctx.set("Entity", entityTable(entity));
        addSourceContext(ctx, entity.level(), source);
        fireSignal(MLuaEventsService.ENTITY_DEATH, entity.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onDeath(entity, ctx);
    }

    public static EventBridge.CancellableResult fireBlockBreak(ServerPlayer player, Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        MLuaBlockCallbackDispatcher.fireOnBreak(level, pos, player);
        LuaTable ctx = eventContext("Server", true);
        ctx.set("Player", playerTable(player));
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        fireSignal(MLuaEventsService.BLOCK_BREAK, level, Side.SERVER, ctx);
        return result(ctx);
    }

    public static void fireBlockPlace(Level level, Entity entity, BlockPos pos) {
        if (level.isClientSide()) {
            return;
        }
        if (entity instanceof Player player) {
            MLuaBlockCallbackDispatcher.fireOnPlace(level, pos, player);
        }
        LuaTable ctx = eventContext("Server", false);
        if (entity instanceof ServerPlayer player) {
            ctx.set("Player", playerTable(player));
        } else if (entity instanceof LivingEntity living) {
            ctx.set("Entity", entityTable(living));
        }
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        fireSignal(MLuaEventsService.BLOCK_PLACE, level, Side.SERVER, ctx);
    }

    public static EventBridge.CancellableResult fireItemUse(Player player, Object handObj, ItemStack itemStack) {
        if (player.level().isClientSide()) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = eventContext("Server", true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", playerTable(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level())));
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Item", MLuaItemWrapper.itemTable(itemStack, MLuaRuntime.registrationForLevel(player.level())));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        fireSignal(MLuaEventsService.ITEM_USE, player.level(), Side.SERVER, ctx);
        return result(ctx);
    }

    public static EventBridge.CancellableResult fireEntityInteract(Player player, Object handObj, Entity target) {
        if (player.level().isClientSide() || !(target instanceof LivingEntity living)) {
            return EventBridge.CancellableResult.pass();
        }
        InteractionHand hand = (InteractionHand) handObj;
        LuaTable ctx = eventContext("Server", true);
        if (player instanceof ServerPlayer serverPlayer) {
            ctx.set("Player", playerTable(serverPlayer));
        } else if (player instanceof LocalPlayer localPlayer) {
            ctx.set("Player", MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), MLuaRuntime.registrationForLevel(player.level())));
        } else {
            return EventBridge.CancellableResult.pass();
        }
        ctx.set("Entity", entityTable(living));
        ctx.set("Hand", LuaValue.valueOf(hand.name().toLowerCase()));
        fireSignal(MLuaEventsService.ENTITY_INTERACT, player.level(), Side.SERVER, ctx);
        MLuaEntityComponentManager.onInteract(living, ctx);
        return result(ctx);
    }

    public static void firePlayerRespawn(Object playerObj) {
        if (!(playerObj instanceof Player player)) return;
        LuaTable ctx = eventContext("Server", false);
        ctx.set("Player", playerTable(player));
        fireSignal(MLuaEventsService.PLAYER_RESPAWN, player.level(), Side.SERVER, ctx);
    }

    public static void forgetPlayerStepPositions(UUID playerId) {
        if (playerId != null) LAST_PLAYER_STEP_POSITIONS.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public static void forgetPlayerStepPositions(UUID playerId, Object runtimeOwner) {
        if (playerId != null) LAST_PLAYER_STEP_POSITIONS.keySet().removeIf(
                key -> key.runtimeOwner() == runtimeOwner && key.playerId().equals(playerId));
    }

    static void forgetWorldStepPositions(ResourceLocation dimension, Object runtimeOwner) {
        if (dimension != null) LAST_PLAYER_STEP_POSITIONS.keySet().removeIf(
                key -> key.runtimeOwner() == runtimeOwner && key.dimension().equals(dimension));
    }

    public static void fireEntitySpawn(Object entityObj) {
        if (!(entityObj instanceof Entity entity)) return;
        LuaTable ctx = eventContext(entity.level().isClientSide() ? "Client" : "Server", false);
        ctx.set("Entity", entityTable(entity));
        fireSignal(MLuaEventsService.ENTITY_SPAWN, entity.level(), entity.level().isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
    }

    public static void fireWorldLoad(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        LuaTable ctx = eventContext(level.isClientSide() ? "Client" : "Server", false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(dimensionName(level)));
        fireSignal(MLuaEventsService.WORLD_LOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
    }

    public static void fireWorldUnload(Object levelObj) {
        if (!(levelObj instanceof Level level)) return;
        if (!level.isClientSide()) {
            forgetWorldStepPositions(level.dimension().location(), MLuaRuntime.sessionForLevel(level));
        }
        LuaTable ctx = eventContext(level.isClientSide() ? "Client" : "Server", false);
        ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Dimension", LuaValue.valueOf(dimensionName(level)));
        fireSignal(MLuaEventsService.WORLD_UNLOAD, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
    }

    public static EventBridge.BlockInteractionResult fireBlockRightClick(Player player, Object handObj, Level level, BlockPos pos) {
        if (player == null || level == null || pos == null) return EventBridge.BlockInteractionResult.pass();
        LuaTable ctx = eventContext(level.isClientSide() ? "Client" : "Server", true);
        ctx.set("Player", playerTable(player));
        ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
        ctx.set("Hand", LuaValue.valueOf(String.valueOf(handObj).toLowerCase()));
        fireSignal(MLuaEventsService.BLOCK_RIGHT_CLICK, level, level.isClientSide() ? Side.CLIENT : Side.SERVER, ctx);
        return isCancelled(ctx) ? EventBridge.BlockInteractionResult.cancelled() : EventBridge.BlockInteractionResult.pass();
    }

    private static LuaTable eventContext(String side, boolean canCancel) {
        LuaTable ctx = new LuaTable();
        ctx.set("Side", LuaValue.valueOf(side));
        ctx.set("Cancelled", LuaValue.FALSE);
        ctx.set("CanCancel", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(canCancel); }
        });
        ctx.set("IsCancelled", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(isCancelled(ctx)); }
        });
        ctx.set("Cancel", new ZeroArgFunction() {
            @Override public LuaValue call() {
                if (canCancel) ctx.set("Cancelled", LuaValue.TRUE);
                return LuaValue.NONE;
            }
        });
        return ctx;
    }

    private static boolean isCancelled(LuaTable ctx) {
        return ctx.get("Cancelled").toboolean();
    }

    private static EventBridge.CancellableResult result(LuaTable ctx) {
        return isCancelled(ctx) ? EventBridge.CancellableResult.cancelled() : EventBridge.CancellableResult.pass();
    }

    private static LuaTable playerTable(Player player) {
        var registration = MLuaRuntime.registrationForLevel(player.level());
        if (player instanceof ServerPlayer serverPlayer) {
            return MLuaPlayerWrapper.playerTable(MCServerPlayerImpl.fromReal(serverPlayer), registration);
        }
        if (player instanceof LocalPlayer localPlayer) {
            return MLuaPlayerWrapper.localPlayerTable(MCLocalPlayerImpl.fromReal(localPlayer), registration);
        }
        return MLuaEntityWrapper.entityTable(player, registration);
    }

    private static LuaTable entityTable(Entity entity) {
        return MLuaEntityWrapper.entityTable(entity, MLuaRuntime.registrationForLevel(entity.level()));
    }

    private static String dimensionName(Level level) {
        return level.dimension().location().toString();
    }

    private static void fireItemStackEvent(MLuaSignal signal, Object playerObj, Object stackObj) {
        LuaTable ctx = eventContext("Server", false);
        if (playerObj instanceof Player player) ctx.set("Player", playerTable(player));
        Level level = playerObj instanceof Player player ? player.level() : null;
        if (stackObj instanceof ItemStack stack) ctx.set("Item", MLuaItemWrapper.itemTable(stack, level != null ? MLuaRuntime.registrationForLevel(level) : MLuaRuntime.get()));
        else if (stackObj != null) ctx.set("Item", LuaValue.userdataOf(stackObj));
        fireSignal(signal, level, Side.SERVER, ctx);
    }

    private static void fireContainerEvent(MLuaSignal signal, Object playerObj, Object container) {
        LuaTable ctx = eventContext("Server", false);
        if (playerObj instanceof Player player) ctx.set("Player", playerTable(player));
        if (container != null) ctx.set("Container", LuaValue.userdataOf(container));
        Level level = playerObj instanceof Player player ? player.level() : null;
        fireSignal(signal, level, Side.SERVER, ctx);
    }

    private static void fireProjectileHit(Object projectileObj, Object hitResult) {
        LuaTable ctx = eventContext("Server", false);
        if (projectileObj instanceof Entity entity) ctx.set("Projectile", entityTable(entity));
        if (hitResult != null) ctx.set("HitResult", LuaValue.userdataOf(hitResult));
        Level level = projectileObj instanceof Entity entity ? entity.level() : null;
        fireSignal(MLuaEventsService.PROJECTILE_HIT, level, Side.SERVER, ctx);
    }

    private static void fireExplosion(Object levelObj, Object position) {
        LuaTable ctx = eventContext("Server", false);
        if (levelObj instanceof Level level) {
            ctx.set("Level", MLuaLevelWrapper.levelTable(level, MLuaRuntime.registrationForLevel(level)));
            ctx.set("Dimension", LuaValue.valueOf(dimensionName(level)));
        }
        if (position instanceof BlockPos pos) {
            if (levelObj instanceof Level level) {
                ctx.set("Block", MLuaBlockWrapper.blockTable(level, pos, MLuaRuntime.registrationForLevel(level)));
            }
            ctx.set("Position", MLuaPlayerWrapper.positionTable(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        } else if (position instanceof Vec3 vector) {
            ctx.set("Position", MLuaPlayerWrapper.positionTable(vector.x, vector.y, vector.z));
        }
        fireSignal(MLuaEventsService.EXPLOSION, levelObj instanceof Level level ? level : null, Side.SERVER, ctx);
    }

    private static void fireAdvancement(Object playerObj, String advancementId) {
        LuaTable ctx = eventContext("Server", false);
        if (playerObj instanceof Player player) ctx.set("Player", playerTable(player));
        ctx.set("Advancement", LuaValue.valueOf(advancementId));
        fireSignal(MLuaEventsService.ADVANCEMENT, playerObj instanceof Player player ? player.level() : null, Side.SERVER, ctx);
    }

    private static void trackPlayerStepOn(Object levelObj) {
        if (!(levelObj instanceof ServerLevel level)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            BlockPos blockPos = player.blockPosition().below();
            Object runtimeOwner = MLuaRuntime.sessionForLevel(level);
            PlayerStepKey key = new PlayerStepKey(runtimeOwner, player.getUUID(), level.dimension().location());
            long currentPos = blockPos.asLong();
            Long previousPos = LAST_PLAYER_STEP_POSITIONS.put(key, currentPos);
            if (previousPos == null || previousPos.longValue() != currentPos) {
                MLuaBlockCallbackDispatcher.fireOnStepOn(level, blockPos, player);
            }
        }
    }

    private static void fireSignal(MLuaSignal signal, Level level, Side side, LuaTable event) {
        signal.fireOnSession(MLuaRuntime.sessionForLevel(level), side, event);
    }

    private static void addSourceContext(LuaTable ctx, Level level, DamageSource source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ctx.set("Player", playerTable(player));
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            ctx.set("Attacker", entityTable(attacker));
        }
        var sourceKey = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getKey(source.type());
        ctx.set("Source", LuaValue.valueOf(sourceKey != null ? sourceKey.toString() : "generic"));
    }
}
