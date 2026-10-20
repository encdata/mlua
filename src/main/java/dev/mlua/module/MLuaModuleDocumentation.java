package dev.mlua.module;

import dev.mlua.core.MLuaLogger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** A single, validated snapshot of a module's contributed API documentation. */
final class MLuaModuleDocumentation {
    private final MLuaDocBuilder contribution;

    private MLuaModuleDocumentation(MLuaDocBuilder contribution) {
        this.contribution = contribution;
    }

    static MLuaModuleDocumentation collect(MLuaModule module) {
        MLuaDocBuilder contribution = new MLuaDocBuilder();
        try {
            module.contributeDocs(contribution);
        } catch (Throwable error) {
            MLuaLogger.LOGGER.warn("MLua module '{}' failed while contributing API descriptions", module.id(), error);
        }
        return new MLuaModuleDocumentation(contribution);
    }

    Map<String, String> descriptions(MLuaDocBuilder.DocEntryType type) {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (MLuaDocBuilder.DocEntry entry : this.contribution.entries()) {
            if (entry.type() != type || entry.desc() == null || entry.desc().isBlank()) continue;
            putConsistentDescription(descriptions, entry.name(), entry.desc(), type + " '" + entry.name() + "'");
        }
        return Collections.unmodifiableMap(descriptions);
    }

    Map<String, Map<String, String>> methodsByReceiver() {
        Map<String, Map<String, Map<String, String>>> overloads = new LinkedHashMap<>();
        for (MLuaDocBuilder.MethodDoc method : this.contribution.methods()) {
            String name = methodName(method.signature());
            if (name == null || method.description() == null || method.description().isBlank()) continue;
            Map<String, String> signatures = overloads
                .computeIfAbsent(method.receiver(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(name, ignored -> new LinkedHashMap<>());
            putConsistentDescription(signatures, method.signature().trim(), method.description(),
                "overload '" + method.signature() + "'");
        }

        Map<String, Map<String, String>> methods = new LinkedHashMap<>();
        overloads.forEach((receiver, receiverOverloads) -> {
            Map<String, String> descriptions = new LinkedHashMap<>();
            receiverOverloads.forEach((name, signatures) -> {
                String description = signatures.size() == 1
                    ? signatures.values().iterator().next()
                    : signatures.entrySet().stream()
                        .map(entry -> entry.getKey() + " — " + entry.getValue())
                        .collect(Collectors.joining("; "));
                descriptions.put(name, description);
            });
            methods.put(receiver, Collections.unmodifiableMap(descriptions));
        });
        return Collections.unmodifiableMap(methods);
    }

    private static void putConsistentDescription(Map<String, String> descriptions, String key,
                                                  String description, String displayName) {
        String existing = descriptions.putIfAbsent(key, description);
        if (existing != null && !existing.equals(description)) {
            throw new IllegalStateException("Conflicting descriptions registered for " + displayName);
        }
    }

    private static String methodName(String signature) {
        if (signature == null) return null;
        int separator = Math.max(signature.lastIndexOf(':'), signature.lastIndexOf('.'));
        int start = separator + 1;
        int end = signature.indexOf('(', start);
        if (start >= signature.length() || end <= start) return null;
        return signature.substring(start, end).trim();
    }
}
