package dev.mlua.mc.impl.item;

import dev.mlua.mcapi.item.MCFoodProperties;
import net.minecraft.world.food.FoodProperties;

public class MCFoodPropertiesImpl implements MCFoodProperties {
    private final FoodProperties inner;

    public MCFoodPropertiesImpl(FoodProperties inner) {
        this.inner = inner;
    }

    public FoodProperties inner() {
        return inner;
    }

    @Override
    public int getNutrition() {
        return inner.getNutrition();
    }

    @Override
    public float getSaturationModifier() {
        return inner.getSaturationModifier();
    }

    @Override
    public boolean isMeat() {
        return inner.isMeat();
    }

    @Override
    public boolean isFastFood() {
        return inner.isFastFood();
    }

    @Override
    public boolean canAlwaysEat() {
        return inner.canAlwaysEat();
    }
}
