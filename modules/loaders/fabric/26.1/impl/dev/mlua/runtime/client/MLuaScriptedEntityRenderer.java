package dev.mlua.runtime.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** 26.1 render-state adapter for scripted living entities. */
public final class MLuaScriptedEntityRenderer extends MLuaScriptedEntityRendererBase {
    public MLuaScriptedEntityRenderer(EntityRendererProvider.Context context, String registryKey) {
        super(context, registryKey);
    }
}
