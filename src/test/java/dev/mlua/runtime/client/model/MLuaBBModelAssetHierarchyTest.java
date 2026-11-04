package dev.mlua.runtime.client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class MLuaBBModelAssetHierarchyTest {
    @Test
    void indexesBoneIdsNamesAndOrderedChildren() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "outliner": [{
                "uuid": "root-id",
                "name": "root",
                "children": [
                  {"uuid": "left-id", "name": "arm", "children": []},
                  {"uuid": "right-id", "name": "arm", "children": []}
                ]
              }]
            }
            """);

        assertEquals("root", asset.boneById("root-id").name());
        assertEquals(2, asset.bonesNamed("arm").size());
        assertEquals("left-id", asset.childrenOf("root-id").get(0).uuid());
        assertEquals("right-id", asset.childrenOf("root-id").get(1).uuid());
        assertNull(asset.boneById("missing"));
    }

    @Test
    void readsBlockbenchFiveGroupTransformsFromGroupDefinitions() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "5.0", "model_format": "free"},
              "groups": [
                {"uuid": "root-id", "name": "root", "origin": [1, 2, 3], "rotation": [4, 5, 6]},
                {"uuid": "child-id", "name": "arm", "origin": [7, 8, 9], "rotation": [10, 11, 12]}
              ],
              "elements": [{"uuid": "cube-id", "from": [0, 0, 0], "to": [2, 2, 2]}],
              "outliner": [{"uuid": "root-id", "children": [
                {"uuid": "child-id", "children": ["cube-id"]}
              ]}]
            }
            """);

        MLuaBBModelAsset.Bone root = asset.boneById("root-id");
        MLuaBBModelAsset.Bone child = asset.boneById("child-id");

        assertEquals("root", root.name());
        assertEquals(1.0F, root.pivot(0), 0.0001F);
        assertEquals(6.0F, root.rotation(2), 0.0001F);
        assertEquals("root-id", child.parent());
        assertEquals(8.0F, child.pivot(1), 0.0001F);
        assertEquals(10.0F, child.rotation(0), 0.0001F);
        assertEquals(java.util.List.of("root-id", "child-id"), asset.boneChainForCube("cube-id")
            .stream().map(MLuaBBModelAsset.Bone::uuid).toList());
    }

    @Test
    void keepsInlineGroupTransformsFromOlderBlockbenchProjects() {
        MLuaBBModelAsset asset = MLuaBBModelAsset.parse("""
            {
              "meta": {"format_version": "4.10", "model_format": "free"},
              "elements": [{"uuid": "cube-id", "from": [0, 0, 0], "to": [2, 2, 2]}],
              "outliner": [{"uuid": "root-id", "name": "root", "origin": [1, 2, 3],
                "rotation": [4, 5, 6], "children": ["cube-id"]}]
            }
            """);

        MLuaBBModelAsset.Bone root = asset.boneById("root-id");

        assertEquals(2.0F, root.pivot(1), 0.0001F);
        assertEquals(4.0F, root.rotation(0), 0.0001F);
    }
}
