package dev.mlua.mc.impl.item.alchemy;

import dev.mlua.mcapi.item.alchemy.MCPotion;
import java.util.List;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;

public class MCPotionImpl implements MCPotion {
    private final Potion inner;

    public MCPotionImpl(Potion inner) {
        this.inner = inner;
    }

    public Potion inner() {
        return inner;
    }

    @Override
    public String getName(String prefix) {
        return inner.getName(prefix);
    }

    @Override
    public List<Object> getEffects() {
        return List.copyOf(inner.getEffects());
    }
}
