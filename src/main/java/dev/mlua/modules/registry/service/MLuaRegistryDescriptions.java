package dev.mlua.modules.registry.service;

import java.util.ArrayList;
import java.util.List;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Reads the user-facing description lines attached to registered content. */
final class MLuaRegistryDescriptions {
    private MLuaRegistryDescriptions() { }

    static List<String> read(LuaTable properties) {
        if (properties == null) return null;
        LuaValue description = properties.get("description");
        if (description.isnil()) description = properties.get("tooltip");
        if (description.isnil()) return null;
        if (description.type() == LuaValue.TSTRING) return List.of(description.tojstring());
        if (!description.istable()) {
            throw new LuaError("[MLua] Content description must be a string or a dense array of strings.");
        }

        LuaTable lines = description.checktable();
        int length = lines.rawlen();
        int entries = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = lines.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                throw new LuaError("[MLua] Content description must be a dense array of strings with no extra keys.");
            }
            entries++;
        }
        if (entries != length) {
            throw new LuaError("[MLua] Content description must be a dense array with no missing entries.");
        }

        List<String> result = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            LuaValue line = lines.rawget(index);
            if (!line.isstring()) {
                throw new LuaError("[MLua] Content description array entries must be strings.");
            }
            result.add(line.tojstring());
        }
        return result.isEmpty() ? null : result;
    }
}
