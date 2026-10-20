package dev.mlua.modules.events;

import dev.mlua.modules.events.service.MLuaEventsService;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAddonEventSignalTest {
    private static final String DESCRIPTION = "Fires when the test addon changes its state.";

    @Test
    void addonSignalsUseOwnerNamespaceAndCloseOnlyTheirOwnRegistration() {
        String owner = "test_" + UUID.randomUUID().toString().replace("-", "");
        String key = owner + ":StateChanged";
        MLuaEventsService.AddonSignal first = MLuaEventsService.registerAddonSignal(
                owner, "StateChanged", DESCRIPTION);
        MLuaEventsService.AddonSignal replacement = null;
        try {
            assertEquals(key, first.name());
            assertTrue(MLuaEventsService.signalNames().contains(key));

            first.close();
            first.close();
            assertFalse(MLuaEventsService.signalNames().contains(key));
            assertThrows(LuaError.class, () -> first.fire(LuaValue.NONE));

            replacement = MLuaEventsService.registerAddonSignal(owner, "StateChanged", DESCRIPTION);
            first.close();
            assertTrue(MLuaEventsService.signalNames().contains(key),
                    "a stale handle must not unregister its replacement");
        } finally {
            first.close();
            if (replacement != null) replacement.close();
        }
        assertFalse(MLuaEventsService.signalNames().contains(key));
    }

    @Test
    void addonSignalsRejectForeignNamespacesAndMissingDocumentation() {
        String owner = "test_" + UUID.randomUUID().toString().replace("-", "");
        assertThrows(IllegalArgumentException.class,
                () -> MLuaEventsService.registerAddonSignal(owner, "other:StateChanged", DESCRIPTION));
        assertThrows(IllegalArgumentException.class,
                () -> MLuaEventsService.registerAddonSignal(owner, "StateChanged", "  "));
        assertFalse(MLuaEventsService.signalNames().contains(owner + ":StateChanged"));
    }
}
