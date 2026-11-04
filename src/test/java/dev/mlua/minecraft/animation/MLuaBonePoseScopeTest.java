package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.common.enums.EasingDirection;
import dev.mlua.common.enums.EasingStyle;
import dev.mlua.math.MLuaCFrame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaBonePoseScopeTest {
    @AfterEach
    void clearPoses() {
        MLuaBonePose.clearAll();
    }

    @Test
    void sameEntityIdKeepsPosesSeparateBySideAndDimension() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope serverOverworld = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", uuid, 12);
        MLuaEntityScope clientOverworld = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", uuid, 12);
        MLuaEntityScope serverNether = new MLuaEntityScope(Side.SERVER, "minecraft:the_nether", uuid, 12);
        MLuaCFrame serverPose = MLuaCFrame.angles(0.1, 0.2, 0.3);
        MLuaCFrame clientPose = MLuaCFrame.angles(0.4, 0.5, 0.6);
        MLuaCFrame netherPose = MLuaCFrame.angles(0.7, 0.8, 0.9);
        MLuaBonePose.setPose(serverOverworld, MLuaBonePose.BONE_HEAD, serverPose);
        MLuaBonePose.setPose(clientOverworld, MLuaBonePose.BONE_HEAD, clientPose);
        MLuaBonePose.setPose(serverNether, MLuaBonePose.BONE_HEAD, netherPose);

        MLuaBonePose.clearPose(clientOverworld);

        assertEquals(serverPose, MLuaBonePose.getBonePose(serverOverworld, MLuaBonePose.BONE_HEAD));
        assertNull(MLuaBonePose.getBonePose(clientOverworld, MLuaBonePose.BONE_HEAD));
        assertEquals(netherPose, MLuaBonePose.getBonePose(serverNether, MLuaBonePose.BONE_HEAD));
    }

    @Test
    void removingOneScopeLeavesOtherTracksWithTheSameNumericId() {
        MLuaEntityScope server = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 21);
        MLuaEntityScope client = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 21);
        MLuaAnimation animation = new MLuaAnimation(List.of(
                new MLuaAnimation.Keyframe(0, Map.of(), EasingStyle.Linear, EasingDirection.In)));
        MLuaAnimationController.loadAnimation(server, animation, AnimationPriority.Idle);
        MLuaAnimationController.loadAnimation(client, animation, AnimationPriority.Idle);

        MLuaAnimationController.removeEntity(server);

        assertEquals(0, MLuaAnimationController.getTracks(server).size());
        assertEquals(1, MLuaAnimationController.getTracks(client).size());
        MLuaAnimationController.removeEntity(client);
    }

    @Test
    void poseWritesAndReloadCleanupStayOwnedWhileRenderingUsesLatestPose() {
        MLuaEntityScope scope = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 33);
        Object firstRuntime = new Object();
        Object secondRuntime = new Object();
        MLuaCFrame firstPose = MLuaCFrame.angles(0.1, 0.2, 0.3);
        MLuaCFrame secondPose = MLuaCFrame.angles(0.4, 0.5, 0.6);
        MLuaBonePose.setPose(scope, MLuaBonePose.BONE_HEAD, firstPose, firstRuntime);
        MLuaBonePose.setPose(scope, MLuaBonePose.BONE_HEAD, secondPose, secondRuntime);

        assertEquals(firstPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD, firstRuntime));
        assertEquals(secondPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD, secondRuntime));
        assertEquals(secondPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD));

        MLuaBonePose.clearRuntime(firstRuntime, Side.SERVER);

        assertNull(MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD, firstRuntime));
        assertEquals(secondPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD));
    }

    @Test
    void clearingAnimationOutputPreservesTheSameRuntimesManualPose() {
        MLuaEntityScope scope = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 34);
        Object runtime = new Object();
        MLuaCFrame manualPose = MLuaCFrame.angles(0.1, 0.2, 0.3);
        MLuaCFrame animationPose = MLuaCFrame.angles(0.4, 0.5, 0.6);

        MLuaBonePose.setPose(scope, MLuaBonePose.BONE_HEAD, manualPose, runtime);
        MLuaBonePose.setAnimatedPose(scope, MLuaBonePose.BONE_HEAD, animationPose, runtime);
        assertEquals(animationPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD, runtime));

        MLuaBonePose.clearAnimatedPose(scope, runtime);

        assertEquals(manualPose, MLuaBonePose.getBonePose(scope, MLuaBonePose.BONE_HEAD, runtime));
    }

    @Test
    void renderPoseInterpolatesOnlyTheRequestedEntityScope() {
        MLuaEntityScope rendered = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 35);
        MLuaEntityScope other = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 36);
        Object runtime = new Object();
        MLuaCFrame previous = MLuaCFrame.angles(0.1, 0.2, 0.3);
        MLuaCFrame current = MLuaCFrame.angles(0.5, 0.6, 0.7);
        MLuaCFrame otherPose = MLuaCFrame.angles(0.9, 0.8, 0.7);

        MLuaBonePose.setAnimatedPose(rendered, MLuaBonePose.BONE_HEAD, previous, runtime);
        MLuaBonePose.beginAnimatedUpdate(rendered, runtime);
        MLuaBonePose.setAnimatedPose(rendered, MLuaBonePose.BONE_HEAD, current, runtime);
        MLuaBonePose.setPose(other, MLuaBonePose.BONE_HEAD, otherPose);

        assertCFrameEquals(previous.lerp(current, 0.5),
                MLuaBonePose.getRenderPose(rendered, 0.5f).get(MLuaBonePose.BONE_HEAD));
        assertCFrameEquals(otherPose, MLuaBonePose.getRenderPose(other, 1.0f).get(MLuaBonePose.BONE_HEAD));
    }

    @Test
    void nullScopeReadsAndCleanupRemainHarmless() {
        assertEquals(Map.of(), MLuaBonePose.getPose((MLuaEntityScope) null));
        assertEquals(Map.of(), MLuaBonePose.getRenderPose((MLuaEntityScope) null));
        MLuaBonePose.clearPose((MLuaEntityScope) null);
    }

    private static void assertCFrameEquals(MLuaCFrame expected, MLuaCFrame actual) {
        assertNotNull(actual);
        assertEquals(expected.x(), actual.x(), 1.0e-9);
        assertEquals(expected.y(), actual.y(), 1.0e-9);
        assertEquals(expected.z(), actual.z(), 1.0e-9);
        assertEquals(expected.r00(), actual.r00(), 1.0e-9);
        assertEquals(expected.r01(), actual.r01(), 1.0e-9);
        assertEquals(expected.r02(), actual.r02(), 1.0e-9);
        assertEquals(expected.r10(), actual.r10(), 1.0e-9);
        assertEquals(expected.r11(), actual.r11(), 1.0e-9);
        assertEquals(expected.r12(), actual.r12(), 1.0e-9);
        assertEquals(expected.r20(), actual.r20(), 1.0e-9);
        assertEquals(expected.r21(), actual.r21(), 1.0e-9);
        assertEquals(expected.r22(), actual.r22(), 1.0e-9);
    }
}
