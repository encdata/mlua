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
        return inner.nutrition();
    }

    @Override
    public float getSaturationModifier() {
        return inner.saturation();
    }

    @Override
    public boolean isMeat() {
        return false;
    }

    @Override
    public boolean isFastFood() {
        return false;
    }

    @Override
    public boolean canAlwaysEat() {
        return inner.canAlwaysEat();
    }

    @Override
    public boolean supportsMeatFlag() {
        return false;
    }

    @Override
    public boolean supportsFastFoodFlag() {
        return false;
    }
}
