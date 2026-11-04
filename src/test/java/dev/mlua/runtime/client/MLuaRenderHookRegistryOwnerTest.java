package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class MLuaRenderHookRegistryOwnerTest {
    @Test
    void ownerKeysUseRuntimeIdentityAndAddonId() {
        Object runtime = new Object();
        Object otherRuntime = new Object();

        var owner = new MLuaRenderHookRegistry.Owner(runtime, "example_addon");
        var sameOwner = new MLuaRenderHookRegistry.Owner(runtime, "example_addon");
        var differentAddon = new MLuaRenderHookRegistry.Owner(runtime, "another_addon");
        var differentRuntime = new MLuaRenderHookRegistry.Owner(otherRuntime, "example_addon");

        assertEquals(owner, sameOwner);
        assertEquals(owner.hashCode(), sameOwner.hashCode());
        assertNotEquals(owner, differentAddon);
        assertNotEquals(owner, differentRuntime);
    }
}
