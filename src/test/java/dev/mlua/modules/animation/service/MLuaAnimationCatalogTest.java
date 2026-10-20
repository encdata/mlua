package dev.mlua.modules.animation.service;

import dev.mlua.minecraft.animation.MLuaAnimation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAnimationCatalogTest {
    @Test
    void registrationReplacesOwnedDefinitionsWithoutDuplicatingIds() {
        var catalog = new MLuaAnimationCatalog();
        MLuaAnimation first = animation();
        MLuaAnimation replacement = animation();

        assertTrue(catalog.register("addon:walk", "addon", first));
        assertTrue(catalog.register("addon:walk", "addon", replacement));
        assertSame(replacement, catalog.get("addon:walk"));
        assertEquals(List.of("addon:walk"), catalog.idsForOwner("addon"));
        assertEquals(1, catalog.entriesForOwner("addon").size());
    }

    @Test
    void catalogKeepsOwnershipIsolatedAcrossLookupRemovalAndCleanup() {
        var catalog = new MLuaAnimationCatalog();
        MLuaAnimation shared = animation();
        MLuaAnimation privateAnimation = animation();
        catalog.register("first:shared", "first", shared);
        catalog.register("first:private", "first", privateAnimation);

        assertFalse(catalog.register("first:shared", "second", animation()));
        assertFalse(catalog.remove("first:private", "second"));
        assertEquals(List.of("first:private", "first:shared"), catalog.idsForOwner("first"));
        assertEquals(2, catalog.clearOwner("first"));
        assertTrue(catalog.idsForOwner("first").isEmpty());
        assertTrue(catalog.idsForOwner("second").isEmpty());
        assertEquals(Map.of(), catalog.entriesForOwner("first"));
    }

    private static MLuaAnimation animation() {
        return new MLuaAnimation(List.of(new MLuaAnimation.Keyframe(0.0, Map.of())));
    }
}
