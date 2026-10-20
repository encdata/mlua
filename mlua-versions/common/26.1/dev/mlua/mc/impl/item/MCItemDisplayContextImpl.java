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
            default -> MCItemDisplayContext.NONE;
        };
    }
    public static ItemDisplayContext toReal(MCItemDisplayContext ctx) { return ItemDisplayContext.NONE; }
}
