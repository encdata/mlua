package dev.mlua.modules.tween.service;

import dev.mlua.common.enums.Side;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaError;

/** Owns active tweens and their per-runtime render snapshots. */
final class MLuaTweenRegistry {
    static final int MAX_ACTIVE_TWEENS = 1_024;
    static final int MAX_ACTIVE_TWEENS_PER_RUNTIME = 64;

    private static final Map<Object, ActiveTweens> ACTIVE = new ConcurrentHashMap<>();
    private static final Object LOCK = new Object();
    private static int activeCount;

    private MLuaTweenRegistry() { }

    static void activate(MLuaTween tween) {
        synchronized (LOCK) {
            ActiveTweens active = ACTIVE.computeIfAbsent(tween.ownerSession(), ignored -> new ActiveTweens());
            if (active.contains(tween)) return;
            if (active.size() >= MAX_ACTIVE_TWEENS_PER_RUNTIME || activeCount >= MAX_ACTIVE_TWEENS) {
                throw new LuaError("[MLua] Tween capacity reached (" + MAX_ACTIVE_TWEENS_PER_RUNTIME
                    + " per runtime, " + MAX_ACTIVE_TWEENS + " total).");
            }
            active.add(tween);
            activeCount++;
        }
    }

    static void deactivate(MLuaTween tween) {
        synchronized (LOCK) {
            Object session = tween.ownerSession();
            ActiveTweens active = session == null ? null : ACTIVE.get(session);
            if (active != null && active.remove(tween)) {
                activeCount--;
                if (active.isEmpty()) ACTIVE.remove(session, active);
            }
        }
    }

    static int countOwned(Object runtimeSession, String ownerModId) {
        if (runtimeSession == null || ownerModId == null) return 0;
        ActiveTweens active = ACTIVE.get(runtimeSession);
        return active == null ? 0 : active.countOwned(ownerModId);
    }

    static int activeCount() {
        synchronized (LOCK) { return activeCount; }
    }

    static void onRenderFrame(Object runtimeSession) {
        ActiveTweens active = ACTIVE.get(runtimeSession);
        if (active == null) return;
        long now = System.nanoTime();
        for (MLuaTween tween : active.snapshot()) {
            if (!tween.sample(now)) {
                deactivate(tween);
                tween.firePendingCompletion();
            }
        }
    }

    static void clearRuntime(Object runtimeSession, Side side) {
        if (runtimeSession == null || (side != Side.CLIENT && side != Side.BOTH)) return;
        MLuaTween[] removed;
        synchronized (LOCK) {
            ActiveTweens active = ACTIVE.remove(runtimeSession);
            if (active == null) return;
            removed = active.snapshot();
            activeCount -= removed.length;
        }
        for (MLuaTween tween : removed) tween.dispose();
    }

    static void clearOwned(Object runtimeSession, String ownerModId, Side side) {
        if ((side != Side.CLIENT && side != Side.BOTH) || runtimeSession == null) return;
        ActiveTweens active = ACTIVE.get(runtimeSession);
        if (active == null) return;
        for (MLuaTween tween : active.snapshot()) {
            if (tween.ownerModId().equals(ownerModId)) tween.dispose();
        }
    }

    /** Membership changes publish snapshots off the render path; sampling reads without allocation. */
    private static final class ActiveTweens {
        private final Set<MLuaTween> members = new HashSet<>();
        private final Map<String, Integer> countsByOwner = new HashMap<>();
        private volatile MLuaTween[] snapshot = new MLuaTween[0];

        boolean contains(MLuaTween tween) { return members.contains(tween); }
        int size() { return members.size(); }
        boolean isEmpty() { return members.isEmpty(); }
        MLuaTween[] snapshot() { return snapshot; }

        int countOwned(String ownerModId) {
            synchronized (LOCK) { return countsByOwner.getOrDefault(ownerModId, 0); }
        }

        boolean add(MLuaTween tween) {
            if (!members.add(tween)) return false;
            countsByOwner.merge(tween.ownerModId(), 1, Integer::sum);
            publish();
            return true;
        }

        boolean remove(MLuaTween tween) {
            if (!members.remove(tween)) return false;
            String owner = tween.ownerModId();
            int remaining = countsByOwner.getOrDefault(owner, 0) - 1;
            if (remaining <= 0) countsByOwner.remove(owner);
            else countsByOwner.put(owner, remaining);
            publish();
            return true;
        }

        private void publish() { snapshot = members.toArray(MLuaTween[]::new); }
    }
}
