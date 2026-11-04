package dev.mlua.modules.coregui.service;

import org.luaj.vm2.LuaTable;
import dev.mlua.core.service.MLuaServiceBase;

/** Assembles the domain-specific Gui API installers into one service table. */
final class MLuaGuiApi {
    private MLuaGuiApi() { }

    static LuaTable build(MLuaGuiService service) {
        LuaTable table = new LuaTable();
        MLuaGuiHudApi.install(service, table);
        MLuaGuiScreenApi.install(service, table);
        MLuaGuiNativeControlApi.install(service, table);
        return MLuaServiceBase.normalizeServiceCalls(table, service.name());
    }
}
