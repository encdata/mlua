package dev.mlua.core.service;

import dev.mlua.common.enums.Side;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class MLuaServiceRegistryBatchTest {
    @Test
    void conflictingBatchLeavesEveryServiceUnregistered() {
        MLuaServiceRegistry registry = new MLuaServiceRegistry();
        var factory = (java.util.function.Function<MLuaService.Context, MLuaService>) context -> null;
        registry.register("existing", Side.BOTH, factory, "Existing fixture service.", java.util.Map.of("Ping", "Checks the fixture service."));

        assertThrows(IllegalStateException.class, () -> registry.registerBatch(Side.BOTH, List.of(
            new MLuaServiceRegistry.Registration("new_before_conflict", factory, "New fixture service.", java.util.Map.of("Ping", "Checks the fixture service.")),
            new MLuaServiceRegistry.Registration("existing", factory, "Existing fixture service.", java.util.Map.of("Ping", "Checks the fixture service."))
        )));

        assertEquals(java.util.Set.of("existing"), registry.names());
    }

    @Test
    void duplicateEntriesWithinBatchAreRejectedAtomically() {
        MLuaServiceRegistry registry = new MLuaServiceRegistry();
        var factory = (java.util.function.Function<MLuaService.Context, MLuaService>) context -> null;

        assertThrows(IllegalStateException.class, () -> registry.registerBatch(Side.SERVER, List.of(
            new MLuaServiceRegistry.Registration("repeated", factory, "Repeated fixture service.", java.util.Map.of("Ping", "Checks the fixture service.")),
            new MLuaServiceRegistry.Registration("repeated", factory, "Repeated fixture service.", java.util.Map.of("Ping", "Checks the fixture service."))
        )));

        assertEquals(java.util.Set.of(), registry.names());
    }
}
