package dev.mlua.minecraft.scripted;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaScriptedOwnerPolicyTest {
    @Test
    void derivesFallbackOwnershipFromNamespacedDefinitionKeys() {
        assertEquals("myaddon", MLuaScriptedOwnerPolicy.ownerOf(null, "myaddon:stalker"));
        assertEquals("", MLuaScriptedOwnerPolicy.ownerOf(null, "unqualified"));
        assertTrue(MLuaScriptedOwnerPolicy.sameOwner(null, null, "myaddon:item"));
        assertFalse(MLuaScriptedOwnerPolicy.sameOwner(null, null, "another:item"));
    }
}
