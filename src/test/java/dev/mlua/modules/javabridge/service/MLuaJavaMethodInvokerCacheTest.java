package dev.mlua.modules.javabridge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;

final class MLuaJavaMethodInvokerCacheTest {
    @Test
    void cachesResolvedMethodWrappers() {
        MLuaJavaMethodInvokerCache cache = new MLuaJavaMethodInvokerCache();
        AtomicInteger creations = new AtomicInteger();
        LuaValue wrapper = LuaValue.valueOf("method-wrapper");

        assertSame(wrapper, cache.get("method", () -> {
            creations.incrementAndGet();
            return wrapper;
        }));
        assertSame(wrapper, cache.get("method", () -> {
            creations.incrementAndGet();
            return LuaValue.NIL;
        }));

        assertEquals(1, creations.get());
    }

    @Test
    void cachesMissingMethodLookups() {
        MLuaJavaMethodInvokerCache cache = new MLuaJavaMethodInvokerCache();
        AtomicInteger lookups = new AtomicInteger();

        assertSame(LuaValue.NIL, cache.get("missing", () -> {
            lookups.incrementAndGet();
            return LuaValue.NIL;
        }));
        assertSame(LuaValue.NIL, cache.get("missing", () -> {
            lookups.incrementAndGet();
            return LuaValue.valueOf("unexpected");
        }));

        assertEquals(1, lookups.get());
    }

    @Test
    void concurrentLookupsReturnTheCachedWrapperWinner() throws Exception {
        MLuaJavaMethodInvokerCache cache = new MLuaJavaMethodInvokerCache();
        int callerCount = 8;
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService callers = Executors.newFixedThreadPool(callerCount);
        List<Future<LuaValue>> results = new ArrayList<>();
        try {
            for (int index = 0; index < callerCount; index++) {
                results.add(callers.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cache.get("shared", () -> LuaValue.userdataOf(new Object()));
                }));
            }
            ready.await();
            start.countDown();

            LuaValue winner = results.get(0).get();
            for (Future<LuaValue> result : results) assertSame(winner, result.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void boundsCachedUnknownAndResolvedNames() {
        MLuaJavaMethodInvokerCache cache = new MLuaJavaMethodInvokerCache();
        AtomicInteger recomputed = new AtomicInteger();
        int distinctNames = MLuaJavaMethodInvokerCache.MAX_ENTRIES + 1;

        for (int index = 0; index < distinctNames; index++) {
            String name = "member" + index;
            cache.get(name, () -> LuaValue.valueOf(name));
        }
        for (int index = 0; index < distinctNames; index++) {
            String name = "member" + index;
            cache.get(name, () -> {
                recomputed.incrementAndGet();
                return LuaValue.valueOf(name);
            });
        }

        assertTrue(recomputed.get() > 0, "old cache entries must be evicted after the configured bound");
    }

    @Test
    void keepsRecentlyUsedNamesWhenTheCacheReachesItsBound() {
        MLuaJavaMethodInvokerCache cache = new MLuaJavaMethodInvokerCache();
        for (int index = 0; index < MLuaJavaMethodInvokerCache.MAX_ENTRIES; index++) {
            String name = "member" + index;
            cache.get(name, () -> LuaValue.valueOf(name));
        }

        cache.get("member0", () -> LuaValue.NIL);
        cache.get("overflow", () -> LuaValue.NIL);
        AtomicInteger recomputed = new AtomicInteger();
        cache.get("member0", () -> {
            recomputed.incrementAndGet();
            return LuaValue.NIL;
        });
        cache.get("member1", () -> {
            recomputed.incrementAndGet();
            return LuaValue.NIL;
        });

        assertEquals(1, recomputed.get(), "the least recently used wrapper should be evicted first");
    }
}
