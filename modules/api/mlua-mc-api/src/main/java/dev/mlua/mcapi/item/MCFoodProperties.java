package dev.mlua.mcapi.item;

public interface MCFoodProperties {
    int getNutrition();
    float getSaturationModifier();
    boolean isMeat();
    boolean isFastFood();
    boolean canAlwaysEat();

    /** Whether the active game's native food component stores the meat flag. */
    default boolean supportsMeatFlag() { return true; }

    /** Whether the active game's native food component stores the fast-food flag. */
    default boolean supportsFastFoodFlag() { return true; }

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private int nutrition;
        private float saturationModifier;
        private boolean meat;
        private boolean fastFood;
        private boolean canAlwaysEat;

        public Builder nutrition(int nutrition) {
            this.nutrition = nutrition;
            return this;
        }

        public Builder saturationModifier(float saturationModifier) {
            this.saturationModifier = saturationModifier;
            return this;
        }

        public Builder meat() {
            this.meat = true;
            return this;
        }

        public Builder fastFood() {
            this.fastFood = true;
            return this;
        }

        public Builder canAlwaysEat() {
            this.canAlwaysEat = true;
            return this;
        }

        public MCFoodProperties build() {
            int n = this.nutrition;
            float s = this.saturationModifier;
            boolean m = this.meat;
            boolean f = this.fastFood;
            boolean c = this.canAlwaysEat;
            return new MCFoodProperties() {
                @Override
                public int getNutrition() { return n; }

                @Override
                public float getSaturationModifier() { return s; }

                @Override
                public boolean isMeat() { return m; }

                @Override
                public boolean isFastFood() { return f; }

                @Override
                public boolean canAlwaysEat() { return c; }
            };
        }
    }
}
