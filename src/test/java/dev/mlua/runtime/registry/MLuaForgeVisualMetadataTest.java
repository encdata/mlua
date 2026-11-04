package dev.mlua.runtime.registry;

import dev.mlua.loaderapi.EntityVisualInfo;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaForgeVisualMetadataTest {
    @Test
    void resolvesPathSpecificMetadataBeforeBasenameFallback() {
        MLuaForgeVisualMetadata metadata = new MLuaForgeVisualMetadata();
        metadata.setItemModel("addon:item", "addon:item/path_model");
        metadata.setItemModel("addon:shared", "addon:shared_model");

        assertEquals("addon:item/path_model", metadata.itemModel("addon", "item"));
        assertEquals("addon:shared_model", metadata.itemModel("addon", "folder/shared"));
        assertNull(metadata.itemModel("addon", "missing"));
    }

    @Test
    void snapshotsDescriptionsAndRestoresOnlyTheExpectedEntityVisual() {
        MLuaForgeVisualMetadata metadata = new MLuaForgeVisualMetadata();
        var description = new java.util.ArrayList<>(List.of("first", "second"));
        metadata.setBlockDescription("addon:block", description);
        description.set(0, "changed");
        assertEquals(List.of("first", "second"), metadata.blockDescription("addon", "block"));

        var expected = new EntityVisualInfo("addon:texture", "addon:model", 0.5F);
        var previous = new EntityVisualInfo("addon:old_texture", "addon:old_model", 0.25F);
        metadata.setEntityVisual("addon:entity", new MLuaEntityVisualDescriptor(
                expected.texture(), expected.model(), expected.shadowRadius()));

        assertFalse(metadata.restoreEntityVisual("addon:entity",
                new EntityVisualInfo("addon:other", expected.model(), expected.shadowRadius()), previous));
        assertTrue(metadata.restoreEntityVisual("addon:entity", expected, previous));
        assertEquals(previous.texture(), metadata.entityVisual("addon:entity").texture());
        assertTrue(metadata.restoreEntityVisual("addon:entity", previous, null));
        assertNull(metadata.entityVisual("addon:entity"));
    }
}
