package dev.mlua.runtime.client;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaClientExecutionScopeTest {
    @Test
    void inheritedCoroutineAuthorityExpiresWhenTheClientCallbackEnds() throws Exception {
        CountDownLatch childEntered = new CountDownLatch(1);
        CountDownLatch callbackEnded = new CountDownLatch(1);
        AtomicBoolean activeDuringCallback = new AtomicBoolean();
        AtomicBoolean activeAfterCallback = new AtomicBoolean(true);
        Thread child;

        try (MLuaClientExecutionScope ignored = MLuaClientExecutionScope.open()) {
            child = new Thread(() -> {
                activeDuringCallback.set(MLuaClientExecutionScope.isActive());
                childEntered.countDown();
                try {
                    if (!callbackEnded.await(5, TimeUnit.SECONDS)) return;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
                activeAfterCallback.set(MLuaClientExecutionScope.isActive());
            });
            child.start();
            assertTrue(childEntered.await(5, TimeUnit.SECONDS));
            assertTrue(activeDuringCallback.get());
        }

        callbackEnded.countDown();
        child.join(TimeUnit.SECONDS.toMillis(5));
        assertFalse(child.isAlive());
        assertFalse(activeAfterCallback.get());
    }

    @Test
    void closingNestedScopeRestoresActiveParent() {
        try (MLuaClientExecutionScope outer = MLuaClientExecutionScope.open()) {
            assertTrue(MLuaClientExecutionScope.isActive());
            try (MLuaClientExecutionScope inner = MLuaClientExecutionScope.open()) {
                assertTrue(MLuaClientExecutionScope.isActive());
            }
            assertTrue(MLuaClientExecutionScope.isActive());
        }
        assertFalse(MLuaClientExecutionScope.isActive());
    }
}
