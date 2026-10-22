package dev.mlua.core;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRuntimeScriptCatalogTest {
    @TempDir
    Path temp;

    @Test
    void indexesImmutableRefreshSnapshotsByTypeAndSupportedEntrypoint() throws IOException {
        Path root = this.temp.resolve("alpha-scripts");
        Files.createDirectories(root);
        Path serverPath = root.resolve("main.server.lua");
        Path clientPath = root.resolve("main.client.lua");
        Path entrypointPath = root.resolve("hooks.lua");
        Files.writeString(serverPath, "print('server')");
        Files.writeString(clientPath, "print('client')");
        Files.writeString(entrypointPath, "function run_server() end\nfunction run_client() end\n");

        MLuaRuntimeScriptCatalog catalog = new MLuaRuntimeScriptCatalog();
        var scriptRoot = new MLuaScriptRoot("alpha", root);
        assertTrue(catalog.registerRoot(scriptRoot));
        assertFalse(catalog.registerRoot(scriptRoot));

        List<dev.mlua.core.script.MLuaScript> firstSnapshot = catalog.refresh(false);
        assertEquals(3, firstSnapshot.size());
        assertEquals(1, catalog.ofType(ScriptType.SERVER).size());
        assertEquals(1, catalog.ofType(ScriptType.CLIENT).size());
        assertEquals(List.of("alpha:hooks.lua", "alpha:main.server.lua"),
                catalog.withEntrypoint("run_server", Side.SERVER).stream()
                        .map(MLuaScript::displayName).toList());
        assertEquals(List.of("alpha:hooks.lua", "alpha:main.client.lua"),
                catalog.withEntrypoint("run_client", Side.CLIENT).stream()
                        .map(MLuaScript::displayName).toList());

        Files.writeString(serverPath, "print('updated')");
        assertEquals("print('server')", catalog.refresh(false).stream()
                .filter(script -> script.path().equals(serverPath)).findFirst().orElseThrow().source());
        assertEquals("print('updated')", catalog.refresh(true).stream()
                .filter(script -> script.path().equals(serverPath)).findFirst().orElseThrow().source());
    }
}
