package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedContentClassRegistryTest {
    private static final String DOMAIN = "item";
    private static final String KEY = "mlua_test:scripted_item_class";

    @Test
    void restoresClassMappingForFailedRegistrationAndRetainsFacadeBehavior() {
        try {
            MLuaBehaviorRegistry.registerContentClass(DOMAIN, KEY, "OriginalItem");
            MLuaBehaviorRegistry.ContentClassDefinition original =
                    MLuaBehaviorRegistry.getContentClassDefinition(DOMAIN, KEY);

            MLuaBehaviorRegistry.registerContentClass(DOMAIN, KEY, "ReplacementItem");
            MLuaBehaviorRegistry.ContentClassDefinition replacement =
                    MLuaBehaviorRegistry.getContentClassDefinition(DOMAIN, KEY);

            assertEquals("ReplacementItem", MLuaBehaviorRegistry.getContentClass(DOMAIN, KEY));
            assertTrue(MLuaBehaviorRegistry.restoreContentClass(DOMAIN, KEY, replacement, original));
            assertEquals("OriginalItem", MLuaBehaviorRegistry.getContentClass(DOMAIN, KEY));
        } finally {
            MLuaBehaviorRegistry.registerContentClass(DOMAIN, KEY, null);
        }
    }
}
