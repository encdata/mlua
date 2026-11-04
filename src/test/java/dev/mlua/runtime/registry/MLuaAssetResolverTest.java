package dev.mlua.runtime.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class MLuaAssetResolverTest {
    @Test
    void folderReferencesResolveInMinecraftTextureFolders() {
        assertEquals("minecraft:item/diamond",
            MLuaAssetResolver.resolveTexture("addon", "item:/minecraft:diamond", "gui").toString());
        assertEquals("othermod:block/stone",
            MLuaAssetResolver.resolveTexture("addon", "block:/othermod:stone", "item").toString());
        assertEquals("addon:gui/folder/texture",
            MLuaAssetResolver.resolveTexture("addon", "gui:/folder/texture", "item").toString());
        assertEquals("modid:gui/folder/texture",
            MLuaAssetResolver.resolveTexture("addon", "modid:/gui/folder/texture", "item").toString());
        assertEquals("addon:entity/creature/skin",
            MLuaAssetResolver.resolveTexture("addon", "entity:/creature/skin", "models").toString());
    }

    @Test
    void folderReferencesResolveToPngAssetsAndKeepExplicitNamespaces() {
        assertEquals("minecraft:textures/item/diamond.png",
            MLuaAssetResolver.resolveTextureFile("addon", "item:/minecraft:diamond", "gui").toString());
        assertEquals("addon:textures/gui/folder/texture.png",
            MLuaAssetResolver.resolveTextureFile("addon", "gui:/folder/texture", "item").toString());
        assertEquals("modid:textures/gui/folder/texture.png",
            MLuaAssetResolver.resolveTextureFile("addon", "modid:/gui/folder/texture", "item").toString());
        assertEquals("othermod:textures/block/stone.png",
            MLuaAssetResolver.resolveTextureFile("addon", "block:/othermod:stone.png", "gui").toString());
    }

    @Test
    void recognizesNamespacedAndFolderTextureUris() {
        assertTrue(MLuaAssetResolver.isTextureUri("gui:/icons/key"));
        assertTrue(MLuaAssetResolver.isTextureUri("item:/minecraft:diamond"));
        assertTrue(MLuaAssetResolver.isTextureUri("modid:/gui/folder/texture"));
        assertFalse(MLuaAssetResolver.isTextureUri("minecraft:textures/gui/panorama.png"));
        assertFalse(MLuaAssetResolver.isTextureUri("gui:icons/key"));
    }
}
