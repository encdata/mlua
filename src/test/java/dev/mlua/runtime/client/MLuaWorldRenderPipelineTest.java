package dev.mlua.runtime.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaWorldRenderPipelineTest {
    @Test
    void addsFollowedEntityCoordinatesOnceOnEveryAxis() {
        double[] position = {10.0D, 20.0D, 30.0D};

        MLuaWorldRenderPipeline.addFollowPosition(position, 1.0D, 2.0D, 3.0D);

        assertEquals(11.0D, position[0], 0.0001D);
        assertEquals(22.0D, position[1], 0.0001D);
        assertEquals(33.0D, position[2], 0.0001D);
    }
}
