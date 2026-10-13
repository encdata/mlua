package dev.mlua.modules.region.Service;

import dev.mlua.common.enums.Side;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaRegionServiceScopeTest {
    @Test
    void sameEntityIdInDifferentDimensionsHasIndependentEnterTransitions() {
        UUID uuid = UUID.randomUUID();
        MLuaEntityScope overworld = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", uuid, 31);
        MLuaEntityScope nether = new MLuaEntityScope(Side.SERVER, "minecraft:the_nether", uuid, 31);

        var firstTick = MLuaRegionMembership.transition(Set.of(overworld, nether), Set.of());
        var nextTick = MLuaRegionMembership.transition(Set.of(nether), Set.of(overworld, nether));

        assertEquals(Set.of(overworld, nether), firstTick.entered());
        assertEquals(Set.of(overworld), nextTick.exited());
        assertEquals(Set.of(nether), nextTick.within());
    }

    @Test
    void entityIdReuseWithDifferentUuidCountsAsExitAndEnter() {
        MLuaEntityScope removed = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 31);
        MLuaEntityScope replacement = new MLuaEntityScope(Side.SERVER, "minecraft:overworld", UUID.randomUUID(), 31);

        var transition = MLuaRegionMembership.transition(Set.of(replacement), Set.of(removed));

        assertEquals(Set.of(removed), transition.exited());
        assertEquals(Set.of(replacement), transition.entered());
        assertEquals(Set.of(), transition.within());
    }
}
