package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

final class MLuaRendererModelResolverTest {
    @Test
    void resolvesConfiguredNestedModelPath() {
        Renderer renderer = new Renderer();

        assertSame(renderer.nested.model,
            MLuaRendererModelResolver.findModel(renderer, "nested.model", ModelFixture.class::isInstance));
    }

    @Test
    void infersOnlyAnUnambiguousAdaptedRendererModel() {
        ModelFixture model = new ModelFixture();
        Renderer renderer = new Renderer(model, null);

        assertSame(model, MLuaRendererModelResolver.findModel(renderer, null, ModelFixture.class::isInstance));
        assertNull(MLuaRendererModelResolver.findModel(new Renderer(), null, ModelFixture.class::isInstance));
        assertNull(MLuaRendererModelResolver.findModel(
            new Renderer(new ModelFixture(), new ModelFixture()), null, ModelFixture.class::isInstance));
    }

    private static final class ModelFixture { }

    private static final class NestedFixture {
        private final ModelFixture model = new ModelFixture();
    }

    private static final class Renderer {
        private final NestedFixture nested = new NestedFixture();
        private final ModelFixture first;
        private final ModelFixture second;

        private Renderer() {
            this(null, null);
        }

        private Renderer(ModelFixture first, ModelFixture second) {
            this.first = first;
            this.second = second;
        }
    }
}
