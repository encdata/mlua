package dev.mlua.modules.javabridge.service;

import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.common.service.MLuaServiceMetadata;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Assembles the Lua-facing Java service from focused API groups. */
final class MLuaJavaApi {
    private MLuaJavaApi() { }

    static LuaTable build(MLuaJavaService service) {
        LuaTable table = new LuaTable();
        table.set("Null", MLuaJavaService.JAVA_NULL);
        MLuaJavaClassApi.install(table, service);
        MLuaJavaProxyApi.install(table, service);
        MLuaJavaReflectionApi.install(table, service);
        MLuaJavaConversionApi.install(table, service);
        MLuaServiceMetadata.attachMethodDescriptions(table, JavaServiceMethods.serviceMethodDescriptions());
        return MLuaServiceBase.normalizeServiceCalls(table, service.name());
    }
}
