package dev.mlua.loaderapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class ClientWorldRenderContextTest {
    @Test
    void invokeFlushesOnceAndExpiresReferencesAfterCallback() {
        AtomicInteger flushes = new AtomicInteger();
        Object poseStack = new Object();
        ClientWorldRenderContext context = new ClientWorldRenderContext(
            new Object(), poseStack, new Object(), new Object(), flushes::incrementAndGet);

        context.invoke(frame -> {
            assertTrue(frame.isActive());
            assertSame(poseStack, frame.poseStack());
        });

        assertEquals(1, flushes.get());
        assertFalse(context.isActive());
        assertThrows(IllegalStateException.class, context::poseStack);
        context.finish();
        assertEquals(1, flushes.get());
    }

    @Test
    void callbackFailureStillFlushesAndExpiresContext() {
        AtomicInteger flushes = new AtomicInteger();
        ClientWorldRenderContext context = new ClientWorldRenderContext(
            new Object(), new Object(), new Object(), new Object(), flushes::incrementAndGet);
        IllegalArgumentException callbackFailure = new IllegalArgumentException("callback failed");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> context.invoke(frame -> { throw callbackFailure; }));

        assertSame(callbackFailure, thrown);
        assertEquals(1, flushes.get());
        assertFalse(context.isActive());
        assertThrows(IllegalStateException.class, context::nativeContext);
    }

    @Test
    void flushFailureIsSuppressedWhenCallbackAlsoFails() {
        RuntimeException flushFailure = new RuntimeException("flush failed");
        IllegalStateException callbackFailure = new IllegalStateException("callback failed");
        ClientWorldRenderContext context = new ClientWorldRenderContext(
            new Object(), new Object(), new Object(), new Object(), () -> { throw flushFailure; });

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> context.invoke(frame -> { throw callbackFailure; }));

        assertSame(callbackFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(flushFailure, thrown.getSuppressed()[0]);
        assertFalse(context.isActive());
    }

    @Test
    void nativeReferencesAndFinishAreBoundToCreatingRenderThread() throws InterruptedException {
        ClientWorldRenderContext context = new ClientWorldRenderContext(
            new Object(), new Object(), new Object(), new Object(), null);
        AtomicReference<Throwable> wrongThreadFailure = new AtomicReference<>();
        AtomicReference<Throwable> wrongThreadFinishFailure = new AtomicReference<>();
        Thread otherThread = new Thread(() -> {
            try {
                context.poseStack();
            } catch (Throwable failure) {
                wrongThreadFailure.set(failure);
            }
            try {
                context.finish();
            } catch (Throwable failure) {
                wrongThreadFinishFailure.set(failure);
            }
        }, "render-context-contract-test");

        otherThread.start();
        otherThread.join();

        assertTrue(wrongThreadFailure.get() instanceof IllegalStateException);
        assertTrue(wrongThreadFinishFailure.get() instanceof IllegalStateException);
        assertTrue(context.isActive());
        context.finish();
        assertFalse(context.isActive());
    }
}
