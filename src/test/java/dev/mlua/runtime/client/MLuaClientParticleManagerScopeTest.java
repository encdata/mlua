package dev.mlua.runtime.client;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaClientParticleManagerScopeTest {
    @AfterEach
    void clearEmitters() {
        MLuaClientParticleManager.clearAll();
    }

    @Test
    void entityRemovalOnlyRemovesEmittersAttachedToThatScope() {
        MLuaEntityScope overworld = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 14);
        MLuaEntityScope nether = new MLuaEntityScope(Side.CLIENT, "minecraft:the_nether", UUID.randomUUID(), 14);
        int overworldId = MLuaClientParticleManager.createEmitter("minecraft:flame", 0, 0, 0, 1, 0, 0);
        int netherId = MLuaClientParticleManager.createEmitter("minecraft:flame", 0, 0, 0, 1, 0, 0);
        MLuaClientParticleManager.get(overworldId).setFollowEntity(overworld);
        MLuaClientParticleManager.get(netherId).setFollowEntity(nether);

        MLuaClientParticleManager.removeFollowingEntity(overworld);

        assertNull(MLuaClientParticleManager.get(overworldId));
        assertNotNull(MLuaClientParticleManager.get(netherId));
    }
}
