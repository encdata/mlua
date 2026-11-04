package dev.mlua.modules.registry.service;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaRegistryIdentityTest {
    @Test
    void qualifiesUnqualifiedNamesAndTrimsWhitespace() {
        assertEquals("sample:rust_sword", MLuaRegistryIdentity.normalize(" rust_sword ", "sample"));
    }

    @Test
    void preservesExplicitNamespaces() {
        assertEquals("other:rust_sword", MLuaRegistryIdentity.normalize("other:rust_sword", "sample"));
    }

    @Test
    void restrictsAddonRegistrationsToTheirOwningNamespace() {
        assertEquals("sample:rust_sword",
            MLuaRegistryIdentity.requireOwnerNamespace("sample:rust_sword", "sample", "Items"));
        assertThrows(LuaError.class,
            () -> MLuaRegistryIdentity.requireOwnerNamespace("other:rust_sword", "sample", "Items"));
    }

    @Test
    void rejectsMalformedAndEmptyIdentifiers() {
        assertThrows(LuaError.class, () -> MLuaRegistryIdentity.normalize(" ", "sample"));
        assertThrows(LuaError.class, () -> MLuaRegistryIdentity.normalize("bad namespace:item", "sample"));
    }
}
