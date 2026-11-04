package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCInteractionResult;
import net.minecraft.world.InteractionResult;

public final class MCInteractionResultImpl {
    private MCInteractionResultImpl() {}
    public static MCInteractionResult fromReal(InteractionResult result) {
        if (result == null || result == InteractionResult.PASS) return MCInteractionResult.PASS;
        if (result == InteractionResult.FAIL) return MCInteractionResult.FAIL;
        if (result == InteractionResult.CONSUME) return MCInteractionResult.CONSUME;
        return MCInteractionResult.SUCCESS;
    }
    public static InteractionResult toReal(MCInteractionResult result) {
        if (result == null || result == MCInteractionResult.PASS) return InteractionResult.PASS;
        if (result == MCInteractionResult.FAIL) return InteractionResult.FAIL;
        if (result == MCInteractionResult.CONSUME || result == MCInteractionResult.CONSUME_PARTIAL) return InteractionResult.CONSUME;
        return InteractionResult.SUCCESS;
    }
}
