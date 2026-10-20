package dev.mlua.runtime.registry;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.service.MLuaServiceRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaDynamicRegistryCatalogTest {
    @TempDir
    Path temp;

    @Test
    void definitionsAndCallbackArgumentsAreDetachedSnapshots() {
        MLuaDynamicRegistryCatalog catalog = new MLuaDynamicRegistryCatalog();
        MLuaDynamicRegistryCatalog.Category category = category("alpha", "alpha:weapons");
        assertTrue(catalog.register(category));

        LuaTable nested = new LuaTable();
        nested.set("damage", 7);
        LuaTable source = new LuaTable();
        source.set("settings", nested);
        var reservation = catalog.beginDefinition("alpha:weapons", "alpha:blade", "alpha", source);
        assertNotSame(source, catalog.callbackDefinition(reservation));

        LuaTable callbackArgument = catalog.callbackDefinition(reservation);
        callbackArgument.get("settings").set("damage", 99);
        source.get("settings").set("damage", 42);
        var stored = catalog.registerDefinition(reservation);
        assertTrue(stored != null);

        LuaTable definition = catalog.definition("alpha:weapons", "alpha:blade");
        assertEquals(7, definition.get("settings").get("damage").toint());
        definition.get("settings").set("damage", 123);
        assertEquals(7, catalog.definition("alpha:weapons", "alpha:blade")
            .get("settings").get("damage").toint());
        assertEquals(new MLuaDynamicRegistryCatalog.Usage(1, 1, 1, 1, 0, 0, 2, 2), catalog.usage("alpha"));
    }

    @Test
    void failedRegistrationReservationCanBeCancelledAndAddonTeardownInvalidatesHandles() {
        MLuaDynamicRegistryCatalog catalog = new MLuaDynamicRegistryCatalog();
        MLuaDynamicRegistryCatalog.Category category = category("alpha", "alpha:weapons");
        assertTrue(catalog.register(category));

        LuaTable source = new LuaTable();
        source.set("damage", 4);
        var reservation = catalog.beginDefinition("alpha:weapons", "alpha:blade", "alpha", source);
        assertTrue(catalog.callbackDefinition(reservation) != null);
        assertNull(catalog.beginDefinition("alpha:weapons", "alpha:blade", "alpha", source),
            "pending ids must be reserved while the category callback is running");
        catalog.cancelPendingDefinition(reservation);
        assertEquals(0, catalog.usage("alpha").pendingDefinitions());
        assertEquals(0, catalog.usage("alpha").definitionTableEntries());

        var completed = catalog.beginDefinition("alpha:weapons", "alpha:blade", "alpha", source);
        assertTrue(catalog.registerDefinition(completed) != null);
        var reference = catalog.definitionReference("alpha:weapons", "alpha:blade");
        assertTrue(catalog.containsDefinition(reference));
        catalog.unregisterOwner("alpha");
        assertFalse(catalog.containsDefinition(reference));
        assertNull(catalog.category("alpha:weapons"));
        assertEquals(new MLuaDynamicRegistryCatalog.Usage(0, 0, 0, 0, 0, 0, 0, 0), catalog.usage("alpha"));
    }

    @Test
    void catalogRejectsUnqualifiedAndForeignNamespaceIdsBeforeReservingThem() {
        MLuaDynamicRegistryCatalog catalog = new MLuaDynamicRegistryCatalog();
        assertTrue(catalog.register(category("alpha", "alpha:weapons")));
        LuaTable definition = new LuaTable();
        definition.set("damage", 4);

        assertThrows(LuaError.class,
            () -> catalog.beginDefinition("alpha:weapons", "blade", "alpha", definition));
        assertThrows(LuaError.class,
            () -> catalog.beginDefinition("alpha:weapons", "other:blade", "alpha", definition));
        assertEquals(0, catalog.usage("alpha").pendingDefinitions());
    }

    private MLuaDynamicRegistryCatalog.Category category(String owner, String name) {
        MLuaScript script = new MLuaScript(owner, ScriptType.REGISTRY, this.temp,
            this.temp.resolve("registry.registry.lua"), "registry", "");
        MLuaContext context = new MLuaContext(script, Side.SERVER, RegistrationPhase.REGISTRY,
            new MLuaServiceRegistry(), new MLuaTaskScheduler(), new MLuaModuleCache(), null,
            this.temp.resolve("data"));
        return new MLuaDynamicRegistryCatalog.Category(name, "Test dynamic registry", owner + ":content",
            owner, new ZeroArgFunction() {
                @Override public LuaValue call() { return LuaValue.NIL; }
            }, context);
    }
}
