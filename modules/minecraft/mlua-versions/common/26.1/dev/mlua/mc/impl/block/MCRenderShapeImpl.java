package dev.mlua.mc.impl.block;

import dev.mlua.mcapi.block.MCRenderShape;
import net.minecraft.world.level.block.RenderShape;

public final class MCRenderShapeImpl {
    private MCRenderShapeImpl() {}
    public static MCRenderShape fromReal(RenderShape shape) { return shape == RenderShape.INVISIBLE ? MCRenderShape.INVISIBLE : MCRenderShape.MODEL; }
    public static RenderShape toReal(MCRenderShape shape) { return shape == null || shape == MCRenderShape.INVISIBLE ? RenderShape.INVISIBLE : RenderShape.MODEL; }
}
