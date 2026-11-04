package dev.mlua.modules.javabridge.service;

import dev.mlua.common.enums.RegistrationPhase;
import dev.mlua.common.enums.ScriptType;
import dev.mlua.common.enums.Side;
import dev.mlua.core.MLuaContextView;
import dev.mlua.core.service.MLuaService;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaJavaInputConverterTest {
    private final MLuaJavaService service = new MLuaJavaService(
            new MLuaService.Context(new TestContext()));

    @Test
    void convertsDenseLuaArrayToTheRequestedGenericList() {
        LuaTable values = new LuaTable();
        values.set(1, LuaValue.valueOf(4));
        values.set(2, LuaValue.valueOf(9));

        Object converted = service.convertLuaValue(values, typeOfListOfIntegers());

        assertEquals(List.of(4, 9), converted);
    }

    @Test
    void rejectsSparseLuaTablesForJavaCollections() {
        LuaTable values = new LuaTable();
        values.set(1, "first");
        values.set(3, "third");

        assertThrows(LuaError.class,
                () -> service.convertLuaValue(values, typeOfListOfIntegers()));
    }

    @Test
    void convertsJavaCollectionsToPrimitiveArrays() {
        int[] converted = (int[]) service.convertValue(List.of(2, 5, 8), int[].class);

        assertArrayEquals(new int[]{2, 5, 8}, converted);
    }

    @Test
    void rejectsNilForPrimitiveJavaParameters() {
        assertThrows(LuaError.class, () -> service.convertValue(null, int.class));
    }

    private static Type typeOfListOfIntegers() {
        return new TypeHint<List<Integer>>() {}.type();
    }

    private abstract static class TypeHint<T> {
        final Type type() {
            ParameterizedType superclass = (ParameterizedType) getClass().getGenericSuperclass();
            return superclass.getActualTypeArguments()[0];
        }
    }

    private static final class TestContext implements MLuaContextView {
        @Override
        public String ownerModId() {
            return "mlua_test";
        }

        @Override
        public Side side() {
            return Side.SERVER;
        }

        @Override
        public RegistrationPhase phase() {
            return RegistrationPhase.RUNTIME;
        }

        @Override
        public dev.mlua.core.scheduler.MLuaTaskSchedulerView scheduler() {
            return null;
        }

        @Override
        public MinecraftServer server() {
            return null;
        }

        @Override
        public Path filesRoot() {
            return Path.of(".");
        }

        @Override
        public boolean isClient() {
            return false;
        }

        @Override
        public boolean isServer() {
            return true;
        }

        @Override
        public boolean isRegistration() {
            return false;
        }

        @Override
        public boolean isPhase(RegistrationPhase phase) {
            return phase == RegistrationPhase.RUNTIME;
        }

        @Override
        public boolean isScriptType(ScriptType type) {
            return false;
        }
    }
}
