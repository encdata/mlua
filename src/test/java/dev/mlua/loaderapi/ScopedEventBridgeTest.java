package dev.mlua.loaderapi;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ScopedEventBridgeTest {
    @Test
    void closeDisablesCallbackAndReleasesItsTarget() {
        List<Runnable> registered = new ArrayList<>();
        EventBridge delegate = (EventBridge) Proxy.newProxyInstance(
            EventBridge.class.getClassLoader(), new Class<?>[]{EventBridge.class},
            (proxy, method, args) -> {
                if (method.getName().equals("onClientTickEnd")) registered.add((Runnable) args[0]);
                return null;
            });
        AtomicInteger calls = new AtomicInteger();
        ScopedEventBridge scope = new ScopedEventBridge(delegate);

        scope.bridge().onClientTickEnd(calls::incrementAndGet);
        registered.get(0).run();
        assertEquals(1, calls.get());

        scope.close();
        scope.close();
        registered.get(0).run();
        assertEquals(1, calls.get());
        assertThrows(IllegalStateException.class, scope::bridge);
    }
}
