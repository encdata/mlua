package dev.mlua.runtime.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** Dedicated 26.1 NeoForge render-state adapter for scripted living entities. */
public final class MLuaScriptedEntityRenderer extends MLuaScriptedEntityRendererBase {
    public MLuaScriptedEntityRenderer(EntityRendererProvider.Context context, String key) {
        super(context, key);
    }
}
