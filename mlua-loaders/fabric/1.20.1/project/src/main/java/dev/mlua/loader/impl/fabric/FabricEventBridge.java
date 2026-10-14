package dev.mlua.loader.impl.fabric;

import dev.mlua.loaderapi.EventBridge;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class FabricEventBridge implements EventBridge {
    private static final List<ProjectileCallback> projectileHitCallbacks = new CopyOnWriteArrayList<>();
    private final List<BlockPlaceCallback> blockPlaceCallbacks = new ArrayList<>();

    @Override
    public void onClientTickEnd(Runnable callback) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> callback.run());
    }

    @Override
    public void onServerTickEnd(Consumer<Object> callback) {
        ServerTickEvents.END_SERVER_TICK.register(server -> callback.accept(server));
    }

    @Override
    public void onServerLevelTickEnd(Consumer<Object> callback) {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!world.isClientSide()) {
                callback.accept(world);
            }
        });
    }

    @Override
    public void onClientDisconnect(Runnable callback) {
        AtomicBoolean notified = new AtomicBoolean();
        Runnable notifyOnce = () -> {
            if (notified.compareAndSet(false, true)) callback.run();
        };
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> notified.set(false));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> notifyOnce.run());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> notifyOnce.run());
    }

    @Override
    public void onPlayerJoin(Consumer<Object> callback) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> callback.accept(handler.getPlayer()));
    }

    @Override
    public void onPlayerLeave(Consumer<Object> callback) {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> callback.accept(handler.getPlayer()));
    }

    @Override
    public void onServerChat(ServerChatCallback callback) {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            ChatResult result = callback.handle(sender, message.signedBody().content());
            if (result == null) {
                return true;
            }
            if (result.cancel()) {
                return false;
            }
            String updated = result.message();
            if (updated != null && !updated.equals(message.signedBody().content())) {
                sender.getServer().getPlayerList().broadcastSystemMessage(Component.literal(updated), false);
                return false;
            }
            return true;
        });
    }

    @Override
    public void onEntityHurt(EntityHurtCallback callback) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            callback.handle(entity, source, amount);
            return true;
        });
    }

    @Override
    public void onEntityDeath(EntityDeathCallback callback) {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> callback.handle(entity, source));
    }

    @Override
    public void onBlockBreak(BlockBreakCallback callback) {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            callback.handle(player, world, pos);
            return true;
        });
    }

    @Override
    public void onBlockPlace(BlockPlaceCallback callback) {
        blockPlaceCallbacks.add(callback);
    }

    @Override
    public void onItemUse(ItemUseCallback callback) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            callback.handle(player, hand, player.getItemInHand(hand));
            return InteractionResultHolder.pass(ItemStack.EMPTY);
        });
    }

    @Override
    public void onEntityInteract(EntityInteractCallback callback) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            callback.handle(player, hand, entity);
            return InteractionResult.PASS;
        });
    }

    @Override
    public void onPlayerRespawn(Consumer<Object> callback) {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> callback.accept(newPlayer));
    }

    @Override
    public void onEntitySpawn(Consumer<Object> callback) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> callback.accept(entity));
    }

    @Override
    public void onWorldLoad(Consumer<Object> callback) {
        ServerWorldEvents.LOAD.register((server, world) -> callback.accept(world));
    }

    @Override
    public void onWorldUnload(Consumer<Object> callback) {
        ServerWorldEvents.UNLOAD.register((server, world) -> callback.accept(world));
    }

    @Override
    public void onBlockRightClick(BlockInteractionCallback callback) {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockInteractionResult result = callback.handle(player, hand, world, hit.getBlockPos());
            return result != null && result.cancel() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    @Override
    public void onEntityHurtEvent(CancellableEntityHurtCallback callback) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            CancellableResult result = callback.handle(entity, source, amount);
            return result == null || !result.cancel();
        });
    }

    @Override
    public void onBlockBreakEvent(CancellableBlockCallback callback) {
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            CancellableResult result = callback.handle(player, world, pos);
            return result == null || !result.cancel();
        });
    }

    @Override
    public void onItemUseEvent(CancellableItemUseCallback callback) {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            CancellableResult result = callback.handle(player, hand, player.getItemInHand(hand));
            return result != null && result.cancel() ? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }

    @Override
    public void onEntityInteractEvent(CancellableEntityInteractCallback callback) {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            CancellableResult result = callback.handle(player, hand, entity);
            return result != null && result.cancel() ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }

    public void fireBlockPlace(Entity entity, Level level, BlockPos pos) {
        for (BlockPlaceCallback callback : blockPlaceCallbacks) {
            callback.handle(entity, level, pos);
        }
    }

    @Override
    public void onProjectileHit(ProjectileCallback callback) {
        projectileHitCallbacks.add(callback);
    }

    public static void fireProjectileHit(Object projectile, Object hitResult) {
        for (ProjectileCallback callback : projectileHitCallbacks) {
            callback.handle(projectile, hitResult);
        }
    }
}
