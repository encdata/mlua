package dev.mlua.modules.registry.Service;

import dev.mlua.loaderapi.RegistryKind;
import java.util.List;
import java.util.Objects;

record RegistryEndpoint(String table, String registerMethod, String typeDomain,
                        String typeTable, RegistryKind registryKind, int genericArgumentCount,
                        List<TypedDefinition> typedDefinitions) {
    RegistryEndpoint {
        requireText(table, "table");
        requireText(registerMethod, "register method");
        requireText(typeDomain, "type domain");
        requireText(typeTable, "type table");
        if (genericArgumentCount < 1) throw new IllegalArgumentException("genericArgumentCount must be positive");
        typedDefinitions = List.copyOf(Objects.requireNonNull(typedDefinitions, "typedDefinitions"));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be non-empty");
    }

    record TypedDefinition(String method, int argumentIndex) {
        TypedDefinition {
            requireText(method, "typed registration method");
            if (argumentIndex < 2) throw new IllegalArgumentException("typed definition argument index must include self and be at least 2");
        }
    }
}
