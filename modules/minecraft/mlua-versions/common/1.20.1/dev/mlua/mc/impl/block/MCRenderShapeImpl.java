package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCRenderShape;
import net.minecraft.world.level.block.RenderShape;

public final class MCRenderShapeImpl {
    private MCRenderShapeImpl() {}

    public static MCRenderShape fromReal(RenderShape shape) {
        if (shape == null) return MCRenderShape.INVISIBLE;
        return switch (shape) {
            case INVISIBLE -> MCRenderShape.INVISIBLE;
            case ENTITYBLOCK_ANIMATED -> MCRenderShape.ENTITYBLOCK_ANIMATED;
            case MODEL -> MCRenderShape.MODEL;
        };
    }

    public static RenderShape toReal(MCRenderShape shape) {
        if (shape == null) return RenderShape.INVISIBLE;
        return switch (shape) {
            case INVISIBLE -> RenderShape.INVISIBLE;
            case ENTITYBLOCK_ANIMATED -> RenderShape.ENTITYBLOCK_ANIMATED;
            case MODEL -> RenderShape.MODEL;
        };
    }
}
