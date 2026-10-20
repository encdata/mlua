package dev.mlua.minecraft.scripted;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded cross-loader guard for duplicate block callbacks within one world tick. */
public final class MLuaBlockEventDeduplicator {
    public static final int MAX_TRACKED_EVENTS = 8_192;
    private static final Map<EventKey, Long> RECENT_EVENTS = new LinkedHashMap<>(256, 0.75F, true);
    private static final Map<Object, Long> WORLD_IDS = new WeakHashMap<>();
    private static final AtomicLong NEXT_WORLD_ID = new AtomicLong(1L);

    private MLuaBlockEventDeduplicator() { }

    public static synchronized boolean isDuplicate(String event, Object world, String dimension, long position,
            String entityId, long gameTime) {
        if (event == null || event.isBlank() || world == null || dimension == null || dimension.isBlank()) {
            throw new IllegalArgumentException("Block event, world, and dimension identifiers must not be blank");
        }
        long worldId;
        synchronized (WORLD_IDS) {
            worldId = WORLD_IDS.computeIfAbsent(world, ignored -> NEXT_WORLD_ID.getAndIncrement());
        }
        // A block's onPlace hook has no actor, while the loader placement event does.
        // Both describe one placement at a position in a tick, so actor identity must
        // not let the second delivery through. Break and step events remain actor-scoped.
        String eventEntity = "place".equals(event) ? "none" : entityId == null ? "none" : entityId;
        EventKey key = new EventKey(event, worldId, dimension, position, eventEntity);
        Long previous = RECENT_EVENTS.put(key, gameTime);
        while (RECENT_EVENTS.size() > MAX_TRACKED_EVENTS) {
            EventKey eldest = RECENT_EVENTS.keySet().iterator().next();
            RECENT_EVENTS.remove(eldest);
        }
        return previous != null && previous == gameTime;
    }

    private record EventKey(String event, long worldId, String dimension, long position, String entityId) { }
}
