package dev.mlua.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaRegistryScriptLifecycleTest {
    @Test
    void failedScriptRollsBackItsLuaRegistrationAndMixinScope() {
        MLuaRuntime owner = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRegistryScriptLifecycle lifecycle = new MLuaRegistryScriptLifecycle(owner);
        Path source = Path.of("scripts", "broken.registry.lua");
        AtomicReference<Path> cleanedScope = new AtomicReference<>();

        assertThrows(IllegalStateException.class, () -> lifecycle.execute(source, cleanedScope::set, registration -> {
            registration.registerClass("FailedRegistryClass", null, "Temporary test class.");
            throw new IllegalStateException("intentional registration failure");
        }));

        assertEquals(source.toAbsolutePath().normalize(), cleanedScope.get());
        assertNull(owner.classTable("FailedRegistryClass"));
    }

    @Test
    void clearPathsNormalizesSourcesAndRollsBackOnlyMatchingScripts() {
        MLuaRuntime owner = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRegistryScriptLifecycle lifecycle = new MLuaRegistryScriptLifecycle(owner);
        Path keptSource = Path.of("scripts", "kept.registry.lua");
        Path removedSource = Path.of("scripts", "removed.registry.lua");
        lifecycle.execute(keptSource, ignored -> { }, registration ->
            registration.registerClass("KeptRegistryClass", null, "Kept test class."));
        lifecycle.execute(removedSource, ignored -> { }, registration ->
            registration.registerClass("RemovedRegistryClass", null, "Removed test class."));

        lifecycle.clearPaths(Set.of(Path.of("scripts", "nested", "..", "removed.registry.lua")), ignored -> { });

        assertNull(owner.classTable("RemovedRegistryClass"));
        assertEquals("KeptRegistryClass", owner.classTable("KeptRegistryClass").get("ClassName").tojstring());
        lifecycle.clearAll(ignored -> { });
        assertNull(owner.classTable("KeptRegistryClass"));
    }

    @Test
    void replacingOneSourceRollsBackItsPriorRegistrationBeforeInstallingTheNext() {
        MLuaRuntime owner = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRegistryScriptLifecycle lifecycle = new MLuaRegistryScriptLifecycle(owner);
        Path source = Path.of("scripts", "replace.registry.lua");
        List<Path> cleaned = new ArrayList<>();
        lifecycle.execute(source, cleaned::add, registration ->
            registration.registerClass("OldRegistryClass", null, "Old test class."));

        lifecycle.execute(Path.of("scripts", "nested", "..", "replace.registry.lua"), cleaned::add,
            registration -> registration.registerClass("NewRegistryClass", null, "New test class."));

        assertEquals(List.of(source.toAbsolutePath().normalize()), cleaned);
        assertNull(owner.classTable("OldRegistryClass"));
        assertEquals("NewRegistryClass", owner.classTable("NewRegistryClass").get("ClassName").tojstring());
        lifecycle.clearAll(ignored -> { });
    }

    @Test
    void clearAllCleansMixinScopesInReverseRegistrationOrder() {
        MLuaRuntime owner = new MLuaRuntime(new MLuaRuntimeSession());
        MLuaRegistryScriptLifecycle lifecycle = new MLuaRegistryScriptLifecycle(owner);
        Path first = Path.of("scripts", "first.registry.lua");
        Path second = Path.of("scripts", "second.registry.lua");
        lifecycle.execute(first, ignored -> { }, registration -> { });
        lifecycle.execute(second, ignored -> { }, registration -> { });
        List<Path> cleaned = new ArrayList<>();

        lifecycle.clearAll(cleaned::add);

        assertEquals(List.of(second.toAbsolutePath().normalize(), first.toAbsolutePath().normalize()), cleaned);
    }
}
