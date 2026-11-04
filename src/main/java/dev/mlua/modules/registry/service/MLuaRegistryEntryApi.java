package dev.mlua.modules.registry.service;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.loaderapi.RegistryKind;

/** Installs the stable identifier and typed-entry lookup methods for native registry categories. */
final class MLuaRegistryEntryApi {
    private MLuaRegistryEntryApi() {}

    static void install(LuaTable target, RegistryKind kind, MLuaRegistryService service) {
        target.set("Get", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = service.identifiers.registryId(args.checkjstring(2));
                return service.entries.nativeEntry(kind, id);
            }
        });
        target.set("GetAll", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.entries.registeredEntryMap(kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2)));
            }
        });
        target.set("GetIds", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return MLuaRegistryLuaValues.stringArray(
                    MLuaRegistryEntryQuery.registeredIdList(kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2))));
            }
        });
        target.set("GetRegistered", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.entries.registeredIds(kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2)));
            }
        });
        target.set("GetEntries", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return service.entries.registeredEntries(kind, MLuaRegistryLuaValues.optionalNamespace(args.arg(2)));
            }
        });
        target.set("GetId", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(service.identifiers.registryId(args.checkjstring(2)));
            }
        });
        target.set("GetEntry", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = service.identifiers.registryId(args.checkjstring(2));
                return service.entries.nativeEntry(kind, id);
            }
        });
        target.set("Exists", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable entry = service.entries.nativeEntryHandle(
                    kind, service.identifiers.registryId(args.checkjstring(2)));
                return entry.get("Exists").invoke(entry).arg1();
            }
        });
    }
}
