package dev.mlua.minecraft.scripted;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Owns bounded Lua/NBT conversion and persisted data for a scripted entity. */
final class MLuaScriptedEntityPersistentData {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_ENTRIES = 16_384;

    private CompoundTag value = new CompoundTag();

    CompoundTag value() {
        return this.value;
    }

    void load(CompoundTag data) {
        this.value = data == null ? new CompoundTag() : data;
    }

    LuaTable toLua() {
        return this.compoundToLua(this.value, new Budget(), 0).checktable();
    }

    void replaceFromLua(LuaTable table) {
        CompoundTag replacement = new CompoundTag();
        Set<LuaTable> activeTables = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            this.luaToCompound(table, replacement, activeTables, 0, new Budget());
        } catch (IllegalArgumentException invalidData) {
            throw new LuaError("[MLua] ScriptedEntity:SetData could not convert this table to NBT: "
                + invalidData.getMessage());
        }
        this.value = replacement;
    }

    private LuaValue compoundToLua(CompoundTag tag, Budget budget, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new LuaError("[MLua] ScriptedEntity:GetData supports nested data up to " + MAX_DEPTH + " levels deep.");
        }
        LuaTable table = new LuaTable();
        for (String key : keys(tag)) {
            budget.addEntry("GetData");
            table.set(key, this.tagToLua(tag.get(key), budget, depth + 1));
        }
        return table;
    }

    private LuaValue tagToLua(Tag tag, Budget budget, int depth) {
        if (tag instanceof ByteTag byteTag) {
            return LuaValue.valueOf(booleanValue(byteTag));
        } else if (tag instanceof NumericTag numericTag) {
            return LuaValue.valueOf(numericValue(numericTag));
        } else if (tag instanceof StringTag stringTag) {
            return LuaValue.valueOf(stringValue(stringTag));
        } else if (tag instanceof CompoundTag compound) {
            return this.compoundToLua(compound, budget, depth);
        } else if (tag instanceof ListTag listTag) {
            if (depth >= MAX_DEPTH) {
                throw new LuaError("[MLua] ScriptedEntity:GetData supports nested data up to " + MAX_DEPTH + " levels deep.");
            }
            LuaTable list = new LuaTable();
            for (int index = 0; index < listTag.size(); index++) {
                budget.addEntry("GetData");
                list.set(index + 1, this.tagToLua(listTag.get(index), budget, depth + 1));
            }
            return list;
        }
        return LuaValue.NIL;
    }

    private void luaToCompound(LuaTable table, CompoundTag tag, Set<LuaTable> activeTables, int depth,
                               Budget budget) {
        this.enterTable(table, activeTables, depth);
        try {
            LuaValue key = LuaValue.NIL;
            while (true) {
                Varargs next = table.next(key);
                key = next.arg1();
                if (key.isnil()) break;
                if (key.type() != LuaValue.TSTRING) {
                    throw new IllegalArgumentException("compound table keys must be strings; got " + key.typename());
                }
                budget.addEntry("SetData");
                tag.put(key.tojstring(), this.valueToTag(next.arg(2), activeTables, depth + 1, budget));
            }
        } finally {
            activeTables.remove(table);
        }
    }

    private Tag valueToTag(LuaValue value, Set<LuaTable> activeTables, int depth, Budget budget) {
        if (value.type() == LuaValue.TBOOLEAN) {
            return ByteTag.valueOf(value.toboolean());
        } else if (value.type() == LuaValue.TNUMBER) {
            double number = value.todouble();
            if (!Double.isFinite(number)) {
                throw new LuaError("[MLua] ScriptedEntity:SetData cannot save NaN or infinite numbers.");
            }
            if (number == Math.rint(number) && number >= Long.MIN_VALUE && number < 0x1.0p63) {
                return LongTag.valueOf((long) number);
            }
            return DoubleTag.valueOf(number);
        } else if (value.type() == LuaValue.TSTRING) {
            return StringTag.valueOf(value.tojstring());
        } else if (value.istable()) {
            LuaTable table = value.checktable();
            this.enterTable(table, activeTables, depth);
            try {
                int arrayLength = arrayLength(table, budget);
                if (arrayLength < 0) {
                    CompoundTag nested = new CompoundTag();
                    LuaValue key = LuaValue.NIL;
                    while (true) {
                        Varargs next = table.next(key);
                        key = next.arg1();
                        if (key.isnil()) break;
                        if (key.type() != LuaValue.TSTRING) {
                            throw new IllegalArgumentException("compound table keys must be strings; got " + key.typename());
                        }
                        nested.put(key.tojstring(), this.valueToTag(next.arg(2), activeTables, depth + 1, budget));
                    }
                    return nested;
                }
                ListTag list = new ListTag();
                for (int index = 1; index <= arrayLength; index++) {
                    if (!list.add(this.valueToTag(table.get(index), activeTables, depth + 1, budget))) {
                        throw new IllegalArgumentException("Lua array values must use one NBT value type");
                    }
                }
                return list;
            } finally {
                activeTables.remove(table);
            }
        }
        throw new IllegalArgumentException("unsupported Lua value type " + value.typename()
            + "; only booleans, finite numbers, strings, maps, and dense arrays can be saved");
    }

    /** Returns the dense one-based array length, or -1 for a string-keyed map. */
    private static int arrayLength(LuaTable table, Budget budget) {
        LuaValue key = LuaValue.NIL;
        int count = 0;
        int maximum = 0;
        boolean hasNumberKeys = false;
        boolean hasStringKeys = false;
        while (true) {
            Varargs entry = table.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            count++;
            budget.addEntry("SetData");
            if (key.type() == LuaValue.TSTRING) {
                hasStringKeys = true;
            } else if (key.type() == LuaValue.TNUMBER) {
                hasNumberKeys = true;
                double index = key.todouble();
                if (!Double.isFinite(index) || index < 1 || index > Integer.MAX_VALUE || index != Math.rint(index)) {
                    throw new IllegalArgumentException("array keys must be positive whole numbers");
                }
                maximum = Math.max(maximum, (int) index);
            } else {
                throw new IllegalArgumentException("table keys must be strings or positive array indexes; got "
                    + key.typename());
            }
        }
        if (hasNumberKeys && hasStringKeys) throw new IllegalArgumentException("a table cannot mix map keys and array indexes");
        if (!hasNumberKeys) return -1;
        if (maximum != count) throw new IllegalArgumentException("arrays must use dense indexes starting at one");
        return maximum;
    }

    private static void enterTable(LuaTable table, Set<LuaTable> activeTables, int depth) {
        if (depth >= MAX_DEPTH) {
            throw new LuaError("[MLua] ScriptedEntity:SetData supports nested tables up to " + MAX_DEPTH + " levels deep.");
        }
        if (!activeTables.add(table)) throw new LuaError("[MLua] ScriptedEntity:SetData cannot save cyclic Lua tables.");
    }

    private static Set<String> keys(CompoundTag tag) {
        //--version 26.1
        return tag.keySet();
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getAllKeys();
        //--endversion
    }

    private static boolean booleanValue(ByteTag tag) {
        //--version 26.1
        return tag.byteValue() != 0;
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getAsByte() != 0;
        //--endversion
    }

    private static double numericValue(NumericTag tag) {
        //--version 26.1
        return tag.doubleValue();
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getAsDouble();
        //--endversion
    }

    private static String stringValue(StringTag tag) {
        //--version 26.1
        return tag.value();
        //--endversion
        //--version 1.20.1,1.21.1
        return tag.getAsString();
        //--endversion
    }

    private static final class Budget {
        private int entries;

        private void addEntry(String method) {
            if (++this.entries > MAX_ENTRIES) {
                throw new LuaError("[MLua] ScriptedEntity:" + method + " exceeds the limit of " + MAX_ENTRIES
                    + " total table entries.");
            }
        }
    }
}
