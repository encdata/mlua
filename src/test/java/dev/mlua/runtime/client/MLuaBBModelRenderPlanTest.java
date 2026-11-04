package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaBBModelRenderPlanTest {
    @Test
    void groupsEachCubeOncePerMaterialInStableOrder() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "textures": [
                {"relative_path": "first.png"},
                {"relative_path": "second.png"}
              ],
              "elements": [
                {"uuid": "cube-a", "name": "a", "from": [0, 0, 0], "to": [1, 1, 1],
                 "faces": {"north": {"texture": 0}, "south": {"texture": 1}}},
                {"uuid": "cube-b", "name": "b", "from": [1, 0, 0], "to": [2, 1, 1],
                 "faces": {"east": {"texture": 0}}}
              ]
            }
            """);
        var firstTexture = MLuaResourceLocations.build("addon", "first.png");
        var secondTexture = MLuaResourceLocations.build("addon", "second.png");
        var fallbackTexture = MLuaResourceLocations.build("minecraft", "textures/block/stone.png");

        MLuaBBModelRenderPlan plan = MLuaBBModelRenderPlan.prepare(asset, List.of(firstTexture, secondTexture));

        assertEquals(3, plan.textureBatches().size());
        assertEquals(firstTexture, plan.textureBatches().get(0).texture());
        assertEquals(List.of("cube-a", "cube-b"),
            plan.textureBatches().get(0).cubes().stream().map(MLuaBBModelAsset.Cube::uuid).toList());
        assertEquals(secondTexture, plan.textureBatches().get(1).texture());
        assertEquals(List.of("cube-a"),
            plan.textureBatches().get(1).cubes().stream().map(MLuaBBModelAsset.Cube::uuid).toList());
        assertEquals(fallbackTexture, plan.textureBatches().get(2).texture());
        assertEquals(List.of("cube-a", "cube-b"),
            plan.textureBatches().get(2).cubes().stream().map(MLuaBBModelAsset.Cube::uuid).toList());
    }
}
