package dev.mlua.loader.impl.forge;

import dev.mlua.loaderapi.EventBridge;
import dev.mlua.loaderapi.ClientWorldLifecycleTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public class ForgeEventBridge implements EventBridge {
    private ClientWorldLifecycleTracker clientWorldTracker;
    private boolean clientWorldTrackerRegistered;

    public ForgeEventBridge(IEventBus modBus) {
    }

    @Override
    public void onClientTickStart(Runnable callback) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.START) callback.run();
        });
    }

    @Override
    public void onClientTickEnd(Runnable callback) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                callback.run();
            }
        });
    }

    @Override
    public void onServerTickEnd(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                callback.accept(event.getServer());
            }
        });
    }

    @Override
    public void onServerLevelTickEnd(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.LevelTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
                callback.accept(event.level);
            }
        });
    }

    @Override
    public void onClientDisconnect(Runnable callback) {
        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> notified.set(false));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (notified.compareAndSet(false, true)) callback.run();
        });
    }

    @Override
    public void onPlayerJoin(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLeave(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onServerChat(ServerChatCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
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
                event.getPlayer().getServer().getPlayerList().broadcastSystemMessage(replacement, false);
            }
        });
    }

    @Override
    public void onEntityHurt(EntityHurtCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getSource(), event.getAmount());
        });
    }

    @Override
    public void onEntityDeath(EntityDeathCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getSource());
        });
    }

    @Override
    public void onBlockBreak(BlockBreakCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            callback.handle(event.getPlayer(), event.getLevel(), event.getPos());
        });
    }

    @Override
    public void onBlockPlace(BlockPlaceCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            callback.handle(event.getEntity(), event.getLevel(), event.getPos());
        });
    }

    @Override
    public void onItemUse(ItemUseCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> callback.handle(event.getEntity(), event.getHand(), event.getItemStack()));
    }

    @Override
    public void onEntityInteract(EntityInteractCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> callback.handle(event.getEntity(), event.getHand(), event.getTarget()));
    }

    @Override
    public void onPlayerRespawn(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> callback.accept(event.getEntity()));
    }

    @Override
    public void onEntitySpawn(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getEntity());
        });
    }

    @Override
    public void onClientEntityUnload(java.util.function.Consumer<Object> callback) {
        if (!dev.mlua.loaderapi.LoaderApi.platform().isClient()) {
            throw new UnsupportedOperationException("Client entity unload events are unavailable on a dedicated server");
        }
        MinecraftForge.EVENT_BUS.addListener((EntityLeaveLevelEvent event) -> {
            if (event.getLevel().isClientSide()) callback.accept(event.getEntity());
        });
    }

    @Override
    public void onWorldLoad(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getLevel());
        });
    }

    @Override
    public void onWorldUnload(java.util.function.Consumer<Object> callback) {
        MinecraftForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
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
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) this.clientWorldTracker().tick();
        });
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
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            BlockInteractionResult result = callback.handle(event.getEntity(), event.getHand(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityHurtEvent(CancellableEntityHurtCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getSource(), event.getAmount());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onBlockBreakEvent(CancellableBlockCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (event.getLevel().isClientSide()) return;
            CancellableResult result = callback.handle(event.getPlayer(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemUseEvent(CancellableItemUseCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getItemStack());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityInteractEvent(CancellableEntityInteractCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (event.getEntity().level().isClientSide()) return;
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getTarget());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemDrop(ItemStackCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((ItemTossEvent event) -> callback.handle(event.getPlayer(), event.getEntity().getItem()));
    }

    @Override
    public void onItemPickup(ItemStackCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.ItemPickupEvent event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getStack());
        });
    }

    @Override
    public void onItemCraft(ItemStackCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent event) -> callback.handle(event.getEntity(), event.getCrafting()));
    }

    @Override
    public void onContainerOpen(ContainerCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerContainerEvent.Open event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getContainer());
        });
    }

    @Override
    public void onContainerClose(ContainerCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity().level().isClientSide()) return;
            callback.handle(event.getEntity(), event.getContainer());
        });
    }

    @Override
    public void onProjectileHit(ProjectileCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((ProjectileImpactEvent event) -> callback.handle(event.getProjectile(), event.getRayTraceResult()));
    }

    @Override
    public void onExplosion(ExplosionCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((ExplosionEvent.Start event) -> {
            //--version 1.20.1
            callback.handle(event.getLevel(), event.getExplosion().getPosition());
            //--endversion
            //--version 1.21.1
            callback.handle(event.getLevel(), event.getExplosion().center());
            //--endversion
        });
    }

    @Override
    public void onAdvancement(AdvancementCallback callback) {
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) -> {
            //--version 1.20.1
            callback.handle(event.getEntity(), event.getAdvancement().getId().toString());
            //--endversion
            //--version 1.21.1
            callback.handle(event.getEntity(), event.getAdvancement().id().toString());
            //--endversion
        });
    }
}
