package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCInteractionResult;
import net.minecraft.world.InteractionResult;

public final class MCInteractionResultImpl {
    private MCInteractionResultImpl() {}

    public static MCInteractionResult fromReal(InteractionResult result) {
        if (result == null) return MCInteractionResult.PASS;
        return switch (result) {
            case SUCCESS -> MCInteractionResult.SUCCESS;
            case CONSUME -> MCInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> MCInteractionResult.CONSUME_PARTIAL;
            case PASS -> MCInteractionResult.PASS;
            case FAIL -> MCInteractionResult.FAIL;
            default -> MCInteractionResult.PASS;
        };
    }

    public static InteractionResult toReal(MCInteractionResult result) {
        if (result == null) return InteractionResult.PASS;
        return switch (result) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case CONSUME_PARTIAL -> InteractionResult.CONSUME_PARTIAL;
            case PASS -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
        };
    }
}
