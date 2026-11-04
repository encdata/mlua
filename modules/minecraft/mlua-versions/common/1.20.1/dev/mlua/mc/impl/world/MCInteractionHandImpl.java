package dev.mlua.mc.impl.world;

import dev.mlua.mcapi.world.MCInteractionHand;
import net.minecraft.world.InteractionHand;

public final class MCInteractionHandImpl {
    private MCInteractionHandImpl() {}

    public static MCInteractionHand fromReal(InteractionHand hand) {
        if (hand == null) return MCInteractionHand.MAIN_HAND;
        return switch (hand) {
            case MAIN_HAND -> MCInteractionHand.MAIN_HAND;
            case OFF_HAND -> MCInteractionHand.OFF_HAND;
        };
    }

    public static InteractionHand toReal(MCInteractionHand hand) {
        if (hand == null) return InteractionHand.MAIN_HAND;
        return switch (hand) {
            case MAIN_HAND -> InteractionHand.MAIN_HAND;
            case OFF_HAND -> InteractionHand.OFF_HAND;
        };
    }
}
