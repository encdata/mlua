package dev.mlua.mcapi.block;

public interface MCBlockBehaviour {

    Properties getProperties();

    final class Properties {
        private float destroyTime = 1.5f;
        private float explosionResistance = 6.0f;
        private MCSoundType soundType;
        private MCMapColor mapColor;
        private boolean noOcclusion;
        private boolean instabreak;
        private boolean randomTicks;
        private boolean requiresCorrectToolForDrops;
        private boolean noCollision;
        private int lightLevel;
        private float friction = 0.6f;
        private float speedFactor = 1.0f;
        private float jumpFactor = 1.0f;

        private Properties() {}

        public static Properties of() {
            return new Properties();
        }

        public Properties strength(float destroyTime, float explosionResistance) {
            this.destroyTime = destroyTime;
            this.explosionResistance = explosionResistance;
            return this;
        }

        public Properties destroyTime(float destroyTime) {
            this.destroyTime = destroyTime;
            return this;
        }

        public Properties explosionResistance(float explosionResistance) {
            this.explosionResistance = explosionResistance;
            return this;
        }

        public Properties sound(MCSoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public Properties mapColor(MCMapColor mapColor) {
            this.mapColor = mapColor;
            return this;
        }

        public Properties noOcclusion() {
            this.noOcclusion = true;
            return this;
        }

        public Properties instabreak() {
            this.instabreak = true;
            this.destroyTime = 0.0f;
            return this;
        }

        public Properties randomTicks() {
            this.randomTicks = true;
            return this;
        }

        public Properties requiresCorrectToolForDrops() {
            this.requiresCorrectToolForDrops = true;
            return this;
        }

        public Properties noCollision() {
            this.noCollision = true;
            return this;
        }

        public Properties lightLevel(int lightLevel) {
            this.lightLevel = lightLevel;
            return this;
        }

        public Properties friction(float friction) {
            this.friction = friction;
            return this;
        }

        public Properties speedFactor(float speedFactor) {
            this.speedFactor = speedFactor;
            return this;
        }

        public Properties jumpFactor(float jumpFactor) {
            this.jumpFactor = jumpFactor;
            return this;
        }

        public float destroyTime() { return destroyTime; }
        public float explosionResistance() { return explosionResistance; }
        public MCSoundType sound() { return soundType; }
        public MCMapColor mapColor() { return mapColor; }
        public boolean isNoOcclusion() { return noOcclusion; }
        public boolean isInstabreak() { return instabreak; }
        public boolean isRandomTicks() { return randomTicks; }
        public boolean isRequiresCorrectToolForDrops() { return requiresCorrectToolForDrops; }
        public boolean isNoCollision() { return noCollision; }
        public int lightLevel() { return lightLevel; }
        public float friction() { return friction; }
        public float speedFactor() { return speedFactor; }
        public float jumpFactor() { return jumpFactor; }
    }
}
