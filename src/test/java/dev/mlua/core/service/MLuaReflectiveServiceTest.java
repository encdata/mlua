package dev.mlua.core.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.Side;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.core.MLuaContextView;
import java.lang.reflect.Type;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaReflectiveServiceTest {
    @Test
    void argumentExtractionPreservesTheServiceConversionExtensionPoint() {
        MLuaService.Context context = new MLuaService.Context(new TestContext());
        LuaTable api = new ConversionService(context).toLua();

        String dotResult = api.get("Echo").call(LuaValue.valueOf("input")).checkjstring();
        String colonResult = api.get("Echo").invoke(LuaValue.varargsOf(new LuaValue[]{
            api, LuaValue.valueOf("input")
        })).arg1().checkjstring();

        assertEquals("converted:input", dotResult);
        assertEquals("converted:input", colonResult);
    }

    @MLuaDescription("Service used to check reflective service argument conversion.")
    private static final class ConversionService extends MLuaReflectiveService {
        private ConversionService(Context context) { super(context); }

        @Override public String name() { return "ConversionTest"; }

        @MLuaDescription("Returns the converted argument.")
        public String Echo(String value) { return value; }

        @Override
        protected Object toJavaValue(LuaValue value, Type targetType) {
            return "converted:" + value.tojstring();
        }
    }

    private static final class TestContext implements MLuaContextView {
        @Override public String ownerModId() { return "mlua_test"; }
        @Override public Side side() { return Side.SERVER; }
        @Override public RegistrationPhase phase() { return RegistrationPhase.RUNTIME; }
        @Override public dev.mlua.core.scheduler.MLuaTaskSchedulerView scheduler() { return null; }
        @Override public net.minecraft.server.MinecraftServer server() { return null; }
        @Override public Path filesRoot() { return Path.of("."); }
        @Override public boolean isClient() { return false; }
        @Override public boolean isServer() { return true; }
        @Override public boolean isRegistration() { return false; }
        @Override public boolean isPhase(RegistrationPhase phase) { return false; }
        @Override public boolean isScriptType(ScriptType type) { return false; }
    }
}
