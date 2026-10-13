package dev.mlua.runtime.client;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
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
        LuaTable overworldOffset = new LuaTable();
        overworldOffset.set("X", LuaValue.valueOf(1));
        LuaTable netherOffset = new LuaTable();
        netherOffset.set("X", LuaValue.valueOf(2));
        MLuaClientModelManager.setOffset(clientOverworld, "head", overworldOffset);
        MLuaClientModelManager.setOffset(clientNether, "head", netherOffset);

        MLuaClientModelManager.clearEntity(clientOverworld);

        assertNull(MLuaClientModelManager.getPartState(clientOverworld, "head"));
        assertSame(netherOffset, MLuaClientModelManager.getPartState(clientNether, "head").offset());
    }

    @Test
    void ownersComposeByLatestWriteAndClearRevealsEarlierOwner() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope scope = new MLuaEntityScope(Side.CLIENT, "minecraft:overworld", uuid, 9);
        var first = new MLuaClientModelManager.Owner(new Object(), "first");
        var second = new MLuaClientModelManager.Owner(new Object(), "second");
        LuaTable firstOffset = new LuaTable();
        firstOffset.set("X", 1);
        LuaTable secondOffset = new LuaTable();
        secondOffset.set("X", 2);

        MLuaClientModelManager.setOffset(scope, "head", firstOffset, first);
        MLuaClientModelManager.setOffset(scope, "head", secondOffset, second);
        assertSame(secondOffset, MLuaClientModelManager.getPartState(scope, "head").offset());
        assertSame(firstOffset, MLuaClientModelManager.getState(scope, first).get("head").offset());

        MLuaClientModelManager.clearOwned(second);
        assertSame(firstOffset, MLuaClientModelManager.getPartState(scope, "head").offset());
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
