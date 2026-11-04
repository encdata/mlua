package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.loaderapi.RegistryKind;
import dev.mlua.minecraft.binding.MLuaRegistryDefinition;
import dev.mlua.minecraft.binding.MLuaRegistryEntry;
import dev.mlua.runtime.registry.MLuaDynamicRegistryCatalog;

/** Builds stable Lua entry collections for native and addon-defined registry categories. */
final class MLuaRegistryEntryCatalog {
    private final MLuaRegistryService service;

    MLuaRegistryEntryCatalog(MLuaRegistryService service) {
        this.service = service;
    }

    LuaTable registeredIds(RegistryKind kind, String namespace) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (String id : MLuaRegistryEntryQuery.addonRegisteredIds(kind, namespace)) {
            result.set(index++, LuaValue.valueOf(id));
        }
        return result;
    }

    LuaTable registeredEntries(RegistryKind kind, String namespace) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (String id : MLuaRegistryEntryQuery.registeredIdList(kind, namespace)) {
            result.set(index++, MLuaRegistryEntry.create(this.service.apiContext().mlua().registration(), kind, id));
        }
        return result;
    }

    LuaTable registeredEntryMap(RegistryKind kind, String namespace) {
        LuaTable result = new LuaTable();
        for (String id : MLuaRegistryEntryQuery.registeredIdList(kind, namespace)) {
            result.set(id, MLuaRegistryEntry.create(this.service.apiContext().mlua().registration(), kind, id));
        }
        return result;
    }

    LuaTable dynamicEntryMap(String category, String namespace) {
        LuaTable result = new LuaTable();
        MLuaDynamicRegistryCatalog dynamicRegistry = this.service.dynamicRegistry;
        for (String id : dynamicRegistry.ids(category, namespace)) {
            LuaTable entry = MLuaRegistryDefinition.create(
                this.service.apiContext().mlua().registration(), dynamicRegistry, category, id);
            if (entry != null) result.set(id, entry);
        }
        return result;
    }

    LuaValue nativeEntry(RegistryKind kind, String id) {
        if (!MLuaRegistryEntry.isKnown(kind, id)) return LuaValue.NIL;
        return this.nativeEntryHandle(kind, id);
    }

    LuaTable nativeEntryHandle(RegistryKind kind, String id) {
        return MLuaRegistryEntry.create(this.service.apiContext().mlua().registration(), kind, id);
    }

    LuaValue entry(RegistryKind kind, String owner, String name) {
        return MLuaRegistryEntry.create(
            this.service.apiContext().mlua().registration(), kind, owner + ":" + name);
    }

    LuaValue lookup(RegistryEndpoint endpoint, String id, LuaTable registry) {
        if (endpoint.registryKind() != null) return this.nativeEntry(endpoint.registryKind(), id);
        if (this.service.dynamicRegistry.category(endpoint.table()) != null) {
            LuaTable definition = MLuaRegistryDefinition.create(
                this.service.apiContext().mlua().registration(),
                this.service.dynamicRegistry, endpoint.table(), id);
            return definition == null ? LuaValue.NIL : definition;
        }
        LuaTable target = registry.get(endpoint.table()).checktable();
        LuaValue getter = target.get("GetEntry");
        if (!getter.isfunction()) getter = target.get("Get");
        if (!getter.isfunction()) return LuaValue.NIL;
        return getter.invoke(LuaValue.varargsOf(new LuaValue[]{target, LuaValue.valueOf(id)})).arg1();
    }
}
