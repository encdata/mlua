package dev.mlua.runtime.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaAnimationPlayerTest {
    @Test
    void stoppingAnAnimationClearsTheSelectedPose() {
        MLuaBBModelAsset asset = asset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("idle", asset, false, 1.0F);
        player.stop();

        assertNull(player.active());
        assertFalse(player.playing());
        assertEquals(0.0F, player.time(), 0.0001F);
    }

    @Test
    void naturallyHeldAnimationKeepsItsFinalPoseUntilStopped() {
        MLuaBBModelAsset asset = asset();
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();

        player.play("idle", asset, false, 1.0F);
        player.tick(2.0F, asset);

        assertEquals("idle", player.active());
        assertFalse(player.playing());
        assertEquals(1.0F, player.time(), 0.0001F);
    }

    private static MLuaBBModelAsset asset() {
        return MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "animations": [{"name": "idle", "loop": "hold", "length": 1, "animators": {}}]
            }
            """);
    }
}
