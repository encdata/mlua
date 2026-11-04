package dev.mlua.core;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mlua.common.enums.Side;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;

class MLuaRuntimeSessionServicesTest {
    @Test
    void cachesLuaInstancesPerSideAndClassTable() {
        MLuaRuntimeSessionServices services = new MLuaRuntimeSessionServices();
        LuaTable serverClass = new LuaTable();
        LuaTable clientClass = new LuaTable();
        LuaTable firstServerInstance = new LuaTable();

        assertSame(firstServerInstance, services.materialize(
            Side.SERVER, "Render", serverClass, () -> firstServerInstance));
        assertSame(firstServerInstance, services.materialize(
            Side.SERVER, "Render", serverClass, LuaTable::new));
        assertNotSame(firstServerInstance, services.materialize(
            Side.SERVER, "Render", new LuaTable(), LuaTable::new));
        assertNotSame(firstServerInstance, services.materialize(
            Side.CLIENT, "Render", clientClass, LuaTable::new));
    }

    @Test
    void sharedJavaStateStaysScopedAndCanBeRemovedAcrossSides() {
        MLuaRuntimeSessionServices services = new MLuaRuntimeSessionServices();
        Object serverState = services.sharedState(Side.SERVER, "Render", "frames", Object.class, Object::new);
        Object clientState = services.sharedState(Side.CLIENT, "Render", "frames", Object.class, Object::new);

        assertSame(serverState, services.existingState(Side.SERVER, "Render", "frames", Object.class));
        assertSame(clientState, services.existingState(Side.CLIENT, "Render", "frames", Object.class));
        assertNotSame(serverState, clientState);
        assertThrows(IllegalStateException.class,
            () -> services.sharedState(Side.SERVER, "Render", "frames", String.class, String::new));

        assertTrue(services.removeState(Side.BOTH, "Render", "frames"));
        assertNull(services.existingState(Side.SERVER, "Render", "frames", Object.class));
        assertNull(services.existingState(Side.CLIENT, "Render", "frames", Object.class));
    }

    @Test
    void clearingAndForgettingOnlyRemoveMatchingLuaServiceInstances() {
        MLuaRuntimeSessionServices services = new MLuaRuntimeSessionServices();
        LuaTable serverClass = new LuaTable();
        LuaTable clientClass = new LuaTable();
        LuaTable serverInstance = services.materialize(Side.SERVER, "Render", serverClass, LuaTable::new);
        LuaTable clientInstance = services.materialize(Side.CLIENT, "Render", clientClass, LuaTable::new);

        services.forgetServicesForClass(serverClass);
        assertNotSame(serverInstance, services.materialize(Side.SERVER, "Render", serverClass, LuaTable::new));
        assertSame(clientInstance, services.materialize(Side.CLIENT, "Render", clientClass, LuaTable::new));

        services.clear(Side.CLIENT);
        assertNotSame(clientInstance, services.materialize(Side.CLIENT, "Render", clientClass, LuaTable::new));
        assertSame(serverInstance, services.materialize(Side.SERVER, "Other", serverClass, () -> serverInstance));
    }
}
