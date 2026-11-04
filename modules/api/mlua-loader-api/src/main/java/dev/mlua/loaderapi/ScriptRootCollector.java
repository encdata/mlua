package dev.mlua.loaderapi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;

/** Shared script-root filtering and deduplication for loader implementations. */
public final class ScriptRootCollector {
    private ScriptRootCollector() { }

    /** Adds an existing directory once, using its normalized absolute path as identity. */
    public static void add(Collection<ScriptRootRef> roots, String modId, Path path) {
        Objects.requireNonNull(roots, "roots");
        if (modId == null || modId.isBlank() || path == null || !Files.isDirectory(path)) return;
        Path normalized = path.toAbsolutePath().normalize();
        for (ScriptRootRef root : roots) {
            if (root.path().toAbsolutePath().normalize().equals(normalized)) return;
        }
        roots.add(new ScriptRootRef(modId, normalized));
    }
}
