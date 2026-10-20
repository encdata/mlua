package dev.mlua.core.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaDescribedServiceTest {
    @Test
    void functionDescriptionsMustCoverRawServiceFunctions() {
        MLuaService service = serviceWithFunctions("GetStatus", "SetStatus");
        MLuaDescribedService described = new MLuaDescribedService(service, "Status service",
            Map.of("GetStatus", "Returns the current status."));

        IllegalStateException failure = assertThrows(IllegalStateException.class, described::toLua);
        assertTrue(failure.getMessage().contains("SetStatus"));
    }

    @Test
    void registeredFunctionDescriptionsAreAddedToLiveServiceMetadata() {
        MLuaService service = serviceWithFunctions("GetStatus");
        MLuaDescribedService described = new MLuaDescribedService(service, "Status service",
            Map.of("GetStatus", "Returns the current status."));

        LuaTable table = described.toLua();
        assertEquals("Status service", table.get("__mlua_doc_description").tojstring());
        assertEquals("Returns the current status.",
            table.get("__mlua_doc_methods").get("GetStatus").tojstring());
    }

    @Test
    void classFunctionsAndDescriptionsAreReadThroughTableIndex() {
        LuaTable classTable = new LuaTable();
        classTable.set("GetStatus", function());
        LuaTable classDocs = new LuaTable();
        classDocs.set("GetStatus", "Returns the current status.");
        classTable.set("__mlua_doc_methods", classDocs);

        LuaTable instance = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", classTable);
        instance.setmetatable(metatable);
        MLuaService service = new MLuaService() {
            @Override public String name() { return "TestService"; }
            @Override public LuaTable toLua() { return instance; }
        };

        LuaTable table = new MLuaDescribedService(service, "Status service").toLua();
        assertEquals("Returns the current status.",
            table.get("__mlua_doc_methods").get("GetStatus").tojstring());
    }

    @Test
    void undocumentedClassFunctionsAreRejected() {
        LuaTable classTable = new LuaTable();
        classTable.set("GetStatus", function());
        LuaTable instance = new LuaTable();
        LuaTable metatable = new LuaTable();
        metatable.set("__index", classTable);
        instance.setmetatable(metatable);
        MLuaService service = new MLuaService() {
            @Override public String name() { return "TestService"; }
            @Override public LuaTable toLua() { return instance; }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> new MLuaDescribedService(service, "Status service").toLua());
        assertTrue(failure.getMessage().contains("GetStatus"));
    }

    @Test
    void blankServiceDescriptionMetadataIsRejected() {
        LuaTable table = new LuaTable();
        table.set("__mlua_doc_description", "  ");
        MLuaService service = serviceWithTable(table);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> new MLuaDescribedService(service, null, Map.of(), true).toLua());
        assertTrue(failure.getMessage().contains("empty description"));
    }

    @Test
    void malformedFunctionDescriptionMetadataIsRejected() {
        LuaTable table = new LuaTable();
        LuaTable docs = new LuaTable();
        docs.set("GetStatus", LuaValue.valueOf("  "));
        table.set("__mlua_doc_methods", docs);
        MLuaService service = serviceWithTable(table);

        assertThrows(IllegalArgumentException.class,
            () -> new MLuaDescribedService(service, "Status service", Map.of()).toLua());
    }

    private static MLuaService serviceWithFunctions(String... names) {
        LuaTable table = new LuaTable();
        for (String name : names) table.set(name, function());
        return serviceWithTable(table);
    }

    private static MLuaService serviceWithTable(LuaTable table) {
        return new MLuaService() {
            @Override public String name() { return "TestService"; }
            @Override public LuaTable toLua() { return table; }
        };
    }

    private static VarArgFunction function() {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return LuaValue.NONE;
            }
        };
    }
}
