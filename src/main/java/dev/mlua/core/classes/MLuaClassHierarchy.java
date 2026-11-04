package dev.mlua.core.classes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns the single-parent graph used by class validation and ancestry queries. */
final class MLuaClassHierarchy {
    private final Map<String, String> parents = new HashMap<>();
    private final Map<String, Set<String>> children = new HashMap<>();

    String parentOf(String className) {
        return this.parents.get(className);
    }

    boolean hasChildren(String className) {
        Set<String> directChildren = this.children.get(className);
        return directChildren != null && !directChildren.isEmpty();
    }

    String removeClass(String className) {
        String parent = this.parents.remove(className);
        if (parent != null) {
            Set<String> siblings = this.children.get(parent);
            if (siblings != null) {
                siblings.remove(className);
                if (siblings.isEmpty()) this.children.remove(parent);
            }
        }
        this.children.remove(className);
        return parent;
    }

    String registerParent(String child, String parent) {
        String existing = this.parents.putIfAbsent(child, parent);
        if (existing == null) this.children.computeIfAbsent(parent, ignored -> new HashSet<>()).add(child);
        return existing;
    }

    List<String> descendantsOf(String className) {
        Set<String> directChildren = this.children.get(className);
        if (directChildren == null || directChildren.isEmpty()) return List.of();
        ArrayDeque<String> pending = new ArrayDeque<>(directChildren);
        Set<String> visited = new HashSet<>();
        List<String> descendants = new ArrayList<>();
        while (!pending.isEmpty()) {
            String child = pending.removeLast();
            if (!visited.add(child)) continue;
            descendants.add(child);
            Set<String> nestedChildren = this.children.get(child);
            if (nestedChildren != null) pending.addAll(nestedChildren);
        }
        return List.copyOf(descendants);
    }

    boolean isAncestorOf(String className, String ancestor) {
        if (className == null || ancestor == null) return false;
        String current = className;
        int remaining = this.traversalLimit();
        while (remaining-- > 0) {
            String parent = this.parents.get(current);
            if (parent == null) return false;
            if (parent.equals(ancestor)) return true;
            current = parent;
        }
        return false;
    }

    boolean wouldCreateCycle(String child, String parent) {
        String current = parent;
        int remaining = this.traversalLimit();
        while (current != null && remaining-- > 0) {
            if (child.equals(current)) return true;
            current = this.parents.get(current);
        }
        return false;
    }

    int traversalLimit() {
        return this.parents.size() + 1;
    }
}
