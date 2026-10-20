package dev.mlua.modules.region.service;

import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Computes region membership transitions using stable entity identity. */
final class MLuaRegionMembership {
    private MLuaRegionMembership() {
    }

    static Delta transition(Set<MLuaEntityScope> current, Set<MLuaEntityScope> previous) {
        Set<MLuaEntityScope> entered = Set.copyOf(difference(current, previous));
        Set<MLuaEntityScope> exited = Set.copyOf(difference(previous, current));
        Set<MLuaEntityScope> within = new HashSet<>(current);
        within.retainAll(previous);
        return new Delta(entered, exited, Set.copyOf(within));
    }

    static List<MLuaEntityScope> difference(Set<MLuaEntityScope> candidates,
                                             Set<MLuaEntityScope> excluded) {
        List<MLuaEntityScope> changed = new ArrayList<>();
        for (MLuaEntityScope candidate : candidates) {
            if (!excluded.contains(candidate)) changed.add(candidate);
        }
        sort(changed);
        return changed;
    }

    static List<MLuaEntityScope> ordered(Iterable<MLuaEntityScope> scopes) {
        List<MLuaEntityScope> ordered = new ArrayList<>();
        for (MLuaEntityScope scope : scopes) ordered.add(scope);
        sort(ordered);
        return ordered;
    }

    private static void sort(List<MLuaEntityScope> scopes) {
        scopes.sort(java.util.Comparator.comparing(scope -> scope.uuid().toString()));
    }

    record Delta(Set<MLuaEntityScope> entered, Set<MLuaEntityScope> exited, Set<MLuaEntityScope> within) {
    }
}
