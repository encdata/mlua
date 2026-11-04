package dev.mlua.runtime.client.model;

import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MLuaBBModelCubeTransformTest {
    @Test
    void parsesStaticCubePivotRotationAndInflation() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "elements": [{
                "uuid": "cube",
                "from": [0, 2, 4],
                "to": [8, 10, 12],
                "origin": [1, 3, 5],
                "rotation": [0, 0, 45],
                "inflate": 0.5
              }]
            }
            """);

        MLuaBBModelAsset.Cube cube = asset.cubes().get(0);

        assertEquals(1.0F, cube.origin(0), 0.0001F);
        assertEquals(3.0F, cube.origin(1), 0.0001F);
        assertEquals(45.0F, cube.rotation(2), 0.0001F);
        assertEquals(-0.5F, cube.renderFrom(0), 0.0001F);
        assertEquals(12.5F, cube.renderTo(2), 0.0001F);
    }

    @Test
    void staticCubeRotationIsAppliedWithoutAnAnimation() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "elements": [{
                "uuid": "cube",
                "from": [-8, -8, -8],
                "to": [8, 8, 8],
                "origin": [0, 0, 0],
                "rotation": [0, 0, 90]
              }]
            }
            """);
        MLuaBBModelAsset.Cube cube = asset.cubes().get(0);
        Matrix4f transform = new Matrix4f();

        new MLuaAnimationPlayer().applyCubeTransform(transform, null, cube, 0.0F,
            new float[3], new float[3], new float[]{1, 1, 1});

        Vector3f point = transform.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(0.0F, point.x, 0.0001F);
        assertEquals(1.0F, point.y, 0.0001F);
        assertEquals(0.0F, point.z, 0.0001F);
    }

    @Test
    void boneEulerRotationUsesBlockbenchZyxOrder() {
        MLuaBBModelAsset.Bone bone = new MLuaBBModelAsset.Bone("root", "root", null,
            new float[]{0, 0, 0}, new float[]{90, 90, 0});
        Matrix4f transform = new Matrix4f();

        new MLuaAnimationPlayer().applyBoneTransform(transform, null, bone, 0.0F,
            new float[3], new float[3], new float[]{1, 1, 1});

        Vector3f point = transform.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(0.0F, point.x, 0.0001F);
        assertEquals(0.0F, point.y, 0.0001F);
        assertEquals(-1.0F, point.z, 0.0001F);
    }

    @Test
    void staticCubeEulerRotationUsesBlockbenchZyxOrder() {
        MLuaBBModelAsset.Cube cube = new MLuaBBModelAsset.Cube("cube", "cube",
            new float[]{-1, -1, -1}, new float[]{1, 1, 1}, new float[]{0, 0, 0},
            new float[]{90, 90, 0}, 0.0F, Map.of());
        Matrix4f transform = new Matrix4f();

        new MLuaAnimationPlayer().applyCubeTransform(transform, null, cube, 0.0F,
            new float[3], new float[3], new float[]{1, 1, 1});

        Vector3f point = transform.transformPosition(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(0.0F, point.x, 0.0001F);
        assertEquals(0.0F, point.y, 0.0001F);
        assertEquals(-1.0F, point.z, 0.0001F);
    }

    @Test
    void legacyYFlipAlsoTransformsCubePivotAndRotation() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "modded_entity_flip_y": true,
              "elements": [{
                "uuid": "cube",
                "from": [0, 2, 4],
                "to": [8, 10, 12],
                "origin": [1, 3, 5],
                "rotation": [10, 20, 30]
              }]
            }
            """);

        MLuaBBModelAsset.Cube cube = asset.cubes().get(0);

        assertEquals(-3.0F, cube.origin(1), 0.0001F);
        assertEquals(-10.0F, cube.rotation(0), 0.0001F);
        assertEquals(20.0F, cube.rotation(1), 0.0001F);
        assertEquals(-30.0F, cube.rotation(2), 0.0001F);
    }
}
