package dev.mlua.modules.registry.service;

import dev.mlua.common.service.MLuaServiceMetadata;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Shared Lua table adapters for built-in and addon-defined registry categories. */
final class MLuaRegistryTableSupport {
    private MLuaRegistryTableSupport() { }

    static Varargs invokeCategoryQuery(LuaTable target, String category, String method, LuaValue argument) {
        LuaValue query = target.get(method);
        if (!query.isfunction()) {
            throw new LuaError("[MLua] Registry category '" + category + "' does not support " + method);
        }
        return query.invoke(LuaValue.varargsOf(new LuaValue[]{target, argument}));
    }

    static void describeRegistryMethod(LuaTable target, String method, String description) {
        LuaValue metadata = target.rawget("__mlua_doc_methods");
        if (!metadata.istable() || !target.get(method).isfunction()) return;
        metadata.checktable().rawset(method, LuaValue.valueOf(description));
    }

    static void installEntryLookupApi(LuaTable target, String category) {
        LuaValue get = target.get("Get");
        if (!target.get("GetEntry").isfunction() && get.isfunction()) {
            target.set("GetEntry", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) {
                    String id = args.checkjstring(2);
                    return get.invoke(LuaValue.varargsOf(new LuaValue[]{target, LuaValue.valueOf(id)}));
                }
            });
        }
        LuaValue getIds = target.get("GetIds");
        LuaValue getEntry = target.get("GetEntry");
        if (target.get("GetEntries").isfunction() || !getIds.isfunction() || !getEntry.isfunction()) return;
        target.set("GetEntries", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaValue idsValue = getIds.invoke(
                    LuaValue.varargsOf(new LuaValue[]{target, args.arg(2)})).arg1();
                if (!idsValue.istable()) {
                    throw new LuaError("[MLua] Registry." + category
                        + ":GetIds must return an array of identifiers");
                }
                LuaTable ids = idsValue.checktable();
                LuaTable entries = new LuaTable();
                int entryIndex = 1;
                for (int index = 1; index <= ids.length(); index++) {
                    LuaValue entry = getEntry.invoke(
                        LuaValue.varargsOf(new LuaValue[]{target, ids.get(index)})).arg1();
                    if (!entry.isnil()) entries.set(entryIndex++, entry);
                }
                return entries;
            }
        });
    }

    static void documentRegistryTable(LuaTable target, String category) {
        target.rawset("__mlua_doc_description", LuaValue.valueOf(
            "Registry".equals(category)
                ? "The central registry service for built-in Minecraft content and addon-owned categories."
                : "Registry." + category + " provides the registration and lookup operations available for this content category."));
        MLuaServiceMetadata.attachMethodDescriptions(target, RegistryServiceMethods.methodDescriptions());
    }
}
