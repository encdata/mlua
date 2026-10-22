package dev.mlua.modules.render.service;

import dev.mlua.core.service.MLuaReflectiveService;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Arrays;
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
        Set<String> expectedObjectMethods = Set.of(
            "CreateRect", "CreateMesh", "LoadBBModel", "CreateWorldLine", "CreateWorldBox",
            "Clear", "GetAll");
        assertTrue(Modifier.isPublic(MLuaRenderObjectApi.class.getModifiers()),
            "The inherited render API base must remain visible to reflective service discovery");
        Set<String> declaredObjectMethods = new HashSet<>();
        Arrays.stream(MLuaRenderObjectApi.class.getDeclaredMethods())
            .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
            .map(java.lang.reflect.Method::getName)
            .forEach(declaredObjectMethods::add);
        Set<String> missingDeclarations = new HashSet<>(expectedObjectMethods);
        missingDeclarations.removeAll(declaredObjectMethods);
        assertTrue(missingDeclarations.isEmpty(),
            () -> "Render object API declarations are missing: " + missingDeclarations);
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(api.functionDescriptions().keySet());
        assertTrue(missing.isEmpty(), () -> "Missing documented render API methods: " + missing);
    }
}
