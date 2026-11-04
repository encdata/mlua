package dev.mlua.modules.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaChatStateRegistryTest {
    @Test
    void muteHistoryAndSignalStateAreIndependentPerRuntime() {
        Object firstSession = new Object();
        Object secondSession = new Object();
        var first = MLuaChatStateRegistry.get(firstSession);
        var second = MLuaChatStateRegistry.get(secondSession);

        first.mutedPlayers.put("player", 0L);
        first.messageHistory.computeIfAbsent("player", ignored -> new java.util.concurrent.ConcurrentLinkedDeque<>())
                .add("first server message");

        assertNotSame(first.onMessage, second.onMessage);
        assertNull(second.mutedPlayers.get("player"));
        assertNull(second.messageHistory.get("player"));

        MLuaChatStateRegistry.clear(firstSession);
        assertNull(MLuaChatStateRegistry.get(firstSession).mutedPlayers.get("player"));
        assertNull(second.mutedPlayers.get("player"));
        MLuaChatStateRegistry.clear(firstSession);
        MLuaChatStateRegistry.clear(secondSession);
    }
}
