package dev.mlua.core.classes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.luaj.vm2.LuaTable;

/** Owns weak instance identity and the per-class index used by class lifecycle operations. */
final class MLuaClassLiveInstances {
    private final Map<LuaTable, LuaTable> classesByInstance = new WeakHashMap<>();
    private final Map<String, Set<LuaTable>> instancesByClass = new HashMap<>();

    boolean contains(LuaTable value) {
        return this.classesByInstance.containsKey(value);
    }

    LuaTable classOf(LuaTable instance) {
        return this.classesByInstance.get(instance);
    }

    void attach(LuaTable instance, LuaTable classTable) {
        LuaTable previousClass = this.classesByInstance.put(instance, classTable);
        if (previousClass != null && previousClass != classTable) {
            Set<LuaTable> previousInstances = this.instancesByClass.get(className(previousClass));
            if (previousInstances != null) previousInstances.remove(instance);
        }
        this.instancesByClass.computeIfAbsent(className(classTable),
            ignored -> Collections.newSetFromMap(new WeakHashMap<>())).add(instance);
    }

    List<LuaTable> ofClass(String className) {
        Set<LuaTable> instances = this.instancesByClass.get(className);
        return instances == null ? List.of() : new ArrayList<>(instances);
    }

    List<LuaTable> ofClassAndDescendants(String className, List<String> descendants) {
        List<LuaTable> result = new ArrayList<>();
        result.addAll(this.ofClass(className));
        for (String descendant : descendants) result.addAll(this.ofClass(descendant));
        return result;
    }

    boolean hasInstances(String className) {
        Set<LuaTable> instances = this.instancesByClass.get(className);
        return instances != null && !instances.isEmpty();
    }

    void removeClass(String className) {
        this.instancesByClass.remove(className);
    }

    private static String className(LuaTable classTable) {
        return classTable.rawget("__type").optjstring("");
    }
}
