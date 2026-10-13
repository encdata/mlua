package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.common.enums.Side;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaAnimationControllerOwnershipTest {
    @AfterEach
    void clearTracks() {
        MLuaAnimationController.clearAll();
    }

    @Test
    void identicalEntityTracksAndCallbacksRemainOwnedByTheirRuntime() {
        MLuaEntityScope entity = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 9);
        Object firstRuntime = new Object();
        Object secondRuntime = new Object();
        var firstGlobals = JsePlatform.standardGlobals();
        var secondGlobals = JsePlatform.standardGlobals();
        firstGlobals.load("count = 0").call();
        secondGlobals.load("count = 0").call();
        MLuaAnimation animation = new MLuaAnimation(List.of(
                new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In),
                new MLuaAnimation.Keyframe(1, Map.of(), EasingStyle.Linear, EasingDirection.In)));

        MLuaAnimationTrack first = MLuaAnimationController.loadAnimation(entity, animation, AnimationPriority.Idle, firstRuntime);
        MLuaAnimationTrack second = MLuaAnimationController.loadAnimation(entity, animation, AnimationPriority.Idle, secondRuntime);
        first.addEvent(0.05f, firstGlobals.load("count = count + 1"));
        second.addEvent(0.05f, secondGlobals.load("count = count + 1"));
        second.addEvent(0.15f, secondGlobals.load("count = count + 1"));
        first.play();
        second.play();
        assertEquals(1, MLuaAnimationController.getTracks(entity, firstRuntime).size());
        assertEquals(1, MLuaAnimationController.getTracks(entity, secondRuntime).size());

        MLuaAnimationController.tickEntity(entity, 0.1f);
        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(1, secondGlobals.get("count").toint());

        MLuaAnimationController.clearRuntime(firstRuntime, Side.SERVER);
        MLuaAnimationController.tickEntity(entity, 0.1f);

        assertEquals(1, firstGlobals.get("count").toint());
        assertEquals(2, secondGlobals.get("count").toint());
        assertEquals(0, MLuaAnimationController.getTracks(entity, firstRuntime).size());
        assertEquals(1, MLuaAnimationController.getTracks(entity, secondRuntime).size());
    }
}
