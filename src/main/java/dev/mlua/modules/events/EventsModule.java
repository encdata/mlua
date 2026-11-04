package dev.mlua.modules.events;

import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaRuntimeSession;
import dev.mlua.loaderapi.EventBridge;
import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.events.service.MLuaEventsService;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
        MLuaEventsService.PRE_RENDER.fireInlineOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
        MLuaEventsService.RENDER_STEPPED.fireInlineOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
    }

    @Override
    public void onRenderFrameEnd(MLuaRegistration runtime, Object runtimeSession, double deltaTime) {
        if (runtimeSession instanceof MLuaRuntimeSession session) {
            MLuaEventsService.POST_RENDER.fireInlineOnSession(session, Side.CLIENT, LuaValue.valueOf(deltaTime));
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
            "Runs synchronously on the client render thread at the start of each frame. The callback receives frame duration in seconds directly, not Minecraft's partial-tick interpolation fraction, and must not yield.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.PreRender", "deltaTime: number",
            "Runs synchronously on the client render thread immediately before RenderStepped. The callback receives frame duration in seconds and must not yield.",
            "client:Client"));
        docs.register(MLuaDocBuilder.Registration.event("Events", "Events.PostRender", "deltaTime: number",
            "Runs synchronously on the client render thread after Minecraft finishes drawing the frame. The callback receives frame duration in seconds and must not yield.",
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
        MLuaEventBridgeHandlers.register(events);
    }

    public static EventBridge.CancellableResult fireEntityHurt(LivingEntity entity, DamageSource source, float amount) {
        return MLuaEventBridgeHandlers.fireEntityHurt(entity, source, amount);
    }

    public static void fireEntityDeath(LivingEntity entity, DamageSource source) {
        MLuaEventBridgeHandlers.fireEntityDeath(entity, source);
    }

    public static EventBridge.CancellableResult fireBlockBreak(ServerPlayer player, Level level, BlockPos pos) {
        return MLuaEventBridgeHandlers.fireBlockBreak(player, level, pos);
    }

    public static void fireBlockPlace(Level level, Entity entity, BlockPos pos) {
        MLuaEventBridgeHandlers.fireBlockPlace(level, entity, pos);
    }

    public static EventBridge.CancellableResult fireItemUse(Player player, Object handObj, ItemStack itemStack) {
        return MLuaEventBridgeHandlers.fireItemUse(player, handObj, itemStack);
    }

    public static EventBridge.CancellableResult fireEntityInteract(Player player, Object handObj, Entity target) {
        return MLuaEventBridgeHandlers.fireEntityInteract(player, handObj, target);
    }

    public static void firePlayerRespawn(Object playerObj) {
        MLuaEventBridgeHandlers.firePlayerRespawn(playerObj);
    }

    public static void forgetPlayerStepPositions(UUID playerId) {
        MLuaPlayerStepTracker.forgetPlayer(playerId);
    }

    public static void forgetPlayerStepPositions(UUID playerId, Object runtimeOwner) {
        MLuaPlayerStepTracker.forgetPlayer(playerId, runtimeOwner);
    }

    static void forgetWorldStepPositions(String dimension, Object runtimeOwner) {
        MLuaPlayerStepTracker.forgetWorld(dimension, runtimeOwner);
    }

    public static void fireEntitySpawn(Object entityObj) {
        MLuaEventBridgeHandlers.fireEntitySpawn(entityObj);
    }

    public static void fireClientEntityUnload(Object entityObj) {
        MLuaEventBridgeHandlers.fireClientEntityUnload(entityObj);
    }

    public static void fireWorldLoad(Object levelObj) {
        MLuaEventBridgeHandlers.fireWorldLoad(levelObj);
    }

    public static void fireWorldUnload(Object levelObj) {
        MLuaEventBridgeHandlers.fireWorldUnload(levelObj);
    }

    public static EventBridge.BlockInteractionResult fireBlockRightClick(
            Player player, Object handObj, Level level, BlockPos pos) {
        return MLuaEventBridgeHandlers.fireBlockRightClick(player, handObj, level, pos);
    }

}
