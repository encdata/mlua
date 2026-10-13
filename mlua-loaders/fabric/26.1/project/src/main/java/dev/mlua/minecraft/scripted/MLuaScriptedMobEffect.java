package dev.mlua.minecraft.scripted;

import dev.mlua.minecraft.MLuaResourceLocations;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public final class MLuaScriptedMobEffect extends MobEffect {
    private final String descriptionId;
    private final Identifier iconTexture;
    private final boolean instant;
    public MLuaScriptedMobEffect(MobEffectCategory category, int color, String behaviorKey, String descriptionId, Identifier iconTexture, boolean visibleInInventory, boolean visibleInGui, boolean instant, int tickInterval) {
        super(category, color); this.descriptionId = descriptionId; this.iconTexture = iconTexture; this.instant = instant;
    }
    @Override public String getDescriptionId() { return descriptionId == null || descriptionId.isBlank() ? super.getDescriptionId() : descriptionId; }
    @Override public boolean isInstantenous() { return instant; }
    public Identifier getIconTexture() { return iconTexture; }
}
