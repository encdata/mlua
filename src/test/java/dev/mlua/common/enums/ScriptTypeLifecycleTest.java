package dev.mlua.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ScriptTypeLifecycleTest {
    @Test
    void registrySuffixesAndGenericRegistryEntrypointsResolveToTheStartupPhase() {
        assertEquals(ScriptType.REGISTRY, ScriptType.classify("content.registry.lua", ""));
        assertEquals(ScriptType.REGISTRY, ScriptType.classify("legacy.register.lua", ""));
        assertEquals(ScriptType.ENTRYPOINT,
            ScriptType.classify("content.lua", "function run_registry() end"));
        assertEquals(ScriptType.ENTRYPOINT,
            ScriptType.classify("content.lua", "run_registry = function() end"));
        assertFalse(ScriptType.declaresEntrypoint(
            "-- function run_registry() end\nlocal text = \"run_registry = function() end\"", "run_registry"));
        assertFalse(ScriptType.declaresEntrypoint("local function run_registry() end", "run_registry"));
    }

    @Test
    void registryEntrypointCannotRunAsAnOrdinaryServerOrClientEntrypoint() {
        assertTrue(ScriptType.REGISTRY.supportsEntrypoint("run_registry", Side.SERVER));
        assertFalse(ScriptType.REGISTRY.supportsEntrypoint("run_registry", Side.CLIENT));
        assertFalse(ScriptType.SERVER.supportsEntrypoint("run_registry", Side.SERVER));
        assertFalse(ScriptType.CLIENT.supportsEntrypoint("run_registry", Side.CLIENT));
        assertTrue(ScriptType.MODULE.supportsEntrypoint("run_registry", Side.SERVER));
        assertFalse(ScriptType.MODULE.supportsEntrypoint("run_registry", Side.CLIENT));
    }

    @Test
    void commandFilesCanDeclareCommandEntrypointsOnBothLogicalSides() {
        String source = "function run_commands(ctx) end";
        assertEquals(ScriptType.COMMANDS, ScriptType.classify("tools.commands.lua", source));
        assertTrue(ScriptType.COMMANDS.supportsEntrypoint("run_commands", Side.SERVER));
        assertTrue(ScriptType.COMMANDS.supportsEntrypoint("run_commands", Side.CLIENT));
        assertTrue(ScriptType.declaresEntrypoint(source, "run_commands"));
    }
}
