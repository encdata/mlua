package dev.mlua.modules.mixin.service;

import dev.mlua.minecraft.client.MLuaKeybindController;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reapplies script-held mappings immediately before Minecraft samples movement input. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    //--version 1.20.1,1.21.1
    private void mlua$refreshSimulatedMappings(boolean slowDown, float slowDownFactor,
                                               CallbackInfo callback) {
        MLuaKeybindController.refreshSimulatedPresses();
    }
    //--endversion
    //--version 26.1
    private void mlua$refreshSimulatedMappings(CallbackInfo callback) {
        MLuaKeybindController.refreshSimulatedPresses();
    }
    //--endversion
}
