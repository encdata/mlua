package dev.mlua.loader.impl.neoforge;

import dev.mlua.loaderapi.EventBridge;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;

public class NeoForgeEventBridge implements EventBridge {
    public NeoForgeEventBridge(IEventBus modBus) {
    }

    @Override
    public void onClientTickEnd(Runnable callback) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> callback.run());
    }

    @Override
    public void onServerTickEnd(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> callback.accept(event.getServer()));
    }

    @Override
    public void onServerLevelTickEnd(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (!event.getLevel().isClientSide()) {
                callback.accept(event.getLevel());
            }
        });
    }

    @Override
    public void onClientDisconnect(Runnable callback) {
        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> notified.set(false));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            if (notified.compareAndSet(false, true)) callback.run();
        });
    }

    @Override
    public void onPlayerJoin(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onPlayerLeave(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                callback.accept(player);
            }
        });
    }

    @Override
    public void onServerChat(ServerChatCallback callback) {
        NeoForge.EVENT_BUS.addListener((ServerChatEvent event) -> {
            ChatResult result = callback.handle(event.getPlayer(), event.getMessage().getString());
            if (result == null) {
                return;
            }
            if (result.cancel()) {
                event.setCanceled(true);
                return;
            }
            if (result.message() != null && !result.message().equals(event.getMessage().getString())) {
                event.setMessage(net.minecraft.network.chat.Component.literal(result.message()));
            }
        });
    }

    @Override
    public void onEntityHurt(EntityHurtCallback callback) {
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> callback.handle(event.getEntity(), event.getSource(), event.getNewDamage()));
    }

    @Override
    public void onEntityDeath(EntityDeathCallback callback) {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent event) -> callback.handle(event.getEntity(), event.getSource()));
    }

    @Override
    public void onBlockBreak(BlockBreakCallback callback) {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> callback.handle(event.getPlayer(), event.getLevel(), event.getPos()));
    }

    @Override
    public void onBlockPlace(BlockPlaceCallback callback) {
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> callback.handle(event.getEntity(), event.getLevel(), event.getPos()));
    }

    @Override
    public void onItemUse(ItemUseCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> callback.handle(event.getEntity(), event.getHand(), event.getItemStack()));
    }

    @Override
    public void onEntityInteract(EntityInteractCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> callback.handle(event.getEntity(), event.getHand(), event.getTarget()));
    }

    @Override
    public void onPlayerRespawn(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> callback.accept(event.getEntity()));
    }

    @Override
    public void onEntitySpawn(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getEntity());
        });
    }

    @Override
    public void onWorldLoad(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getLevel());
        });
    }

    @Override
    public void onWorldUnload(java.util.function.Consumer<Object> callback) {
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (!event.getLevel().isClientSide()) callback.accept(event.getLevel());
        });
    }

    @Override
    public void onBlockRightClick(BlockInteractionCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            BlockInteractionResult result = callback.handle(event.getEntity(), event.getHand(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityHurtEvent(CancellableEntityHurtCallback callback) {
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre event) -> {
            CancellableResult result = callback.handle(event.getEntity(), event.getSource(), event.getNewDamage());
            if (result != null && result.cancel()) event.getContainer().setNewDamage(0.0F);
        });
    }

    @Override
    public void onBlockBreakEvent(CancellableBlockCallback callback) {
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            CancellableResult result = callback.handle(event.getPlayer(), event.getLevel(), event.getPos());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemUseEvent(CancellableItemUseCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getItemStack());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onEntityInteractEvent(CancellableEntityInteractCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            CancellableResult result = callback.handle(event.getEntity(), event.getHand(), event.getTarget());
            if (result != null && result.cancel()) event.setCanceled(true);
        });
    }

    @Override
    public void onItemDrop(ItemStackCallback callback) {
        NeoForge.EVENT_BUS.addListener((ItemTossEvent event) -> callback.handle(event.getPlayer(), event.getEntity().getItem()));
    }

    @Override
    public void onItemPickup(ItemStackCallback callback) {
        throw new UnsupportedOperationException("NeoForge 1.21 does not expose a server item-pickup event.");
    }

    @Override
    public void onItemCraft(ItemStackCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.ItemCraftedEvent event) -> callback.handle(event.getEntity(), event.getCrafting()));
    }

    @Override
    public void onContainerOpen(ContainerCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Open event) -> callback.handle(event.getEntity(), event.getContainer()));
    }

    @Override
    public void onContainerClose(ContainerCallback callback) {
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> callback.handle(event.getEntity(), event.getContainer()));
    }

    @Override
    public void onProjectileHit(ProjectileCallback callback) {
        NeoForge.EVENT_BUS.addListener((ProjectileImpactEvent event) -> callback.handle(event.getProjectile(), event.getRayTraceResult()));
    }

    @Override
    public void onExplosion(ExplosionCallback callback) {
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Start event) -> {
            callback.handle(event.getLevel(), event.getExplosion().center());
        });
    }

    @Override
    public void onAdvancement(AdvancementCallback callback) {
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.AdvancementEvent.AdvancementEarnEvent event) -> {
            callback.handle(event.getEntity(), event.getAdvancement().id().toString());
        });
    }
}
