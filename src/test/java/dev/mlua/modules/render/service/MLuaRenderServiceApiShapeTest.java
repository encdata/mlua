package dev.mlua.modules.render.service;

import dev.mlua.core.service.MLuaReflectiveService;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaRenderServiceApiShapeTest {
    @Test
    void inheritedObjectApiMethodsRemainInThePublicServiceCatalog() {
        MLuaReflectiveService.ApiMetadata api = MLuaReflectiveService.apiMetadata(MLuaRenderService.class);

        assertTrue(api.description().contains("Client-side"));
        assertTrue(api.functionDescriptions().keySet().containsAll(Set.of(
            "CreateRect", "CreateMesh", "LoadBBModel", "CreateWorldLine", "CreateWorldBox",
            "Clear", "GetAll", "RegisterEntityRenderPass", "RegisterWorldGeometryPass",
            "RegisterHudRenderPass", "GetRenderCallbackLimits")));
    }
}
