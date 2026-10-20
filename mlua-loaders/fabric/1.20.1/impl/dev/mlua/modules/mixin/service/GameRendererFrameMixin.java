package dev.mlua.modules.mixin.service;

import dev.mlua.mc.impl.fabric.FabricLifecycleWiring;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Provides whole-client-frame lifecycle callbacks, including frames with no world loaded. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFrameMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mlua$beforeRenderFrame(CallbackInfo callback) {
        FabricLifecycleWiring.fireRenderFrameStart();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mlua$afterRenderFrame(CallbackInfo callback) {
        FabricLifecycleWiring.fireRenderFrameEnd();
    }
}
