package dev.mlua.runtime.registry;

import java.util.IdentityHashMap;
import java.util.Map;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Makes bounded, detached snapshots of addon-owned Lua registry definitions. */
final class MLuaRegistryDefinitionSnapshot {
    private MLuaRegistryDefinitionSnapshot() { }

    static Snapshot create(LuaTable source, int maxDepth, int maxEntries) {
        CopyBudget budget = new CopyBudget(maxEntries);
        LuaTable copy = copy(source, new IdentityHashMap<>(), new IdentityHashMap<>(), 0, maxDepth, budget);
        return new Snapshot(copy, maxEntries - budget.remaining);
    }

    static LuaTable copy(LuaTable source, int maxDepth, int maxEntries) {
        return create(source, maxDepth, maxEntries).value();
    }

    private static LuaTable copy(LuaTable source, Map<LuaTable, LuaTable> copies,
                                 IdentityHashMap<LuaTable, Boolean> active, int depth,
                                 int maxDepth, CopyBudget budget) {
        if (depth >= maxDepth) {
            throw new LuaError("[MLua] Registry definitions cannot be nested more than "
                + maxDepth + " tables deep");
        }
        if (active.containsKey(source)) {
            throw new LuaError("[MLua] Registry definitions cannot contain cyclic tables");
        }
        LuaTable existing = copies.get(source);
        if (existing != null) return existing;

        LuaTable result = new LuaTable();
        copies.put(source, result);
        active.put(source, Boolean.TRUE);
        try {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs entry = source.next(key);
                key = entry.arg1();
                if (key.isnil()) return result;
                if (--budget.remaining < 0) {
                    throw new LuaError("[MLua] Registry definition exceeds the table entry limit of "
                        + budget.limit);
                }
                LuaValue value = entry.arg(2);
                LuaValue copiedKey = key.istable()
                    ? copy(key.checktable(), copies, active, depth + 1, maxDepth, budget) : key;
                LuaValue copiedValue = value.istable()
                    ? copy(value.checktable(), copies, active, depth + 1, maxDepth, budget) : value;
                result.rawset(copiedKey, copiedValue);
            }
        } finally {
            active.remove(source);
        }
    }

    record Snapshot(LuaTable value, int entryCount) { }

    private static final class CopyBudget {
        private final int limit;
        private int remaining;

        private CopyBudget(int limit) {
            this.limit = limit;
            this.remaining = limit;
        }
    }
}
