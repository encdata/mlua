package dev.mlua.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MLuaDefinitionSnapshotTest {
    @Test
    void copiesMutableMapKeysBeforeInsertingThemIntoSnapshot() {
        List<String> keyValues = new ArrayList<>(List.of("before"));
        Map<String, Object> mutableKey = new LinkedHashMap<>();
        mutableKey.put("parts", keyValues);

        Map<Object, Object> nested = new LinkedHashMap<>();
        nested.put(mutableKey, "registered value");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("lookup", nested);

        Map<String, Object> snapshot = MLuaDefinitionSnapshot.freeze(definition);
        @SuppressWarnings("unchecked")
        Map<Object, Object> snapshotLookup = (Map<Object, Object>) snapshot.get("lookup");
        Object snapshotKey = snapshotLookup.keySet().iterator().next();

        assertNotSame(mutableKey, snapshotKey);
        keyValues.add("after");
        mutableKey.put("newField", true);

        @SuppressWarnings("unchecked")
        Map<String, Object> copiedKey = (Map<String, Object>) snapshotKey;
        @SuppressWarnings("unchecked")
        List<String> copiedValues = (List<String>) copiedKey.get("parts");
        assertEquals(List.of("before"), copiedValues);
        assertEquals(Map.of("parts", List.of("before")), copiedKey);
        assertEquals("registered value", snapshotLookup.get(snapshotKey));
    }

    @Test
    void rejectsCyclesReachedThroughMapKeys() {
        Map<String, Object> cyclicKey = new LinkedHashMap<>();
        cyclicKey.put("self", cyclicKey);
        Map<Object, Object> nested = new IdentityHashMap<>();
        nested.put(cyclicKey, "value");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("lookup", nested);

        assertThrows(IllegalArgumentException.class, () -> MLuaDefinitionSnapshot.freeze(definition));
    }

    @Test
    void rejectsMapKeysThatBecomeEqualAfterSnapshotCopying() {
        Map<String, Object> firstKey = new LinkedHashMap<>();
        firstKey.put("part", "head");
        Map<String, Object> secondKey = new LinkedHashMap<>();
        secondKey.put("part", "head");
        Map<Object, Object> identityKeyedMap = new IdentityHashMap<>();
        identityKeyedMap.put(firstKey, "first");
        identityKeyedMap.put(secondKey, "second");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("lookup", identityKeyedMap);

        assertThrows(IllegalArgumentException.class, () -> MLuaDefinitionSnapshot.freeze(definition));
    }

    @Test
    void validationCannotMutateTheDefinitionPassedToProviderConstruction() {
        String[] sourceArray = {"original"};
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("values", sourceArray);

        Map<String, Object> forCreation = MLuaDefinitionSnapshot.validateAndFreeze(definition, candidate -> {
            ((String[]) candidate.get("values"))[0] = "changed during validation";
        });

        assertEquals("original", ((String[]) forCreation.get("values"))[0]);
        assertEquals("original", sourceArray[0]);
    }
}
