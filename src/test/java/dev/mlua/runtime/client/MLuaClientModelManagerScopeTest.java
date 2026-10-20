package dev.mlua.runtime.client;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.math.MLuaVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaClientModelManagerScopeTest {
    @AfterEach
    void clearState() {
        MLuaClientModelManager.clearAll();
    }

    @Test
    void clearingModelStateForOneEntityScopePreservesMatchingIdsElsewhere() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope clientOverworld = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", uuid, 9);
        MLuaEntityScope clientNether = new MLuaEntityScope(Side.CLIENT, "minecraft:the_nether", uuid, 9);
        LuaTable overworldOffset = MLuaVector.vector3(1, 0, 0);
        LuaTable netherOffset = MLuaVector.vector3(2, 0, 0);
        MLuaClientModelManager.setOffset(clientOverworld, "head", overworldOffset);
        MLuaClientModelManager.setOffset(clientNether, "head", netherOffset);

        MLuaClientModelManager.clearEntity(clientOverworld);

        assertNull(MLuaClientModelManager.getPartState(clientOverworld, "head"));
        assertEquals(2.0, MLuaClientModelManager.getPartState(clientNether, "head").offset().get("X").todouble());
    }

    @Test
    void ownersComposeByLatestWriteAndClearRevealsEarlierOwner() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope scope = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", uuid, 9);
        var first = new MLuaClientModelManager.Owner(new Object(), "first");
        var second = new MLuaClientModelManager.Owner(new Object(), "second");
        LuaTable firstOffset = MLuaVector.vector3(1, 0, 0);
        LuaTable secondOffset = MLuaVector.vector3(2, 0, 0);

        MLuaClientModelManager.setOffset(scope, "head", firstOffset, first);
        MLuaClientModelManager.setOffset(scope, "head", secondOffset, second);
        assertEquals(2.0, MLuaClientModelManager.getPartState(scope, "head").offset().get("X").todouble());
        assertEquals(1.0, MLuaClientModelManager.getState(scope, first).get("head").offset().get("X").todouble());

        MLuaClientModelManager.clearOwned(second);
        assertEquals(1.0, MLuaClientModelManager.getPartState(scope, "head").offset().get("X").todouble());
    }

    @Test
    void entityRemovalClearsOverridesFromEveryOwner() {
        MLuaEntityScope scope = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", UUID.randomUUID(), 10);
        var first = new MLuaClientModelManager.Owner(new Object(), "first");
        var second = new MLuaClientModelManager.Owner(new Object(), "second");
        MLuaClientModelManager.setVisible(scope, "head", false, first);
        MLuaClientModelManager.setVisible(scope, "body", false, second);

        MLuaClientModelManager.clearEntityForAllOwners(scope);

        assertNull(MLuaClientModelManager.getPartState(scope, "head"));
        assertNull(MLuaClientModelManager.getPartState(scope, "body"));
    }
}
