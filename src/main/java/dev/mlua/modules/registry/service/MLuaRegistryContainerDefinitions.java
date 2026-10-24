package dev.mlua.modules.registry.service;

import dev.mlua.minecraft.MLuaResourceLocations;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Parses inventory restrictions from block and standalone container definitions. */
final class MLuaRegistryContainerDefinitions {
    private MLuaRegistryContainerDefinitions() { }

    static Set<String> allowedItems(String owner, LuaTable definition) {
        LuaValue items = property(definition, "allowedItems", "AllowedItems");
        if (items.isnil()) return Set.of();
        if (items.istable() && items.checktable().next(LuaValue.NIL).arg1().isnil()) return Set.of();

        Set<String> resolved = new LinkedHashSet<>();
        for (String item : MLuaRegistryPropertyReaders.stringValues(items, "Container AllowedItems")) {
            String candidate = item.trim();
            var parsed = MLuaResourceLocations.tryParseOrNull(
                candidate.contains(":") ? candidate : owner + ":" + candidate);
            if (parsed == null) throw new LuaError("[MLua] Invalid container allowed item id: " + item);
            resolved.add(parsed.toString());
        }
        return Set.copyOf(resolved);
    }

    static Map<Integer, Set<String>> slotAllowedItems(String owner, LuaTable definition, int slotLimit) {
        LuaValue slotDefinitions = property(definition, "slots", "Slots");
        if (slotDefinitions.isnil()) return Map.of();
        if (!slotDefinitions.istable()) {
            throw new LuaError("[MLua] Container Slots must be a dense array of per-slot definitions.");
        }

        LuaTable slots = slotDefinitions.checktable();
        int length = slotsLength(slots);
        if (length == 0) return Map.of();
        if (length > slotLimit) {
            throw new LuaError("[MLua] Container Slots has " + length + " entries, but the layout has only "
                + slotLimit + " slots.");
        }

        Map<Integer, Set<String>> result = new TreeMap<>();
        for (int slot = 1; slot <= length; slot++) {
            LuaValue value = slots.get(slot);
            if (!value.istable()) {
                throw new LuaError("[MLua] Container Slots[" + slot + "] must be a slot definition table.");
            }
            LuaTable slotDefinition = value.checktable();
            if (!property(slotDefinition, "allowedItems", "AllowedItems").isnil()) {
                result.put(slot, allowedItems(owner, slotDefinition));
            }
        }
        return Map.copyOf(result);
    }

    private static int slotsLength(LuaTable slots) {
        int count = 0;
        int maximum = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs entry = slots.next(key);
            key = entry.arg1();
            if (key.isnil()) break;
            if (key.type() != LuaValue.TNUMBER) {
                throw new LuaError("[MLua] Container Slots must use one-based integer array indexes.");
            }
            double index = key.todouble();
            if (!Double.isFinite(index) || index < 1 || index > Integer.MAX_VALUE || index != Math.rint(index)) {
                throw new LuaError("[MLua] Container Slots indexes must be positive whole numbers.");
            }
            count++;
            maximum = Math.max(maximum, (int)index);
        }
        if (count != maximum) throw new LuaError("[MLua] Container Slots must be dense and start at index one.");
        return maximum;
    }

    static LuaValue property(LuaTable definition, String... aliases) {
        LuaValue selected = LuaValue.NIL;
        String selectedName = null;
        for (String alias : aliases) {
            LuaValue value = definition.get(alias);
            if (value.isnil()) continue;
            if (selectedName != null) {
                throw new LuaError("[MLua] Container properties '" + selectedName + "' and '" + alias
                    + "' are aliases; provide only one.");
            }
            selected = value;
            selectedName = alias;
        }
        return selected;
    }
}
