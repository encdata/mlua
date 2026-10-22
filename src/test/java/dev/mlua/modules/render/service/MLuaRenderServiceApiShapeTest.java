package dev.mlua.modules.render.service;

import dev.mlua.core.service.MLuaReflectiveService;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRenderServiceApiShapeTest {
    @Test
    void inheritedObjectApiMethodsRemainInThePublicServiceCatalog() {
        MLuaReflectiveService.ApiMetadata api = MLuaReflectiveService.apiMetadata(MLuaRenderService.class);

        assertTrue(api.description().contains("Client-side"));
        Set<String> expected = Set.of(
            "CreateRect", "CreateMesh", "LoadBBModel", "CreateWorldLine", "CreateWorldBox",
            "Clear", "GetAll", "RegisterEntityRenderPass", "RegisterWorldGeometryPass",
            "RegisterHudRenderPass", "GetRenderCallbackLimits");
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(api.functionDescriptions().keySet());
        assertTrue(missing.isEmpty(), () -> "Missing documented render API methods: " + missing);
    }
}
