package dev.mlua.module;

import dev.mlua.core.MLuaLogger;
import dev.mlua.core.service.MLuaServiceRegistry;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** Owns service declarations, installation, and unload cleanup for modules. */
final class MLuaModuleServiceRegistrations {
    private final Map<MLuaServiceRegistry, Map<MLuaModule, List<MLuaServiceRegistry.Registration>>> installed =
        new WeakHashMap<>();
    private final Map<MLuaModule, List<MLuaModule.ServiceEntry>> entriesByModule = new IdentityHashMap<>();

    synchronized boolean registerServices(MLuaModule module, MLuaServiceRegistry registry) {
        List<MLuaServiceRegistry.Registration> batch = new ArrayList<>();
        try {
            List<MLuaModule.ServiceEntry> entries = serviceEntries(module);
            List<MLuaServiceRegistry.Registration> declared = serviceRegistrations(module, entries);
            registry.registerBatch(module.side(), declared);
            batch.addAll(declared);
            rememberServices(registry, module, batch);
            for (MLuaModule.ServiceEntry entry : entries) {
                MLuaLogger.LOGGER.debug("  Registered service '{}' from module '{}'", entry.name(), module.id());
            }
            return true;
        } catch (Throwable error) {
            try {
                registry.unregisterBatch(module.side(), batch);
            } catch (Throwable rollbackFailure) {
                error.addSuppressed(rollbackFailure);
            }
            forgetServiceEntries(module);
            MLuaLogger.LOGGER.error("MLua module '{}' failed during services and was isolated", module.id(), error);
            return false;
        }
    }

    List<MLuaServiceRegistry.Registration> serviceRegistrations(MLuaModule module) {
        return serviceRegistrations(module, serviceEntries(module));
    }

    synchronized List<MLuaModule.ServiceEntry> serviceEntries(MLuaModule module) {
        return this.entriesByModule.computeIfAbsent(module,
            key -> List.copyOf(Objects.requireNonNull(key.services(), "module services")));
    }

    private static List<MLuaServiceRegistry.Registration> serviceRegistrations(
            MLuaModule module, List<MLuaModule.ServiceEntry> entries) {
        Map<String, String> documentedServices = MLuaModuleRegistrations.declaredDescriptions(
            module, MLuaDocBuilder.DocEntryType.SERVICE);
        Map<String, Map<String, String>> documentedMethods = MLuaModuleRegistrations.declaredMethods(module);
        return entries.stream().map(entry -> {
            String description = MLuaModuleRegistrations.mergeServiceDescription(
                entry.name(), documentedServices.get(entry.name()), entry.description());
            Map<String, String> methods = MLuaModuleRegistrations.mergeFunctionDescriptions(
                entry.name(), documentedMethods.getOrDefault(entry.name(), Map.of()), entry.functionDescriptions());
            return new MLuaServiceRegistry.Registration(entry.name(), entry.className(), entry.factory(),
                description, methods);
        }).toList();
    }

    synchronized void rememberServices(MLuaServiceRegistry registry, MLuaModule module,
            List<MLuaServiceRegistry.Registration> registrations) {
        this.installed.computeIfAbsent(registry, ignored -> new IdentityHashMap<>())
            .put(module, List.copyOf(registrations));
    }

    synchronized void unregisterServices(MLuaModule module) {
        for (MLuaServiceRegistry registry : List.copyOf(this.installed.keySet())) {
            Map<MLuaModule, List<MLuaServiceRegistry.Registration>> byModule = this.installed.get(registry);
            List<MLuaServiceRegistry.Registration> registrations = byModule != null ? byModule.remove(module) : null;
            if (registrations != null) registry.unregisterBatch(module.side(), registrations);
            if (byModule != null && byModule.isEmpty()) this.installed.remove(registry);
        }
        forgetServiceEntries(module);
    }

    synchronized void forgetServiceEntries(MLuaModule module) {
        this.entriesByModule.remove(module);
    }
}
