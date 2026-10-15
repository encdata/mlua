package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import net.minecraft.world.entity.HumanoidArm;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.JsePlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaHandPoseRegistryTest {
    @Test
    void composesAddonLayersAndClearsOnlyTheRequestedOwner() {
        MLuaHandPoseRegistry registry = new MLuaHandPoseRegistry();
        Object session = new Object();
        var first = new MLuaHandPoseRegistry.Owner(session, "first-addon");
        var second = new MLuaHandPoseRegistry.Owner(session, "second-addon");
        registry.set(first, HumanoidArm.RIGHT, MLuaCFrame.at(1, 0, 0));
        registry.set(second, HumanoidArm.RIGHT, MLuaCFrame.at(0, 2, 0));

        MLuaCFrame combined = registry.composed(HumanoidArm.RIGHT, 1.0f);
        assertEquals(1.0, combined.x(), 1.0e-9);
        assertEquals(2.0, combined.y(), 1.0e-9);
        assertEquals(1.0, MLuaCFrame.fromLua(registry.getValue(first, HumanoidArm.RIGHT)).x(), 1.0e-9);

        registry.clearOwned(first);
        combined = registry.composed(HumanoidArm.RIGHT, 1.0f);
        assertEquals(0.0, combined.x(), 1.0e-9);
        assertEquals(2.0, combined.y(), 1.0e-9);
        assertTrue(registry.getValue(first, HumanoidArm.RIGHT).isnil());
    }

    @Test
    void interpolatesCallbacksAndKeepsTheLastPoseAfterCallbackFailure() {
        MLuaHandPoseRegistry registry = new MLuaHandPoseRegistry();
        var owner = new MLuaHandPoseRegistry.Owner(new Object(), "animated-addon");
        LuaValue callback = JsePlatform.standardGlobals().load("return function() end").call();
        registry.set(owner, HumanoidArm.RIGHT, MLuaCFrame.IDENTITY);
        registry.setCallback(owner, HumanoidArm.RIGHT, callback,
            (player, hand, stack, arm) -> MLuaCFrame.at(2, 0, 0), new Object());

        registry.sample(null, HumanoidArm.RIGHT);
        assertEquals(1.0, registry.composed(HumanoidArm.RIGHT, 0.5f).x(), 1.0e-9);

        registry.setCallback(owner, HumanoidArm.RIGHT, callback,
            (player, hand, stack, arm) -> { throw new IllegalStateException("sample failure"); }, new Object());
        registry.sample(null, HumanoidArm.RIGHT);
        assertEquals(2.0, registry.composed(HumanoidArm.RIGHT, 1.0f).x(), 1.0e-9);
        assertTrue(registry.getValue(owner, HumanoidArm.RIGHT).istable());
    }
}
