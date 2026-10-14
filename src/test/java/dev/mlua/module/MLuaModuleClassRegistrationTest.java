package dev.mlua.module;

import dev.mlua.common.enums.Side;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModuleClassRegistrationTest {
    @Test
    void sharedRuntimeRegistersClassesFromServerAndClientModules() {
        MLuaModuleLoader loader = new MLuaModuleLoader();
        loader.register(module("server_module", Side.SERVER, "ServerEntity"));
        loader.register(module("client_module", Side.CLIENT, "ClientWidget"));
        RecordingRegistration registration = new RecordingRegistration();

        loader.registerClasses(registration);

        assertEquals(Set.of("ServerEntity", "ClientWidget"), registration.names);
    }

    @Test
    void explicitSideRegistrationRemainsFilteredForSideSpecificSnapshots() {
        MLuaModuleLoader loader = new MLuaModuleLoader();
        loader.register(module("server_module", Side.SERVER, "ServerEntity"));
        loader.register(module("client_module", Side.CLIENT, "ClientWidget"));
        RecordingRegistration registration = new RecordingRegistration();

        loader.registerClasses(registration, Side.SERVER);

        assertEquals(Set.of("ServerEntity"), registration.names);
    }

    @Test
    void clientRuntimeSupportsServerModulesButDedicatedServerSkipsClientModules() {
        assertTrue(MLuaModuleLoader.supportsSide(Side.SERVER, true));
        assertTrue(MLuaModuleLoader.supportsSide(Side.CLIENT, true));
        assertTrue(MLuaModuleLoader.supportsSide(Side.SERVER, false));
        assertTrue(MLuaModuleLoader.supportsSide(Side.BOTH, false));
        assertFalse(MLuaModuleLoader.supportsSide(Side.CLIENT, false));
    }

    private static MLuaModule module(String id, Side side, String className) {
        return new MLuaModule() {
            @Override public String id() { return id; }
            @Override public Side side() { return side; }
            @Override public void registerClasses(MLuaRegistration registration) { registration.registerClass(className, null); }
        };
    }

    private static final class RecordingRegistration implements MLuaRegistration {
        private final Set<String> names = new HashSet<>();

        @Override
        public LuaTable registerClass(String name, String parentName) {
            names.add(name);
            return new LuaTable();
        }

        @Override
        public LuaTable registerClassTable(String name, LuaTable classTable, String parentName) {
            names.add(name);
            return classTable;
        }

        @Override
        public LuaTable classTable(String name) {
            return null;
        }
    }
}
