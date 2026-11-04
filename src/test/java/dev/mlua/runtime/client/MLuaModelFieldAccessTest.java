package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaModelFieldAccessTest {
    @Test
    void resolvesPrivateFieldsAcrossAnInheritancePath() {
        ModelFixture model = new ModelFixture();

        assertEquals("parent-field", MLuaModelFieldAccess.resolveFieldPath(model, "body.layer"));
        assertNull(MLuaModelFieldAccess.resolveFieldPath(model, "body.missing"));
    }

    @Test
    void rendererScanIncludesOnlyAccessibleInstanceReferences() {
        Set<String> fields = Arrays.stream(MLuaModelFieldAccess.rendererFields(RendererFixture.class))
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertTrue(fields.contains("model"));
        assertTrue(fields.contains("inheritedModel"));
        assertFalse(fields.contains("count"));
        assertFalse(fields.contains("shared"));
    }

    private static class BaseFixture {
        private final String layer = "parent-field";
        private final Object inheritedModel = new Object();
    }

    private static final class ModelFixture extends BaseFixture {
        private final BaseFixture body = new BaseFixture();
    }

    private static final class RendererFixture extends BaseFixture {
        private final Object model = new Object();
        private final int count = 1;
        private static final Object shared = new Object();
    }
}
