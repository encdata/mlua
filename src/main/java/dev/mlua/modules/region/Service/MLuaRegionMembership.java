package dev.mlua.modules.region.Service;

import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.HashSet;
import java.util.Set;

/** Computes region membership transitions using stable entity identity. */
final class MLuaRegionMembership {
    private MLuaRegionMembership() {
    }

    static Delta transition(Set<MLuaEntityScope> current, Set<MLuaEntityScope> previous) {
        Set<MLuaEntityScope> entered = new HashSet<>(current);
        entered.removeAll(previous);
        Set<MLuaEntityScope> exited = new HashSet<>(previous);
        exited.removeAll(current);
        Set<MLuaEntityScope> within = new HashSet<>(current);
        within.retainAll(previous);
        return new Delta(Set.copyOf(entered), Set.copyOf(exited), Set.copyOf(within));
    }

    record Delta(Set<MLuaEntityScope> entered, Set<MLuaEntityScope> exited, Set<MLuaEntityScope> within) {
    }
}
