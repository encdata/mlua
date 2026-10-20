package dev.mlua.modules.render.service;

import dev.mlua.module.MLuaRegistration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Resolves, validates, and applies the registered properties for a RenderObject class. */
final class MLuaRenderObjectProperties {
    record Plan(LuaTable descriptors, Map<String, LuaValue> values, boolean enableWorldSpace) { }

    private MLuaRenderObjectProperties() { }

    static String className(LuaTable properties) {
        LuaValue value = read(properties, "ClassName");
        if (value.isnil()) return "RenderObject";
        String className = value.checkjstring();
        if (className.isBlank()) throw new LuaError("[MLua] RenderObject ClassName cannot be empty.");
        return className;
    }

    static LuaTable descriptors(MLuaRegistration registration, String className) {
        List<String> hierarchy = new ArrayList<>();
        String current = className;
        while (current != null) {
            hierarchy.add(current);
            current = registration.superclassName(current);
        }
        Collections.reverse(hierarchy);

        LuaTable merged = new LuaTable();
        for (String typeName : hierarchy) {
            LuaTable type = registration.classTable(typeName);
            LuaValue descriptors = type == null ? LuaValue.NIL : type.rawget("__mlua_properties");
            if (!descriptors.istable()) continue;
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = descriptors.checktable().next(key);
                key = entry.arg1();
                if (key.isnil()) break;
                merged.set(key, entry.arg(2));
            }
        }
        return merged;
    }

    static Plan validate(LuaTable properties, LuaTable descriptors, String... extraProperties) {
        Set<String> extras = Set.of(extraProperties);
        Set<String> provided = new HashSet<>();
        Map<String, LuaValue> values = new TreeMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = properties.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TSTRING) {
                throw new LuaError("[MLua] RenderObject properties must use registered string names.");
            }
            String suppliedName = key.tojstring();
            if (upperCamel(suppliedName).equals("ClassName")) continue;
            String propertyName = registeredPropertyName(descriptors, suppliedName);
            if (extras.contains(upperCamel(suppliedName))) continue;
            if (propertyName == null) {
                throw new LuaError("[MLua] Unknown RenderObject property '" + suppliedName
                    + "'. Use a registered writable RenderObject property.");
            }
            if (!provided.add(propertyName)) {
                throw new LuaError("[MLua] RenderObject property '" + propertyName
                    + "' was supplied more than once through aliases.");
            }
            LuaValue descriptor = descriptors.rawget(propertyName);
            if (!descriptor.istable()) {
                throw new LuaError("[MLua] Unknown RenderObject property '" + suppliedName
                    + "'. Use a registered writable RenderObject property.");
            }
            if (!descriptor.checktable().rawget("set").isfunction()) {
                throw new LuaError("[MLua] RenderObject property '" + propertyName + "' is read-only.");
            }
            values.put(propertyName, entry.arg(2));
        }

        if (provided.contains("Target") && provided.contains("WorldSpace")) {
            throw new LuaError("[MLua] Set Target or WorldSpace in one RenderObject definition, not both.");
        }
        if (values.containsKey("Transparency") && values.containsKey("Alpha")) {
            throw new LuaError("[MLua] RenderObject properties Alpha and Transparency describe the same value; provide only one.");
        }
        if (values.containsKey("Resource") && values.containsKey("Text")) {
            throw new LuaError("[MLua] RenderObject properties Resource and Text refer to the same content value; provide only one.");
        }
        boolean enableWorldSpace = (values.containsKey("WorldPosition") || values.containsKey("EndWorldPosition"))
            && !values.containsKey("WorldSpace") && !values.containsKey("Target");
        return new Plan(descriptors, Collections.unmodifiableMap(values), enableWorldSpace);
    }

    static void apply(LuaTable instance, Plan plan) {
        for (Map.Entry<String, LuaValue> entry : plan.values().entrySet()) {
            LuaValue descriptor = plan.descriptors().rawget(entry.getKey());
            LuaValue setter = descriptor.istable() ? descriptor.checktable().rawget("set") : LuaValue.NIL;
            if (!setter.isfunction()) {
                throw new LuaError("[MLua] RenderObject property '" + entry.getKey() + "' has no registered setter.");
            }
            setter.invoke(LuaValue.varargsOf(new LuaValue[]{instance, entry.getValue()}));
        }
        if (plan.enableWorldSpace()) {
            LuaValue descriptor = plan.descriptors().rawget("WorldSpace");
            LuaValue setter = descriptor.istable() ? descriptor.checktable().rawget("set") : LuaValue.NIL;
            if (setter.isfunction()) setter.invoke(LuaValue.varargsOf(new LuaValue[]{instance, LuaValue.TRUE}));
        }
    }

    private static String registeredPropertyName(LuaTable descriptors, String suppliedName) {
        if (descriptors.rawget(suppliedName).istable()) return suppliedName;
        String upperCamel = upperCamel(suppliedName);
        if (descriptors.rawget(upperCamel).istable()) return upperCamel;
        String lowerCamel = suppliedName.isEmpty() ? suppliedName
            : Character.toLowerCase(suppliedName.charAt(0)) + suppliedName.substring(1);
        return descriptors.rawget(lowerCamel).istable() ? lowerCamel : null;
    }

    private static String upperCamel(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    static LuaValue read(LuaTable properties, String name) {
        LuaValue value = properties.get(name);
        if (name.isEmpty()) return value;
        String lowerCamel = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        LuaValue alias = properties.get(lowerCamel);
        if (!value.isnil() && !alias.isnil()) {
            throw new LuaError("[MLua] RenderObject properties '" + name + "' and '" + lowerCamel
                + "' are aliases; provide only one.");
        }
        return value.isnil() ? alias : value;
    }
}
