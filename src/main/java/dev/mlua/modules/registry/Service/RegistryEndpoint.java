package dev.mlua.modules.registry.Service;

import dev.mlua.loaderapi.RegistryKind;
import java.util.List;

record RegistryEndpoint(String table, String registerMethod, String typeDomain,
                        String typeTable, RegistryKind registryKind, int genericArgumentCount,
                        List<String> typedDefinitions) { }
