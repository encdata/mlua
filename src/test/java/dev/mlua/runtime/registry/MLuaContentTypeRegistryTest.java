package dev.mlua.runtime.registry;

import dev.mlua.module.MLuaRegistration;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.lang.reflect.Proxy;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaContentTypeRegistryTest {
    @Test
    void childTypesInheritFrozenDefaultsAndOwnerCleanupRemovesDefinitions() {
        MLuaRegistration scope = registrationScope();
        LuaTable baseDefaults = new LuaTable();
        baseDefaults.set("damage", 8);
        LuaTable combat = new LuaTable();
        combat.set("reach", 3.0);
        baseDefaults.set("combat", combat);

        MLuaContentTypeRegistry.register(scope, "weapon", "alpha", "blade", "default",
                "A reusable blade definition.", baseDefaults);

        LuaTable heavyDefaults = new LuaTable();
        heavyDefaults.set("weight", 2.5);
        MLuaContentTypeRegistry.register(scope, "weapon", "alpha", "heavy", "blade",
                "A heavy blade based on the addon's blade definition.", heavyDefaults);

        baseDefaults.set("damage", 99);
        combat.set("reach", 99.0);
        LuaTable selector = new LuaTable();
        selector.set("RegistryType", "heavy");
        selector.set("damage", 12);

        LuaTable resolved = MLuaContentTypeRegistry.resolve(scope, "weapon", "alpha", selector);
        assertEquals(12, resolved.get("damage").toint(), "definition fields override inherited defaults");
        assertEquals(2.5, resolved.get("weight").todouble(), 0.0001);
        assertEquals(3.0, resolved.get("combat").get("reach").todouble(), 0.0001,
                "registered defaults are snapshots, not live references to addon tables");
        assertTrue(resolved.get("RegistryType").isnil(), "generic content selectors are consumed during resolution");

        resolved.get("combat").set("reach", 42.0);
        LuaTable secondResolution = MLuaContentTypeRegistry.resolve(scope, "weapon", "alpha", selector);
        assertEquals(3.0, secondResolution.get("combat").get("reach").todouble(), 0.0001,
                "mutating one resolved definition must not mutate the registered type");

        assertEquals(Map.of("blade", "A reusable blade definition.",
                "heavy", "A heavy blade based on the addon's blade definition."),
                MLuaContentTypeRegistry.types(scope, "weapon", "alpha"));
        assertTrue(MLuaContentTypeRegistry.types(scope, "weapon", "beta").isEmpty(),
                "each addon only lists its own reusable definitions");

        MLuaContentTypeRegistry.clearOwner(scope, "alpha");
        assertEquals(0, MLuaContentTypeRegistry.usage(scope, "alpha").activeTotal());
        assertThrows(LuaError.class,
                () -> MLuaContentTypeRegistry.resolve(scope, "weapon", "alpha", selector));
        assertFalse(MLuaContentTypeRegistry.types(scope, "weapon", "alpha").containsKey("blade"));
    }

    @Test
    void rejectsDuplicateNamesAndForeignNamespacesWithoutLeakingCapacity() {
        MLuaRegistration scope = registrationScope();
        LuaTable defaults = new LuaTable();

        MLuaContentTypeRegistry.register(scope, "weapon", "alpha", "blade", "default",
                "A reusable blade definition.", defaults);
        assertThrows(LuaError.class, () -> MLuaContentTypeRegistry.register(scope, "weapon", "alpha",
                "alpha:blade", "default", "Duplicate definition.", defaults));
        assertThrows(LuaError.class, () -> MLuaContentTypeRegistry.register(scope, "weapon", "alpha",
                "other:blade", "default", "Foreign definition.", defaults));
        assertEquals(1, MLuaContentTypeRegistry.usage(scope, "alpha").activeTotal());
        MLuaContentTypeRegistry.clearOwner(scope, "alpha");
    }

    private static MLuaRegistration registrationScope() {
        return (MLuaRegistration) Proxy.newProxyInstance(MLuaRegistration.class.getClassLoader(),
                new Class<?>[]{MLuaRegistration.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "test content type registration scope";
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
