package dev.mlua.minecraft.animation;

import dev.mlua.core.MLuaLogger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.luaj.vm2.LuaValue;

/** Owns animation marker callbacks and their runtime-wide resource budgets. */
final class MLuaAnimationEventState {
    static final int MAX_EVENTS_PER_TRACK = 256;
    static final int MAX_ACTIVE_EVENT_CALLBACKS = 65_536;
    static final int MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER = 4_096;
    static final int MAX_EVENT_CALLBACKS_PER_ENTITY_TICK = 1024;
    static final int MAX_EVENT_CALLBACKS_PER_ADDON_TICK = 256;
    static final int MAX_EVENT_CALLBACKS_PER_BATCH_TICK = 1024;
    static final int MAX_EVENT_CALLBACKS_PER_ADDON_BATCH_TICK = 256;

    private static final AtomicInteger ACTIVE_CALLBACKS = new AtomicInteger();
    private static final Object CALLBACK_LOCK = new Object();
    private static final Map<Owner, Integer> CALLBACKS_BY_OWNER = new HashMap<>();

    private final int trackId;
    private final Owner owner;
    private final List<Event> events = new ArrayList<>();

    MLuaAnimationEventState(int trackId, Object runtimeOwner, String addonOwner) {
        this.trackId = trackId;
        this.owner = new Owner(runtimeOwner, addonOwner);
    }

    void add(float triggerTime, float animationLength, LuaValue callback, Runnable requireActive) {
        if (!Float.isFinite(triggerTime) || triggerTime < 0.0f || triggerTime > animationLength) {
            throw new IllegalArgumentException("Animation event time must be finite and within the animation length");
        }
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Animation event callback must be a function");
        }
        synchronized (this.events) {
            requireActive.run();
            if (this.events.size() >= MAX_EVENTS_PER_TRACK) {
                throw new IllegalStateException("Animation event limit reached for this track ("
                    + MAX_EVENTS_PER_TRACK + ")");
            }
            if (!reserveCallback(this.owner)) {
                throw new IllegalStateException("MLua already has the maximum of "
                    + MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER + " active animation event callbacks for addon '"
                    + this.owner.addonOwner() + "' in this runtime, or the global limit of "
                    + MAX_ACTIVE_EVENT_CALLBACKS + " callbacks");
            }
            Event event = null;
            try {
                event = new Event(triggerTime, callback);
                this.events.add(event);
                this.events.sort((first, second) -> Float.compare(first.triggerTime(), second.triggerTime()));
            } catch (RuntimeException | Error failure) {
                if (event != null) this.events.remove(event);
                releaseCallbacks(this.owner, 1);
                throw failure;
            }
        }
    }

    void clear() {
        synchronized (this.events) {
            int removed = this.events.size();
            this.events.clear();
            releaseCallbacks(this.owner, removed);
        }
    }

    void fireBetween(double fromTime, double toTime, boolean includeStart, double animationLength,
            boolean looped, EventBudget budget, String addonOwner) {
        List<Event> snapshot;
        synchronized (this.events) {
            snapshot = List.copyOf(this.events);
        }
        if (snapshot.isEmpty()) return;
        if (!looped || animationLength <= 0.0) {
            for (Event event : snapshot) {
                boolean startsNow = includeStart && fromTime == 0.0 && event.triggerTime() == 0.0f;
                if (!startsNow && !(event.triggerTime() > fromTime && event.triggerTime() <= toTime)) continue;
                if (!budget.consume(this.trackId, addonOwner)) return;
                fire(event);
            }
            return;
        }

        long firstCycle = (long) Math.floor(fromTime / animationLength);
        long lastCycle = (long) Math.floor(toTime / animationLength);
        for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
            double cycleOffset = cycle * animationLength;
            for (Event event : snapshot) {
                double eventTime = cycleOffset + event.triggerTime();
                boolean startsNow = includeStart && fromTime == 0.0 && cycle == 0 && event.triggerTime() == 0.0f;
                if (!startsNow && !(eventTime > fromTime && eventTime <= toTime)) continue;
                if (!budget.consume(this.trackId, addonOwner)) return;
                fire(event);
            }
            if (cycle == Long.MAX_VALUE) break;
        }
    }

    private void fire(Event event) {
        synchronized (this.events) {
            if (!this.events.contains(event)) return;
        }
        try {
            event.callback().invoke(LuaValue.valueOf(this.trackId), LuaValue.valueOf(event.triggerTime()));
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable error) {
            synchronized (this.events) {
                if (this.events.remove(event)) releaseCallbacks(this.owner, 1);
            }
            MLuaLogger.LOGGER.warn("[MLua] Disabled animation event callback for track {} at t={} after it failed",
                this.trackId, event.triggerTime(), error);
        }
    }

    static int activeCallbackCount() { return ACTIVE_CALLBACKS.get(); }

    static int activeCallbackCount(Object runtimeOwner, String addonOwner) {
        synchronized (CALLBACK_LOCK) {
            return CALLBACKS_BY_OWNER.getOrDefault(new Owner(runtimeOwner, addonOwner), 0);
        }
    }

    private static boolean reserveCallback(Owner owner) {
        synchronized (CALLBACK_LOCK) {
            int count = ACTIVE_CALLBACKS.get();
            if (count >= MAX_ACTIVE_EVENT_CALLBACKS) return false;
            int ownerCount = CALLBACKS_BY_OWNER.getOrDefault(owner, 0);
            if (ownerCount >= MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER) return false;
            ACTIVE_CALLBACKS.set(count + 1);
            CALLBACKS_BY_OWNER.put(owner, ownerCount + 1);
            return true;
        }
    }

    private static void releaseCallbacks(Owner owner, int count) {
        if (count <= 0) return;
        synchronized (CALLBACK_LOCK) {
            int ownerCount = CALLBACKS_BY_OWNER.getOrDefault(owner, 0);
            if (ownerCount < count) {
                throw new IllegalStateException("Animation event callback ownership accounting underflow");
            }
            int remaining = ownerCount - count;
            if (remaining == 0) CALLBACKS_BY_OWNER.remove(owner);
            else CALLBACKS_BY_OWNER.put(owner, remaining);
            ACTIVE_CALLBACKS.addAndGet(-count);
        }
    }

    private static final class Event {
        private final float triggerTime;
        private final LuaValue callback;

        private Event(float triggerTime, LuaValue callback) {
            this.triggerTime = triggerTime;
            this.callback = callback;
        }

        private float triggerTime() { return this.triggerTime; }
        private LuaValue callback() { return this.callback; }
    }

    private record Owner(Object runtimeOwner, String addonOwner) {
        private Owner {
            addonOwner = addonOwner == null ? "" : addonOwner.trim();
        }

        @Override public boolean equals(Object other) {
            return other instanceof Owner owner && this.runtimeOwner == owner.runtimeOwner()
                && this.addonOwner.equals(owner.addonOwner());
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(this.runtimeOwner) + this.addonOwner.hashCode();
        }
    }

    static final class EventBudget {
        private int remaining;
        private final int addonLimit;
        private final EventBudget parent;
        private boolean warningLogged;
        private final Map<String, Integer> remainingByAddon = new HashMap<>();

        EventBudget(int limit) { this(limit, MAX_EVENT_CALLBACKS_PER_ADDON_TICK, null); }

        EventBudget(int limit, int addonLimit, EventBudget parent) {
            this.remaining = limit;
            this.addonLimit = addonLimit;
            this.parent = parent;
        }

        boolean consume(int trackId, String addonOwner) {
            String owner = addonOwner == null ? "" : addonOwner;
            int addonRemaining = this.remainingByAddon.getOrDefault(owner, this.addonLimit);
            if (addonRemaining <= 0) {
                warnLimit(trackId, owner, true);
                return false;
            }
            if (this.remaining <= 0) {
                warnLimit(trackId, owner, false);
                return false;
            }
            if (this.parent != null && !this.parent.canConsume(owner)) {
                this.parent.warnExhausted(trackId, owner);
                return false;
            }
            consumeAvailable(owner);
            if (this.parent != null) this.parent.consumeAvailable(owner);
            return true;
        }

        private boolean canConsume(String owner) {
            return this.remaining > 0 && this.remainingByAddon.getOrDefault(owner, this.addonLimit) > 0
                && (this.parent == null || this.parent.canConsume(owner));
        }

        private void consumeAvailable(String owner) {
            this.remaining--;
            this.remainingByAddon.put(owner, this.remainingByAddon.getOrDefault(owner, this.addonLimit) - 1);
        }

        private void warnExhausted(int trackId, String owner) {
            if (this.parent != null && !this.parent.canConsume(owner)) this.parent.warnExhausted(trackId, owner);
            else warnLimit(trackId, owner, this.remainingByAddon.getOrDefault(owner, this.addonLimit) <= 0);
        }

        private void warnLimit(int trackId, String owner, boolean addonLimitReached) {
            if (this.warningLogged) return;
            this.warningLogged = true;
            if (addonLimitReached) {
                MLuaLogger.LOGGER.warn("[MLua] Animation event callback limit reached for addon '{}' on track {}",
                    owner, trackId);
            } else {
                MLuaLogger.LOGGER.warn("[MLua] Animation event callback limit reached for track {}", trackId);
            }
        }
    }
}
