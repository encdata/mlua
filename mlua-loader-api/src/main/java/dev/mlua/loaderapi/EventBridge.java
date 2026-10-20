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
    /** Runs once when the client leaves a network world; client process shutdown is a separate lifecycle event. */
    void onClientDisconnect(Runnable callback);
    void onPlayerJoin(Consumer<Object> callback);
    void onPlayerLeave(Consumer<Object> callback);
    void onServerChat(ServerChatCallback callback);
    /**
     * Runs on the logical server after the hit passes the entity's invulnerability check and before
     * armor and health mitigation. The amount is the value exposed at this loader hook, not final
     * health loss; earlier hit processing such as shield blocking may already have changed it.
     */
    void onEntityHurt(EntityHurtCallback callback);
    /** Runs only on the logical server when a living entity's death event is posted. */
    void onEntityDeath(EntityDeathCallback callback);
    /** Runs before a player breaks a block on the logical server. */
    void onBlockBreak(BlockBreakCallback callback);
    /** Runs on the logical server after a successful placement; entity may be null for automated placements. */
    void onBlockPlace(BlockPlaceCallback callback);
    void onItemUse(ItemUseCallback callback);
    void onEntityInteract(EntityInteractCallback callback);

    default void onPlayerRespawn(Consumer<Object> callback) { throw unsupported("onPlayerRespawn"); }
    default void onClientEntityUnload(Consumer<Object> callback) { throw unsupported("onClientEntityUnload"); }
    default void onEntitySpawn(Consumer<Object> callback) { throw unsupported("onEntitySpawn"); }
    /** Server-world lifecycle hooks; run after the loader reports the world transition. */
    default void onWorldLoad(Consumer<Object> callback) { throw unsupported("onWorldLoad"); }
    default void onWorldUnload(Consumer<Object> callback) { throw unsupported("onWorldUnload"); }
    /**
     * Client-world lifecycle hooks are separate so dedicated-server loaders need not load client classes.
     * Load runs after the active client world is installed; unload runs after it is removed. On a dimension
     * change, unload is delivered before load.
     */
    default void onClientWorldLoad(Consumer<Object> callback) { throw unsupported("onClientWorldLoad"); }
    default void onClientWorldUnload(Consumer<Object> callback) { throw unsupported("onClientWorldUnload"); }
    default void onBlockRightClick(BlockInteractionCallback callback) { throw unsupported("onBlockRightClick"); }
    /** Runs after invulnerability checks and before armor and health mitigation; cancellation prevents health damage. */
    default void onEntityHurtEvent(CancellableEntityHurtCallback callback) { throw unsupported("onEntityHurtEvent"); }
    /** Runs on the logical server only; cancellation prevents the block break. */
    default void onBlockBreakEvent(CancellableBlockCallback callback) { throw unsupported("onBlockBreakEvent"); }
    /** Runs on the logical server only; cancellation prevents the item use. */
    default void onItemUseEvent(CancellableItemUseCallback callback) { throw unsupported("onItemUseEvent"); }
    /** Runs on the logical server only; cancellation prevents entity interaction. */
    default void onEntityInteractEvent(CancellableEntityInteractCallback callback) { throw unsupported("onEntityInteractEvent"); }
    default void onItemDrop(ItemStackCallback callback) { throw unsupported("onItemDrop"); }
    /**
     * Runs on the logical server after a successful pickup. The supplied stack
     * is a snapshot whose count is the amount transferred to the player, not
     * the source item's pre-pickup count; partial pickups must report only the
     * transferred amount.
     */
    default void onItemPickup(ItemStackCallback callback) { throw unsupported("onItemPickup"); }
    default void onItemCraft(ItemStackCallback callback) { throw unsupported("onItemCraft"); }
    /** Runs only on the logical server when a player opens a non-inventory menu. */
    default void onContainerOpen(ContainerCallback callback) { throw unsupported("onContainerOpen"); }
    /** Runs only on the logical server when a player closes a non-inventory menu. */
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
        /** The placer entity, or null when placement was not performed by an entity. */
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

    /**
     * Result of one logical-server chat callback. Replacement messages are broadcast as an
     * ordinary chat-formatted system message and the original signed chat packet is cancelled.
     * This gives loaders with different signed-chat hooks the same observable MLua behavior.
     */
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
