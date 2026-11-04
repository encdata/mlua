package dev.mlua.module;

import java.util.LinkedHashMap;
import java.util.Map;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Captures and restores only the class-table mutations owned by one module registration. */
final class MLuaModuleClassSnapshot {
    private final LuaTable table;
    private final Map<LuaValue, LuaValue> originalFields;
    private Map<LuaValue, LuaValue> originalMethods;
    private Map<LuaValue, LuaValue> originalMixinMethods;
    private Map<LuaValue, LuaValue> expectedFields;
    private Map<LuaValue, LuaValue> expectedMethods;
    private Map<LuaValue, LuaValue> expectedMixinMethods;

    private MLuaModuleClassSnapshot(LuaTable table, Map<LuaValue, LuaValue> fields,
                              Map<LuaValue, LuaValue> methods, Map<LuaValue, LuaValue> mixinMethods) {
        this.table = table;
        this.originalFields = fields;
        this.originalMethods = methods;
        this.originalMixinMethods = mixinMethods;
    }

    static MLuaModuleClassSnapshot capture(LuaTable table) {
        Map<LuaValue, LuaValue> fields = captureFields(table);
        LuaValue methods = table.rawget("__mlua_doc_methods");
        return new MLuaModuleClassSnapshot(table, fields,
            methods.istable() ? captureFields(methods.checktable()) : null,
            captureMixinMethods(table));
    }

    private static Map<LuaValue, LuaValue> captureMixinMethods(LuaTable table) {
        LuaValue meta = table.getmetatable();
        LuaValue methods = meta.istable() ? meta.checktable().rawget("__mlua_mixin_methods") : LuaValue.NIL;
        return methods.istable() ? captureFields(methods.checktable()) : null;
    }

    private static Map<LuaValue, LuaValue> captureFields(LuaTable table) {
        Map<LuaValue, LuaValue> fields = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return fields;
            fields.put(key, next.arg(2));
        }
    }

    void captureExpected() {
        this.expectedFields = captureFields(this.table);
        LuaValue methods = this.table.rawget("__mlua_doc_methods");
        this.expectedMethods = methods.istable() ? captureFields(methods.checktable()) : null;
        this.expectedMixinMethods = captureMixinMethods(this.table);
    }

    void mergeExpected(MLuaModuleClassSnapshot latest) {
        this.expectedFields = latest.expectedFields;
        this.expectedMethods = latest.expectedMethods;
        this.expectedMixinMethods = latest.expectedMixinMethods;
    }

    void rebaseOriginals(MLuaModuleClassSnapshot removed) {
        if (removed.expectedFields == null) return;
        java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>(removed.originalFields.keySet());
        keys.addAll(removed.expectedFields.keySet());
        for (LuaValue key : keys) {
            LuaValue before = removed.originalFields.getOrDefault(key, LuaValue.NIL);
            LuaValue after = removed.expectedFields.getOrDefault(key, LuaValue.NIL);
            LuaValue thisBefore = this.originalFields.getOrDefault(key, LuaValue.NIL);
            if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                this.originalFields.put(key, before);
            }
        }

        Map<LuaValue, LuaValue> removedBefore = removed.originalMethods == null
            ? Map.of() : removed.originalMethods;
        Map<LuaValue, LuaValue> removedAfter = removed.expectedMethods == null
            ? Map.of() : removed.expectedMethods;
        if (!removedBefore.isEmpty() || !removedAfter.isEmpty()) {
            if (this.originalMethods == null) this.originalMethods = new LinkedHashMap<>();
            java.util.Set<LuaValue> methodNames = new java.util.LinkedHashSet<>(removedBefore.keySet());
            methodNames.addAll(removedAfter.keySet());
            for (LuaValue method : methodNames) {
                LuaValue before = removedBefore.getOrDefault(method, LuaValue.NIL);
                LuaValue after = removedAfter.getOrDefault(method, LuaValue.NIL);
                LuaValue thisBefore = this.originalMethods.getOrDefault(method, LuaValue.NIL);
                if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                    this.originalMethods.put(method, before);
                }
            }
        }

        Map<LuaValue, LuaValue> mixinBefore = removed.originalMixinMethods == null
            ? Map.of() : removed.originalMixinMethods;
        Map<LuaValue, LuaValue> mixinAfter = removed.expectedMixinMethods == null
            ? Map.of() : removed.expectedMixinMethods;
        if (!mixinBefore.isEmpty() || !mixinAfter.isEmpty()) {
            if (this.originalMixinMethods == null) this.originalMixinMethods = new LinkedHashMap<>();
            java.util.Set<LuaValue> names = new java.util.LinkedHashSet<>(mixinBefore.keySet());
            names.addAll(mixinAfter.keySet());
            for (LuaValue name : names) {
                LuaValue before = mixinBefore.getOrDefault(name, LuaValue.NIL);
                LuaValue after = mixinAfter.getOrDefault(name, LuaValue.NIL);
                LuaValue thisBefore = this.originalMixinMethods.getOrDefault(name, LuaValue.NIL);
                if (!sameValue(before, after) && sameValue(thisBefore, after)) {
                    this.originalMixinMethods.put(name, before);
                }
            }
        }
    }

    void restore() {
        if (this.expectedFields == null) this.captureExpected();
        Map<LuaValue, LuaValue> currentFields = captureFields(this.table);
        java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>(this.originalFields.keySet());
        keys.addAll(this.expectedFields.keySet());
        for (LuaValue key : keys) {
            String name = key.optjstring(null);
            if ("__mlua_doc_methods".equals(name)
                || "__mlua_doc_inherited_methods".equals(name)
                || "__mlua_doc_parents".equals(name)
                || "__mlua_doc_mixins".equals(name)) continue;
            LuaValue original = this.originalFields.getOrDefault(key, LuaValue.NIL);
            LuaValue expected = this.expectedFields.getOrDefault(key, LuaValue.NIL);
            LuaValue current = currentFields.getOrDefault(key, LuaValue.NIL);
            if (!sameValue(original, expected) && sameValue(current, expected)) {
                this.table.rawset(key, original);
            }
        }

        restoreMethodDescriptions();
        restoreMixinMethods();
    }

    private void restoreMixinMethods() {
        LuaValue meta = this.table.getmetatable();
        LuaValue methodsValue = meta.istable()
            ? meta.checktable().rawget("__mlua_mixin_methods") : LuaValue.NIL;
        if (!methodsValue.istable()) return;
        LuaTable methods = methodsValue.checktable();
        Map<LuaValue, LuaValue> expected = this.expectedMixinMethods == null
            ? Map.of() : this.expectedMixinMethods;
        java.util.Set<LuaValue> names = new java.util.LinkedHashSet<>();
        if (this.originalMixinMethods != null) names.addAll(this.originalMixinMethods.keySet());
        names.addAll(expected.keySet());
        for (LuaValue name : names) {
            LuaValue before = this.originalMixinMethods == null
                ? LuaValue.NIL : this.originalMixinMethods.getOrDefault(name, LuaValue.NIL);
            LuaValue after = expected.getOrDefault(name, LuaValue.NIL);
            if (!sameValue(before, after) && sameValue(methods.rawget(name), after)) {
                methods.rawset(name, before);
            }
        }
    }

    private void restoreMethodDescriptions() {
        LuaValue currentValue = this.table.rawget("__mlua_doc_methods");
        LuaTable current = currentValue.istable() ? currentValue.checktable() : null;
        if (current == null) return;
        Map<LuaValue, LuaValue> expected = this.expectedMethods == null ? Map.of() : this.expectedMethods;
        java.util.Set<LuaValue> keys = new java.util.LinkedHashSet<>();
        if (this.originalMethods != null) keys.addAll(this.originalMethods.keySet());
        keys.addAll(expected.keySet());
        for (LuaValue key : keys) {
            LuaValue original = this.originalMethods == null
                ? LuaValue.NIL : this.originalMethods.getOrDefault(key, LuaValue.NIL);
            LuaValue after = expected.getOrDefault(key, LuaValue.NIL);
            LuaValue present = current.rawget(key);
            LuaValue expectedMethod = this.expectedFields.getOrDefault(key, LuaValue.NIL);
            if (!sameValue(original, after) && sameValue(present, after)
                && sameValue(this.table.rawget(key), expectedMethod)) {
                current.rawset(key, original);
            }
        }

        LuaValue expectedTable = this.expectedFields.getOrDefault(
            LuaValue.valueOf("__mlua_doc_methods"), LuaValue.NIL);
        boolean originallyHadMethodsTable = this.originalFields
            .getOrDefault(LuaValue.valueOf("__mlua_doc_methods"), LuaValue.NIL).istable();
        if (!originallyHadMethodsTable && current.next(LuaValue.NIL).arg1().isnil()
            && currentValue == expectedTable) {
            this.table.rawset("__mlua_doc_methods", LuaValue.NIL);
        }
    }

    private static boolean sameValue(LuaValue left, LuaValue right) {
        if (left == right) return true;
        if (left == null || right == null || left.type() != right.type()) return false;
        if (left.isnil()) return true;
        if (left.isstring()) return left.tojstring().equals(right.tojstring());
        if (left.isnumber()) return Double.compare(left.todouble(), right.todouble()) == 0;
        if (left.isboolean()) return left.toboolean() == right.toboolean();
        return false;
    }

}
