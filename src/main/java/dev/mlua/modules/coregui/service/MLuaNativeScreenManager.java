package dev.mlua.modules.coregui.service;

import dev.mlua.runtime.client.MLuaClientThread;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaError;

/** Owns the client-thread queue and open/close lifecycle for Lua-created Minecraft screens. */
public final class MLuaNativeScreenManager {
    private static final Set<MLuaScriptScreen> PENDING = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Set<Object>> CLOSING = new ThreadLocal<>();

    private MLuaNativeScreenManager() { }

    static void queueOpen(MLuaScriptScreen screen) {
        if (!screen.markPendingOpen()) return;
        PENDING.add(screen);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.execute(() -> openPending(minecraft, screen));
    }

    static void forget(MLuaScriptScreen screen) {
        PENDING.remove(screen);
    }

    public static void tickPending() {
        if (PENDING.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !MLuaClientThread.isClientThread() || minecraft.getWindow() == null) return;
        for (MLuaScriptScreen screen : PENDING.toArray(MLuaScriptScreen[]::new)) {
            openPending(minecraft, screen);
        }
    }

    private static void openPending(Minecraft minecraft, MLuaScriptScreen screen) {
        if (minecraft == null || !MLuaClientThread.isClientThread() || minecraft.getWindow() == null) return;
        if (!screen.isPendingOpen() || screen.isDisposed()) {
            PENDING.remove(screen);
            return;
        }
        if (minecraft.screen == screen) {
            PENDING.remove(screen);
            return;
        }
        minecraft.setScreen(screen);
        if (minecraft.screen == screen) PENDING.remove(screen);
    }

    public static Object currentScreen() {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.client.Minecraft");
            Object instance = minecraft.getMethod("getInstance").invoke(null);
            Field screen = minecraft.getField("screen");
            return screen.get(instance);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isPending(Object screen) {
        return screen instanceof MLuaScriptScreen scriptScreen && scriptScreen.isPendingOpen();
    }

    public static boolean isDisposed(Object screen) {
        return screen instanceof MLuaScriptScreen scriptScreen && scriptScreen.isDisposed();
    }

    public static boolean isClosing(Object screen) {
        Set<Object> closing = CLOSING.get();
        return screen != null && closing != null && closing.contains(screen);
    }

    static void runCloseCallback(Object screen, Runnable callback) {
        Set<Object> closing = CLOSING.get();
        if (closing == null) {
            closing = Collections.newSetFromMap(new IdentityHashMap<>());
            CLOSING.set(closing);
        }
        boolean added = closing.add(screen);
        try {
            callback.run();
        } finally {
            if (added) closing.remove(screen);
            if (closing.isEmpty()) CLOSING.remove();
        }
    }

    /** Closes only the expected active screen, scheduling the operation when Lua calls from a worker. */
    public static boolean close(Object expectedScreen) {
        if (expectedScreen == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        return onClientThread(() -> {
            if (expectedScreen instanceof MLuaScriptScreen scriptScreen
                    && scriptScreen.isPendingOpen() && minecraft.screen != expectedScreen) {
                scriptScreen.dispose();
                return true;
            }
            return closeOnClientThread(minecraft, expectedScreen);
        });
    }

    /** Closes whichever Minecraft screen is active when this request is made. */
    public static boolean closeCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return false;
        return onClientThread(() -> closeCurrentOnClientThread(minecraft));
    }

    private static boolean onClientThread(java.util.function.Supplier<Boolean> operation) {
        return MLuaClientThread.runOnClientThread(operation::get);
    }

    private static boolean closeCurrentOnClientThread(Minecraft minecraft) {
        return closeOnClientThread(minecraft, minecraft.screen);
    }

    private static boolean closeOnClientThread(Minecraft minecraft, Object expectedScreen) {
        if (!MLuaClientThread.isClientThread() || minecraft.screen != expectedScreen) return false;
        if (expectedScreen == null) return false;
        if (expectedScreen instanceof MLuaScriptScreen scriptScreen) scriptScreen.onClose();
        else minecraft.setScreen(null);
        return true;
    }

    public static void requireClientThread(String operation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !MLuaClientThread.isClientThread()) {
            throw new LuaError("[MLua] " + operation + " must run on Minecraft's client thread. "
                + "Queue screen changes with Gui and inspect or modify controls from a client screen callback.");
        }
    }
}
