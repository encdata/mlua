package dev.mlua.minecraft.entity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaEntityLookupTest {
    @Test
    void reportsMissingWhenNoDimensionContainsTheId() {
        var result = MLuaEntityLookup.resolveUnique(List.of("overworld", "nether"), ignored -> null);

        assertFalse(result.found());
        assertFalse(result.ambiguous());
        assertNull(result.value());
    }

    @Test
    void returnsTheOnlyDimensionMatch() {
        var result = MLuaEntityLookup.resolveUnique(List.of("overworld", "nether"), dimension ->
                dimension.equals("nether") ? "entity-42" : null);

        assertTrue(result.found());
        assertFalse(result.ambiguous());
        assertEquals("entity-42", result.value());
    }

    @Test
    void reportsAmbiguityInsteadOfSelectingTheFirstDimension() {
        var result = MLuaEntityLookup.resolveUnique(List.of("overworld", "nether"), ignored -> "entity-42");

        assertFalse(result.found());
        assertTrue(result.ambiguous());
        assertNull(result.value());
    }
}
