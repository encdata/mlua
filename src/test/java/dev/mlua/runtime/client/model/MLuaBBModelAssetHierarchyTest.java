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
}
