package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void recognizesTheSubmittedModelAmongAmbiguousRendererFields() {
        ModelFixture adultModel = new ModelFixture();
        ModelFixture babyModel = new ModelFixture();
        Renderer renderer = new Renderer(adultModel, babyModel);

        assertTrue(MLuaRendererModelResolver.isModelCandidate(renderer, adultModel, null));
        assertTrue(MLuaRendererModelResolver.isModelCandidate(renderer, babyModel, null));
        assertFalse(MLuaRendererModelResolver.isModelCandidate(renderer, new ModelFixture(), null));
        assertTrue(MLuaRendererModelResolver.isModelCandidate(renderer, renderer.nested.model, "nested.model"));
        assertFalse(MLuaRendererModelResolver.isModelCandidate(renderer, adultModel, "nested.model"));
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
