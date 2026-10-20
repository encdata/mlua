package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCItemDisplayContext;
import net.minecraft.world.item.ItemDisplayContext;

public final class MCItemDisplayContextImpl {
    private MCItemDisplayContextImpl() {}

    public static MCItemDisplayContext fromReal(ItemDisplayContext ctx) {
        if (ctx == null) return MCItemDisplayContext.NONE;
        return switch (ctx) {
            case NONE -> MCItemDisplayContext.NONE;
            case THIRD_PERSON_LEFT_HAND -> MCItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            case THIRD_PERSON_RIGHT_HAND -> MCItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case FIRST_PERSON_LEFT_HAND -> MCItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            case FIRST_PERSON_RIGHT_HAND -> MCItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            case HEAD -> MCItemDisplayContext.HEAD;
            case GUI -> MCItemDisplayContext.GUI;
            case GROUND -> MCItemDisplayContext.GROUND;
            case FIXED -> MCItemDisplayContext.FIXED;
        };
    }

    public static ItemDisplayContext toReal(MCItemDisplayContext ctx) {
        if (ctx == null) return ItemDisplayContext.NONE;
        return switch (ctx) {
            case NONE -> ItemDisplayContext.NONE;
            case THIRD_PERSON_LEFT_HAND -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            case THIRD_PERSON_RIGHT_HAND -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            case FIRST_PERSON_LEFT_HAND -> ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
            case FIRST_PERSON_RIGHT_HAND -> ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
            case HEAD -> ItemDisplayContext.HEAD;
            case GUI -> ItemDisplayContext.GUI;
            case GROUND -> ItemDisplayContext.GROUND;
            case FIXED -> ItemDisplayContext.FIXED;
        };
    }
}
