package dev.mlua.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves module dependencies in input order, with dependencies emitted first. */
final class MLuaModuleOrder {
    private MLuaModuleOrder() {
    }

    static Result resolve(List<MLuaModule> input) {
        Map<String, MLuaModule> byId = new LinkedHashMap<>();
        for (MLuaModule module : input) {
            if (byId.putIfAbsent(module.id(), module) != null) {
                throw new IllegalArgumentException("Duplicate MLua module id: " + module.id());
            }
        }

        Map<String, List<String>> dependencies = new LinkedHashMap<>();
        Map<String, Failure> failures = new LinkedHashMap<>();
        for (MLuaModule module : input) {
            try {
                dependencies.put(module.id(), List.copyOf(module.dependencies()));
            } catch (Throwable error) {
                failures.put(module.id(), new Failure("invalid dependency declaration", error));
            }
        }
        for (MLuaModule module : input) {
            for (String dependency : dependencies.getOrDefault(module.id(), List.of())) {
                if (!byId.containsKey(dependency)) {
                    failures.putIfAbsent(module.id(), new Failure("requires missing module '" + dependency + "'", null));
                }
            }
        }

        boolean changed;
        do {
            changed = false;
            for (MLuaModule module : input) {
                if (failures.containsKey(module.id())) continue;
                for (String dependency : dependencies.getOrDefault(module.id(), List.of())) {
                    if (failures.containsKey(dependency)) {
                        failures.put(module.id(), new Failure("requires unavailable module '" + dependency + "'", null));
                        changed = true;
                        break;
                    }
                }
            }
        } while (changed);

        List<MLuaModule> ordered = new ArrayList<>();
        Set<String> emitted = new HashSet<>();
        do {
            changed = false;
            for (MLuaModule module : input) {
                if (failures.containsKey(module.id()) || emitted.contains(module.id())) continue;
                if (emitted.containsAll(dependencies.getOrDefault(module.id(), List.of()))) {
                    emitted.add(module.id());
                    ordered.add(module);
                    changed = true;
                }
            }
        } while (changed);
        for (MLuaModule module : input) {
            if (!failures.containsKey(module.id()) && !emitted.contains(module.id())) {
                failures.put(module.id(), new Failure("has a circular or unsatisfied dependency chain", null));
            }
        }
        return new Result(List.copyOf(ordered), Collections.unmodifiableMap(new LinkedHashMap<>(failures)));
    }

    static List<MLuaModule> requireOrder(List<MLuaModule> input) {
        Result result = resolve(input);
        for (MLuaModule module : input) {
            Failure failure = result.failures().get(module.id());
            if (failure != null) {
                throw new IllegalStateException("MLua module '" + module.id() + "' " + failure.message(), failure.cause());
            }
        }
        return result.ordered();
    }

    record Result(List<MLuaModule> ordered, Map<String, Failure> failures) {
    }

    record Failure(String message, Throwable cause) {
    }
}
