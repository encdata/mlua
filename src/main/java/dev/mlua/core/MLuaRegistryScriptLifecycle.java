package dev.mlua.core;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Owns registry-script registrations and their transactional rollback lifecycle. */
final class MLuaRegistryScriptLifecycle {
    private final MLuaRegistration owner;
    private final Map<Path, MLuaScriptRegistration> registrations = new LinkedHashMap<>();

    MLuaRegistryScriptLifecycle(MLuaRegistration owner) {
        this.owner = java.util.Objects.requireNonNull(owner, "owner");
    }

    void execute(Path source, Consumer<Path> unregisterMixinScope, RegistrationWork work) {
        Path key = normalize(source);
        MLuaScriptRegistration previous = this.registrations.remove(key);
        if (previous != null) {
            Throwable failure = MLuaRollback.run(null, () -> unregisterMixinScope.accept(key));
            failure = MLuaRollback.run(failure, previous::rollback);
            MLuaRollback.rethrow(failure);
        }

        MLuaScriptRegistration registration = new MLuaScriptRegistration(this.owner);
        try (MLuaLoaderRegistry.RegistrationTransaction nativeRegistrations =
                 MLuaLoaderRegistry.beginRegistrationTransaction()) {
            work.execute(registration);
            nativeRegistrations.commit();
            this.registrations.put(key, registration);
        } catch (RuntimeException | Error failure) {
            MLuaRollback.run(failure, () -> unregisterMixinScope.accept(key));
            MLuaRollback.run(failure, registration::rollback);
            throw failure;
        }
    }

    void clearAll(Consumer<Path> unregisterMixinScope) {
        List<Map.Entry<Path, MLuaScriptRegistration>> entries =
            new ArrayList<>(this.registrations.entrySet());
        Throwable failure = null;
        for (int index = entries.size() - 1; index >= 0; index--) {
            Map.Entry<Path, MLuaScriptRegistration> entry = entries.get(index);
            failure = MLuaRollback.run(failure, () -> unregisterMixinScope.accept(entry.getKey()));
            failure = MLuaRollback.run(failure, entry.getValue()::rollback);
        }
        this.registrations.clear();
        MLuaRollback.rethrow(failure);
    }

    void clearPaths(Set<Path> sources, Consumer<Path> unregisterMixinScope) {
        Set<Path> keys = new HashSet<>();
        for (Path source : sources) keys.add(normalize(source));
        List<Path> paths = new ArrayList<>(this.registrations.keySet());
        Throwable failure = null;
        for (int index = paths.size() - 1; index >= 0; index--) {
            Path path = paths.get(index);
            if (!keys.contains(path)) continue;
            MLuaScriptRegistration registration = this.registrations.remove(path);
            failure = MLuaRollback.run(failure, () -> unregisterMixinScope.accept(path));
            if (registration != null) failure = MLuaRollback.run(failure, registration::rollback);
        }
        MLuaRollback.rethrow(failure);
    }

    private static Path normalize(Path source) {
        return java.util.Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
    }

    @FunctionalInterface
    interface RegistrationWork {
        void execute(MLuaScriptRegistration registration);
    }
}
