package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCTooltipFlag;
import net.minecraft.world.item.TooltipFlag;

public final class MCTooltipFlagImpl {
    private MCTooltipFlagImpl() {}

    public static MCTooltipFlag fromReal(TooltipFlag flag) {
        if (flag == null) return MCTooltipFlag.NORMAL;
        return flag.isAdvanced() ? MCTooltipFlag.ADVANCED : MCTooltipFlag.NORMAL;
    }

    public static TooltipFlag toReal(MCTooltipFlag flag) {
        if (flag == null) return TooltipFlag.Default.NORMAL;
        return flag.isAdvanced() ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
    }
}
