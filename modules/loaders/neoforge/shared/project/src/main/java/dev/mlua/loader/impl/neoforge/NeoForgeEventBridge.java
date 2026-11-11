package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.ClientWorldLifecycleTracker;
import dev.mlua.loaderapi.EventListenerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.Event;
import java.util.function.Consumer;

public class NeoForgeEventBridge implements EventBridge {
    private ClientWorldLifecycleTracker clientWorldTracker;
    private boolean clientWorldTrackerRegistered;
    private final EventListenerRegistry listeners = new EventListenerRegistry();

    public NeoForgeEventBridge(IEventBus modBus) {
    }

    private <T extends Event> void addListener(String hook, Object callback, Consumer<T> listener) {
        this.listeners.register(hook, callback, listener, () -> NeoForge.EVENT_BUS.addListener(listener));
    }

    @Override
    public void removeListener(String hook, Object callback) {
        if ("onClientWorldLoad".equals(hook) || "onClientWorldUnload".equals(hook)) {
            ClientWorldLifecycleTracker tracker;
            synchronized (this) {
                tracker = this.clientWorldTracker;
            }
            if (tracker != null && callback instanceof Consumer<?> consumer) {
                @SuppressWarnings("unchecked") Consumer<Object> listener = (Consumer<Object>) consumer;
                if ("onClientWorldLoad".equals(hook)) tracker.removeLoadListener(listener);
                else tracker.removeUnloadListener(listener);
            }
            return;
        }

        this.listeners.unregister(hook, callback, NeoForge.EVENT_BUS::unregister);
    }

    @Override
    public void onClientTickStart(Runnable callback) {
        this.addListener("onClientTickStart", callback, (ClientTickEvent.Pre event) -> callback.run());
    }

    @Override
    public void onClientTickEnd(Runnable callback) {
        this.addListener("onClientTickEnd", callback, (ClientTickEvent.Post event) -> callback.run());
    }

    @Override
    public void onServerTickEnd(java.util.function.Consumer<Object> callback) {
        this.addListener("onServerTickEnd", callback, (ServerTickEvent.Post event) -> callback.accept(event.getServer()));
    }

    @Override
    public void onServerLevelTickEnd(java.util.function.Consumer<Object> callback) {
        this.addListener("onServerLevelTickEnd", callback, (LevelTickEvent.Post event) -> {
            if (!event.getLevel().isClientSide()) {
                callback.accept(event.getLevel());
            }
        });
    }

    @Override
    public void onClientDisconnect(Runnable callback) {
        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
        this.addListener("onClientDisconnect", callback, (ClientPlayerNetworkEvent.LoggingIn event) -> notified.set(false));
        this.addListener("onClientDisconnect", callback, (ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (notified.compareAndSet(false, true)) callback.run();
        });
    }

    @Override
    public void onPlayerJoin(java.util.function.Consumer<Object> callback) {
        this.addListener("onPlayerJoin", callback, (PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLeave(java.util.function.Consumer<Object> callback) {
        this.addListener("onPlayerLeave", callback, (PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onServerChat(ServerChatCallback callback) {
        this.addListener("onServerChat", callback, (ServerChatEvent event) -> {
            ChatResult result = callback.handle(event.getPlayer(), event.getMessage().getString());
            if (result == null) {
                return;
            }
            if (result.cancel()) {
                event.setCanceled(true);
                return;
            }
            if (result.message() != null && !result.message().equals(event.getMessage().getString())) {
                event.setCanceled(true);
                Component replacement = Component.translatable(
                    "chat.type.text", event.getPlayer().getDisplayName(),
                    Component.literal(result.message()));
                event.getPlayer().level().getServer().getPlayerList().broadcastSystemMessage(replacement, false);
            }
        });
    }

    @Override
    public void onEntityHurt(EntityHurtCallback callback) {
        this.addListener("onEntityHurt", callback, (LivingIncomingDamageEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getSource(), event.getAmount());
        });
    }

    @Override
    public void onEntityDeath(EntityDeathCallback callback) {
        this.addListener("onEntityDeath", callback, (LivingDeathEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getSource());
        });
    }

    @Override
    public void onBlockBreak(BlockBreakCallback callback) {
        this.addListener("onBlockBreak", callback, (BlockEvent.BreakEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            callback.handle(event.getPlayer(), event.getLevel(), event.getPos());
        });
    }

    @Override
    public void onBlockPlace(BlockPlaceCallback callback) {
        this.addListener("onBlockPlace", callback, (BlockEvent.EntityPlaceEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            callback.handle(event.getEntity(), event.getLevel(), event.getPos());
        });
    }

    @Override
    public void onItemUse(ItemUseCallback callback) {
        this.addListener("onItemUse", callback, (PlayerInteractEvent.RightClickItem event) -> callback.handle(event.getEntity(), event.getHand(), event.getItemStack()));
    }

    @Override
    public void onEntityInteract(EntityInteractCallback callback) {
        this.addListener("onEntityInteract", callback, (PlayerInteractEvent.EntityInteract event) -> callback.handle(event.getEntity(), event.getHand(), event.getTarget()));
    }

    @Override
    public void onPlayerRespawn(java.util.function.Consumer<Object> callback) {
        this.addListener("onPlayerRespawn", callback, (PlayerEvent.PlayerRespawnEvent event) -> callback.accept(event.getEntity()));
    }

    @Override
    public void onEntitySpawn(java.util.function.Consumer<Object> callback) {
        this.addListener("onEntitySpawn", callback, (EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getEntity());
        });
    }

    @Override
    public void onClientEntityUnload(java.util.function.Consumer<Object> callback) {
        if (!dev.mlua.loaderapi.LoaderApi.platform().isClient()) {
            throw new UnsupportedOperationException("Client entity unload events are unavailable on a dedicated server");
        }
        this.addListener("onClientEntityUnload", callback, (EntityLeaveLevelEvent event) -> {
            if (event.getLevel().isClientSide()) callback.accept(event.getEntity());
        });
    }

    @Override
    public void onWorldLoad(java.util.function.Consumer<Object> callback) {
        this.addListener("onWorldLoad", callback, (LevelEvent.Load event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getLevel());
        });
    }

    @Override
    public void onWorldUnload(java.util.function.Consumer<Object> callback) {
        this.addListener("onWorldUnload", callback, (LevelEvent.Unload event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getLevel());
        });
    }

    @Override
    public void onClientWorldLoad(java.util.function.Consumer<Object> callback) {
        if (!dev.mlua.loaderapi.LoaderApi.platform().isClient()) throw new UnsupportedOperationException("Client world load is unavailable on a dedicated server");
        this.clientWorldTracker().onLoad(callback);
        this.registerClientWorldTracker();
    }

    @Override
    public void onClientWorldUnload(java.util.function.Consumer<Object> callback) {
        if (!dev.mlua.loaderapi.LoaderApi.platform().isClient()) throw new UnsupportedOperationException("Client world unload is unavailable on a dedicated server");
        this.clientWorldTracker().onUnload(callback);
        this.registerClientWorldTracker();
    }

    private synchronized void registerClientWorldTracker() {
        if (this.clientWorldTrackerRegistered) return;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> this.clientWorldTracker().tick());
        this.clientWorldTrackerRegistered = true;
    }

    private synchronized ClientWorldLifecycleTracker clientWorldTracker() {
        if (this.clientWorldTracker == null) {
            this.clientWorldTracker = new ClientWorldLifecycleTracker(() -> Minecraft.getInstance().level);
        }
        return this.clientWorldTracker;
    }

    @Override
    public void onBlockRightClick(BlockInteractionCallback callback) {
        this.addListener("onBlockRightClick", callback, (PlayerInteractEvent.RightClickBlock event) -> {
            BlockInteractionResult result = callback.handle(event.getEntity(), event.getHand(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityHurtEvent(CancellableEntityHurtCallback callback) {
        this.addListener("onEntityHurtEvent", callback, (LivingIncomingDamageEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getSource(), event.getAmount());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onBlockBreakEvent(CancellableBlockCallback callback) {
        this.addListener("onBlockBreakEvent", callback, (BlockEvent.BreakEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            CancellableResult result = callback.handle(event.getPlayer(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemUseEvent(CancellableItemUseCallback callback) {
        this.addListener("onItemUseEvent", callback, (PlayerInteractEvent.RightClickItem event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getItemStack());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityInteractEvent(CancellableEntityInteractCallback callback) {
        this.addListener("onEntityInteractEvent", callback, (PlayerInteractEvent.EntityInteract event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getTarget());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemDrop(ItemStackCallback callback) {
        this.addListener("onItemDrop", callback, (ItemTossEvent event) -> callback.handle(event.getPlayer(), event.getEntity().getItem()));
    }

    @Override
    public void onItemPickup(ItemStackCallback callback) {
        this.addListener("onItemPickup", callback, (net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post event) -> {
            if (event.getPlayer().level().isClientSide()) return;
            ItemStack original = event.getOriginalStack();
            ItemStack remaining = event.getCurrentStack();
            int pickedCount = original.getCount() - (remaining.isEmpty() ? 0 : remaining.getCount());
            if (pickedCount <= 0) return;
            ItemStack picked = original.copy();
            picked.setCount(pickedCount);
            callback.handle(event.getPlayer(), picked);
        });
    }

    @Override
    public void onItemCraft(ItemStackCallback callback) {
        this.addListener("onItemCraft", callback, (PlayerEvent.ItemCraftedEvent event) -> callback.handle(event.getEntity(), event.getCrafting()));
    }

    @Override
    public void onContainerOpen(ContainerCallback callback) {
        this.addListener("onContainerOpen", callback, (PlayerContainerEvent.Open event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getContainer());
        });
    }

    @Override
    public void onContainerClose(ContainerCallback callback) {
        this.addListener("onContainerClose", callback, (PlayerContainerEvent.Close event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getContainer());
        });
    }

    @Override
    public void onProjectileHit(ProjectileCallback callback) {
        this.addListener("onProjectileHit", callback, (ProjectileImpactEvent event) -> callback.handle(event.getProjectile(), event.getRayTraceResult()));
    }

    @Override
    public void onExplosion(ExplosionCallback callback) {
        this.addListener("onExplosion", callback, (ExplosionEvent.Start event) -> {
            callback.handle(event.getLevel(), event.getExplosion().center());
        });
    }

    @Override
    public void onAdvancement(AdvancementCallback callback) {
        this.addListener("onAdvancement", callback, (net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) -> {
            callback.handle(event.getEntity(), event.getAdvancement().id().toString());
        });
    }
}
