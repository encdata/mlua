package dev.mlua.core.service;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaRuntime;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.core.require.MLuaModuleCache;
import dev.mlua.core.scheduler.MLuaTaskScheduler;
import dev.mlua.core.script.MLuaScript;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaServiceRegistrationDocumentationTest {
    @Test
    void undocumentedFunctionsAreRejectedWithoutRegistrationMetadata() {
        Path root = Path.of(".").toAbsolutePath().normalize();
        MLuaScript script = new MLuaScript("example_mod", ScriptType.SERVER, root,
            root.resolve("service.server.lua"), "service", "");
        MLuaRuntime runtime = new MLuaRuntime();
        runtime.registerClass("Object", null, "Root class used by service documentation tests.");
        runtime.registerInstanceBaseClass("Instance", "Object",
            "Base class for service documentation test instances.");
        runtime.registerServiceBaseClass("Service", "Instance",
            "Base class for service documentation test services.");
        runtime.registerService("Example", Side.BOTH, context -> new MLuaService() {
            @Override public String name() { return "Example"; }

            @Override public LuaTable toLua() {
                LuaTable table = new LuaTable();
                table.set("DoThing", new VarArgFunction() {
                    @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
                });
                table.set("Probe", new VarArgFunction() {
                    @Override public Varargs invoke(Varargs args) { return LuaValue.NONE; }
                });
                return table;
            }
        }, "Example service used to verify function documentation.",
            java.util.Map.of("Probe", "Returns the documented fixture probe value."));
        MLuaContext context = new MLuaContext(script, Side.SERVER, RegistrationPhase.RUNTIME,
            runtime.services(), new MLuaTaskScheduler(), new MLuaModuleCache(), null, root.resolve("data"),
            null, runtime.session(), runtime);

        LuaError failure = assertThrows(LuaError.class,
            () -> MLuaLuaBindings.materializeServiceTable(context, "Example"));
        assertTrue(failure.getMessage().contains("DoThing"));
    }
}
