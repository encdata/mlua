package dev.mlua.core.classes;

import java.util.HashMap;
import java.util.Map;

/** Owns the single-parent graph used by class validation and ancestry queries. */
final class MLuaClassHierarchy {
    private final Map<String, String> parents = new HashMap<>();

    String parentOf(String className) {
        return this.parents.get(className);
    }

    boolean hasChildren(String className) {
        return this.parents.containsValue(className);
    }

    String removeClass(String className) {
        return this.parents.remove(className);
    }

    String registerParent(String child, String parent) {
        return this.parents.putIfAbsent(child, parent);
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
