package dev.mlua.loaderapi;

import java.util.function.Consumer;

/**
 * Loader-facing event hooks. Optional hooks must throw when unavailable instead of
 * silently discarding a callback; shared modules can then report loader gaps clearly.
 */
public interface EventBridge {
    void onClientTickEnd(Runnable callback);
    void onServerTickEnd(Consumer<Object> callback);
    void onServerLevelTickEnd(Consumer<Object> callback);
    void onClientDisconnect(Runnable callback);
    void onPlayerJoin(Consumer<Object> callback);
    void onPlayerLeave(Consumer<Object> callback);
    void onServerChat(ServerChatCallback callback);
    void onEntityHurt(EntityHurtCallback callback);
    void onEntityDeath(EntityDeathCallback callback);
    void onBlockBreak(BlockBreakCallback callback);
    void onBlockPlace(BlockPlaceCallback callback);
    void onItemUse(ItemUseCallback callback);
    void onEntityInteract(EntityInteractCallback callback);

    default void onPlayerRespawn(Consumer<Object> callback) { throw unsupported("onPlayerRespawn"); }
    default void onEntitySpawn(Consumer<Object> callback) { throw unsupported("onEntitySpawn"); }
    default void onWorldLoad(Consumer<Object> callback) { throw unsupported("onWorldLoad"); }
    default void onWorldUnload(Consumer<Object> callback) { throw unsupported("onWorldUnload"); }
    default void onBlockRightClick(BlockInteractionCallback callback) { throw unsupported("onBlockRightClick"); }
    default void onEntityHurtEvent(CancellableEntityHurtCallback callback) { throw unsupported("onEntityHurtEvent"); }
    default void onBlockBreakEvent(CancellableBlockCallback callback) { throw unsupported("onBlockBreakEvent"); }
    default void onItemUseEvent(CancellableItemUseCallback callback) { throw unsupported("onItemUseEvent"); }
    default void onEntityInteractEvent(CancellableEntityInteractCallback callback) { throw unsupported("onEntityInteractEvent"); }
    default void onItemDrop(ItemStackCallback callback) { throw unsupported("onItemDrop"); }
    default void onItemPickup(ItemStackCallback callback) { throw unsupported("onItemPickup"); }
    default void onItemCraft(ItemStackCallback callback) { throw unsupported("onItemCraft"); }
    default void onContainerOpen(ContainerCallback callback) { throw unsupported("onContainerOpen"); }
    default void onContainerClose(ContainerCallback callback) { throw unsupported("onContainerClose"); }
    default void onProjectileHit(ProjectileCallback callback) { throw unsupported("onProjectileHit"); }
    default void onExplosion(ExplosionCallback callback) { throw unsupported("onExplosion"); }
    default void onAdvancement(AdvancementCallback callback) { throw unsupported("onAdvancement"); }

    private static UnsupportedOperationException unsupported(String hook) {
        return new UnsupportedOperationException("Loader does not support event hook '" + hook + "'.");
    }

    @FunctionalInterface
    interface ServerChatCallback {
        ChatResult handle(Object player, String message);
    }

    @FunctionalInterface
    interface EntityHurtCallback {
        void handle(Object entity, Object source, float amount);
    }

    @FunctionalInterface
    interface EntityDeathCallback {
        void handle(Object entity, Object source);
    }

    @FunctionalInterface
    interface BlockBreakCallback {
        void handle(Object player, Object level, Object pos);
    }

    @FunctionalInterface
    interface BlockPlaceCallback {
        void handle(Object entity, Object level, Object pos);
    }

    @FunctionalInterface
    interface ItemUseCallback {
        void handle(Object player, Object hand, Object itemStack);
    }

    @FunctionalInterface
    interface EntityInteractCallback {
        void handle(Object player, Object hand, Object target);
    }

    @FunctionalInterface
    interface BlockInteractionCallback {
        BlockInteractionResult handle(Object player, Object hand, Object level, Object pos);
    }

    @FunctionalInterface
    interface CancellableEntityHurtCallback {
        CancellableResult handle(Object entity, Object source, float amount);
    }

    @FunctionalInterface
    interface CancellableBlockCallback {
        CancellableResult handle(Object player, Object level, Object pos);
    }

    @FunctionalInterface
    interface CancellableItemUseCallback {
        CancellableResult handle(Object player, Object hand, Object itemStack);
    }

    @FunctionalInterface
    interface CancellableEntityInteractCallback {
        CancellableResult handle(Object player, Object hand, Object target);
    }

    @FunctionalInterface
    interface ItemStackCallback {
        void handle(Object player, Object itemStack);
    }

    @FunctionalInterface
    interface ContainerCallback {
        void handle(Object player, Object container);
    }

    @FunctionalInterface
    interface ProjectileCallback {
        void handle(Object projectile, Object hitResult);
    }

    @FunctionalInterface
    interface ExplosionCallback {
        void handle(Object level, Object position);
    }

    @FunctionalInterface
    interface AdvancementCallback {
        void handle(Object player, String advancementId);
    }

    record CancellableResult(boolean cancel) {
        public static CancellableResult pass() { return new CancellableResult(false); }
        public static CancellableResult cancelled() { return new CancellableResult(true); }
    }

    record BlockInteractionResult(boolean cancel) {
        public static BlockInteractionResult pass() { return new BlockInteractionResult(false); }
        public static BlockInteractionResult cancelled() { return new BlockInteractionResult(true); }
    }

    record ChatResult(boolean cancel, String message) {
        public static ChatResult pass(String message) {
            return new ChatResult(false, message);
        }

        public static ChatResult cancelled() {
            return new ChatResult(true, null);
        }

        public static ChatResult replace(String message) {
            return new ChatResult(false, message);
        }
    }
}
