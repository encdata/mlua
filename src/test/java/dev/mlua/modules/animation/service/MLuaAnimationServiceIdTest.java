package dev.mlua.modules.animation.service;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaAnimationServiceIdTest {
    @Test
    void qualifiesAddonLocalIdsAndAcceptsCanonicalResourcePaths() {
        assertEquals("example:walk", MLuaAnimationService.animationId(
            "walk", "example", "AnimationService:RegisterAnimation", true));
        assertEquals("example:walk/run.v2", MLuaAnimationService.animationId(
            "example:walk/run.v2", "example", "AnimationService:GetAnimation", false));
    }

    @Test
    void rejectsWhitespaceNonCanonicalIdsAndForeignRegistrationNamespaces() {
        assertThrows(LuaError.class, () -> MLuaAnimationService.animationId(
            "walk forward", "example", "AnimationService:GetAnimation", false));
        assertThrows(LuaError.class, () -> MLuaAnimationService.animationId(
            "Example:walk", "example", "AnimationService:GetAnimation", false));
        assertThrows(LuaError.class, () -> MLuaAnimationService.animationId(
            "other:walk", "example", "AnimationService:RegisterAnimation", true));
    }
}
