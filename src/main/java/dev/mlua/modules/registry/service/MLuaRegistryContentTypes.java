package dev.mlua.modules.registry.service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.common.service.MLuaServiceMetadata;
import dev.mlua.minecraft.binding.MLuaRegistryProperties;
import dev.mlua.runtime.registry.MLuaBlockFactory;
import dev.mlua.runtime.registry.MLuaItemFactory;

/** Registers reusable content schemas and applies them to item and block definitions. */
final class MLuaRegistryContentTypes {
    private final MLuaRegistryService service;

    MLuaRegistryContentTypes(MLuaRegistryService service) {
        this.service = service;
    }

    void installRootApi(LuaTable target) {
        target.set("Register", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaRegistryContentTypes.this.service.ensureRegistryPhase();
                MLuaRegistryContentTypes.this.register(args.checkjstring(2), args.checkjstring(3), args.checktable(4));
                return LuaValue.NONE;
            }
        });
        target.set("GetTypes", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String domain = args.checkjstring(2);
                return MLuaRegistryService.typeCatalog(MLuaRegistryContentTypes.this.service.apiContext()
                    .mlua().registration().contentTypes(domain,
                        MLuaRegistryContentTypes.this.service.apiContext().mlua().ownerModId()));
            }
        });
        target.set("GetLimits", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                var registration = MLuaRegistryContentTypes.this.service.apiContext().mlua().registration();
                var current = dev.mlua.runtime.registry.MLuaContentTypeRegistry.limits(
                    registration, args.checkjstring(2));
                var usage = dev.mlua.runtime.registry.MLuaContentTypeRegistry.usage(registration,
                    MLuaRegistryContentTypes.this.service.apiContext().mlua().ownerModId());
                LuaTable limits = new LuaTable();
                limits.set("ActiveInDomain", current.activeInDomain());
                limits.set("ActiveTotal", current.activeTotal());
                limits.set("MaxPerDomain", dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_TYPES_PER_DOMAIN);
                limits.set("MaxTotal", dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_TYPES_PER_SCOPE);
                limits.set("MaxEntriesPerType", dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_DEFAULT_ENTRIES_PER_TYPE);
                limits.set("ActiveDefaultTableEntries", usage.defaultTableEntries());
                limits.set("AddonDefaultTableEntries", usage.defaultTableEntriesForOwner());
                limits.set("MaxDefaultTableEntriesPerAddon",
                    dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_DEFAULT_ENTRIES_PER_OWNER);
                limits.set("MaxDefaultTableEntriesTotal",
                    dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_TOTAL_DEFAULT_ENTRIES);
                limits.set("MaxNestingDepth", dev.mlua.runtime.registry.MLuaContentTypeRegistry.MAX_TABLE_DEPTH);
                return limits;
            }
        });
        target.set("Resolve", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                var context = MLuaRegistryContentTypes.this.service.apiContext().mlua();
                return context.registration().resolveContentType(
                    args.checkjstring(2), context.ownerModId(), args.checktable(3));
            }
        });
        Map<String, String> descriptions = new java.util.LinkedHashMap<>(RegistryServiceMethods.methodDescriptions());
        descriptions.put("Register", "Registers a reusable content schema for an addon-owned domain.");
        descriptions.put("GetTypes", "Lists the content schemas visible to this addon in a domain.");
        descriptions.put("GetLimits", "Returns active schema counts and registration limits for a domain.");
        descriptions.put("Resolve", "Applies a registered schema to a definition and returns its resolved values.");
        MLuaServiceMetadata.attachMethodDescriptions(target, descriptions);
    }

    void install(LuaTable target, String domain, List<RegistryEndpoint.TypedDefinition> definitions) {
        target.set("RegisterType", contentTypeRegistrar(domain));
        target.set("ResolveType", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable definition = args.checktable(2);
                return MLuaRegistryContentTypes.this.service.apiContext().mlua().registration().resolveContentType(domain,
                    MLuaRegistryContentTypes.this.service.apiContext().mlua().ownerModId(), definition);
            }
        });
        target.set("GetTypes", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Map<String, String> types = new TreeMap<>();
                if ("item".equals(domain)) types.putAll(MLuaItemFactory.registeredTypes());
                if ("block".equals(domain)) types.putAll(MLuaBlockFactory.registeredTypes());
                types.putAll(MLuaRegistryContentTypes.this.service.apiContext().mlua().registration().contentTypes(
                    domain, MLuaRegistryContentTypes.this.service.apiContext().mlua().ownerModId()));
                return MLuaRegistryService.typeCatalog(types);
            }
        });
        wrapTypedDefinition(target, domain, definitions);
    }

    void register(String domain, String name, LuaTable definition) {
        String base = MLuaRegistryDefinitions.stringProperty(definition, "base", "Base");
        String description = MLuaRegistryDefinitions.stringProperty(definition, "description", "Description");
        LuaValue defaultsValue = definition.get("defaults");
        if (defaultsValue.isnil()) defaultsValue = definition.get("Defaults");
        LuaTable defaults = MLuaRegistryProperties.optionalTable(
            this.service.apiContext().mlua().registration(), defaultsValue, domain, "Registry content type Defaults");
        this.service.apiContext().mlua().registration().registerContentType(domain,
            this.service.apiContext().mlua().ownerModId(), name, base, description, defaults);
    }

    private VarArgFunction contentTypeRegistrar(String domain) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                MLuaRegistryContentTypes.this.service.ensureRegistryPhase();
                String name = args.checkjstring(2);
                LuaTable definition = args.checktable(3);
                MLuaRegistryContentTypes.this.register(domain, name, definition);
                return LuaValue.NONE;
            }
        };
    }

    void wrapTypedDefinition(LuaTable target, String domain,
                             List<RegistryEndpoint.TypedDefinition> definitions) {
        for (RegistryEndpoint.TypedDefinition definitionSpec : definitions) {
            String methodName = definitionSpec.method();
            int definitionIndex = definitionSpec.argumentIndex();
            LuaValue original = target.get(methodName);
            if (!original.isfunction()) continue;
            target.set(methodName, new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    LuaValue definition = args.arg(definitionIndex);
                    if (!definition.istable()) return original.invoke(args);
                    var context = MLuaRegistryContentTypes.this.service.apiContext().mlua();
                    LuaTable resolved = context.registration().resolveContentType(
                        domain, context.ownerModId(), definition.checktable());
                    LuaValue[] forwarded = new LuaValue[args.narg()];
                    for (int index = 1; index <= args.narg(); index++) forwarded[index - 1] = args.arg(index);
                    forwarded[definitionIndex - 1] = resolved;
                    return original.invoke(LuaValue.varargsOf(forwarded));
                }
            });
        }
    }
}
