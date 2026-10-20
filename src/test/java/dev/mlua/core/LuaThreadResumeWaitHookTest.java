package dev.mlua.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaThread;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaThreadResumeWaitHookTest {
    @Test
    void resumeWaitHookRunsOutsideCoroutineMonitorAndCanPumpOwnerWork() throws Exception {
        Globals globals = JsePlatform.standardGlobals();
        CountDownLatch coroutineStarted = new CountDownLatch(1);
        AtomicBoolean releaseCoroutine = new AtomicBoolean();
        AtomicInteger hookCalls = new AtomicInteger();
        LuaThread thread = new LuaThread(globals, new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                coroutineStarted.countDown();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!releaseCoroutine.get() && System.nanoTime() < deadline) Thread.yield();
                return LuaValue.valueOf(releaseCoroutine.get());
            }
        });

        Varargs result = LuaThread.withResumeWaitHook(completed -> {
            hookCalls.incrementAndGet();
            assertTrue(coroutineStarted.await(2, TimeUnit.SECONDS));
            releaseCoroutine.set(true);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!completed.getAsBoolean() && System.nanoTime() < deadline) Thread.yield();
            assertTrue(completed.getAsBoolean());
        }, () -> thread.resume(LuaValue.NONE));

        assertTrue(result.arg1().toboolean());
        assertTrue(result.arg(2).toboolean());
        assertEquals(1, hookCalls.get());
    }
}
