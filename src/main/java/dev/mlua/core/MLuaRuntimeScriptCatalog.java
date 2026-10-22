package dev.mlua.core;

import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.script.MLuaScript;
import dev.mlua.core.script.MLuaScriptLoader;
import dev.mlua.core.script.MLuaScriptRoot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owns script roots, resource-pack inputs, and the immutable discovered script snapshot. */
final class MLuaRuntimeScriptCatalog {
    private static final List<String> ENTRYPOINTS = List.of("run_mixin", "run_registry", "run_commands",
            "run_server", "run_client", "run_server_stopping", "run_world_load", "run_world_unload");
    private static final List<Side> SIDES = List.of(Side.SERVER, Side.CLIENT);
    private final MLuaScriptLoader loader = new MLuaScriptLoader();
    private final List<MLuaScriptRoot> roots = new ArrayList<>();
    private volatile List<MLuaScriptRoot> rootsSnapshot = List.of();
    private final List<MLuaScript> resourceScripts = new ArrayList<>();
    private volatile Snapshot snapshot = Snapshot.empty();
    private boolean dirty = true;

    synchronized boolean registerRoot(MLuaScriptRoot root) {
        if (this.roots.contains(root)) return false;
        this.roots.add(root);
        this.rootsSnapshot = List.copyOf(this.roots);
        this.dirty = true;
        return true;
    }

    List<MLuaScriptRoot> roots() {
        return this.rootsSnapshot;
    }

    synchronized ResourceReload replaceResourceScripts(List<MLuaScript> scripts) {
        List<MLuaScript> previous = List.copyOf(this.resourceScripts);
        Set<Path> replacedPaths = new HashSet<>();
        for (MLuaScript script : previous) replacedPaths.add(script.path());
        this.resourceScripts.clear();
        this.resourceScripts.addAll(scripts);
        this.dirty = true;
        return new ResourceReload(replacedPaths,
                nativeRegistryScriptsChanged(previous, this.resourceScripts));
    }

    synchronized List<MLuaScript> resourceScripts() {
        return List.copyOf(this.resourceScripts);
    }

    synchronized List<MLuaScript> refresh(boolean force) {
        if (!force && !this.dirty) return this.snapshot.all();
        List<MLuaScript> discovered = new ArrayList<>(this.loader.discover(List.copyOf(this.roots)));
        discovered.addAll(this.resourceScripts);
        this.snapshot = Snapshot.create(discovered);
        this.dirty = false;
        return this.snapshot.all();
    }

    List<MLuaScript> all() {
        return this.snapshot.all();
    }

    List<MLuaScript> ofType(ScriptType type) {
        return this.snapshot.byType().getOrDefault(type, List.of());
    }

    List<MLuaScript> mixinScripts() {
        return this.snapshot.mixins();
    }

    List<MLuaScript> withEntrypoint(String name, Side side) {
        return this.snapshot.entrypoints().getOrDefault(new Entrypoint(name, side), List.of());
    }

    private static boolean nativeRegistryScriptsChanged(List<MLuaScript> previous, List<MLuaScript> current) {
        return !registryScriptSources(previous).equals(registryScriptSources(current));
    }

    private static Map<Path, String> registryScriptSources(List<MLuaScript> scripts) {
        Map<Path, String> sources = new LinkedHashMap<>();
        for (MLuaScript script : scripts) {
            if (isRegistryPhaseScript(script)) {
                sources.put(script.path().toAbsolutePath().normalize(), script.source());
            }
        }
        return sources;
    }

    static boolean isRegistryPhaseScript(MLuaScript script) {
        return script.type() == ScriptType.REGISTRY
                || script.type().supportsEntrypoint("run_registry", Side.SERVER)
                && ScriptType.declaresEntrypoint(script.source(), "run_registry");
    }

    record ResourceReload(Set<Path> replacedPaths, boolean nativeRegistryScriptsChanged) {
        ResourceReload {
            replacedPaths = Set.copyOf(replacedPaths);
        }
    }

    private record Entrypoint(String name, Side side) { }

    private record Snapshot(List<MLuaScript> all, Map<ScriptType, List<MLuaScript>> byType,
                           Map<Entrypoint, List<MLuaScript>> entrypoints, List<MLuaScript> mixins) {
        static Snapshot empty() {
            return new Snapshot(List.of(), Map.of(), Map.of(), List.of());
        }

        static Snapshot create(List<MLuaScript> scripts) {
            List<MLuaScript> all = List.copyOf(scripts);
            Map<ScriptType, List<MLuaScript>> grouped = new EnumMap<>(ScriptType.class);
            Map<Entrypoint, List<MLuaScript>> entrypoints = new LinkedHashMap<>();
            List<MLuaScript> mixins = new ArrayList<>();
            for (MLuaScript script : all) {
                grouped.computeIfAbsent(script.type(), ignored -> new ArrayList<>()).add(script);
                if (script.type().isMixin()) mixins.add(script);
                for (String name : ENTRYPOINTS) {
                    if (!ScriptType.declaresEntrypoint(script.source(), name)) continue;
                    for (Side side : SIDES) {
                        if (script.type().supportsEntrypoint(name, side)) {
                            entrypoints.computeIfAbsent(new Entrypoint(name, side), ignored -> new ArrayList<>())
                                    .add(script);
                        }
                    }
                }
            }
            Map<ScriptType, List<MLuaScript>> immutable = new EnumMap<>(ScriptType.class);
            grouped.forEach((type, typedScripts) -> immutable.put(type, List.copyOf(typedScripts)));
            Map<Entrypoint, List<MLuaScript>> immutableEntrypoints = new LinkedHashMap<>();
            entrypoints.forEach((key, scriptsForEntrypoint) ->
                    immutableEntrypoints.put(key, List.copyOf(scriptsForEntrypoint)));
            return new Snapshot(all, Map.copyOf(immutable), Map.copyOf(immutableEntrypoints), List.copyOf(mixins));
        }
    }
}
