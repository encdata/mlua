package dev.mlua.loaderapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EventBridgeTest {
    @Test
    void blockInteractionResultsExposePassAndCancelStates() {
        assertFalse(EventBridge.BlockInteractionResult.pass().cancel());
        assertTrue(EventBridge.BlockInteractionResult.cancelled().cancel());
    }
}
