package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedEntityAISettingsTest {
    private static final String KEY = "mlua_test_ai:interval_settings";

    @Test
    void intervalClampsAndRollbackOnlyRestoresTheExpectedRegistration() {
        try {
            MLuaBehaviorRegistry.registerEntityAIInterval(KEY, 0);
            assertEquals(1, MLuaBehaviorRegistry.getEntityAIInterval(KEY));

            MLuaBehaviorRegistry.registerEntityAIInterval(KEY, 999);
            assertEquals(120, MLuaBehaviorRegistry.getEntityAIInterval(KEY));
            assertTrue(MLuaBehaviorRegistry.restoreEntityAIInterval(KEY, 120, 1));
            assertEquals(1, MLuaBehaviorRegistry.getEntityAIInterval(KEY));

            MLuaBehaviorRegistry.registerEntityAIInterval(KEY, 20);
            assertFalse(MLuaBehaviorRegistry.restoreEntityAIInterval(KEY, 120, 1));
            assertEquals(20, MLuaBehaviorRegistry.getEntityAIInterval(KEY));
        } finally {
            MLuaBehaviorRegistry.clearEntityCallbacks(KEY);
        }
    }
}
