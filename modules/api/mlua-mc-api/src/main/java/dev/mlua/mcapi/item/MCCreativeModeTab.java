package dev.mlua.mcapi.item;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCCreativeModeTab {
    String getDisplayName();
    MCResourceLocation getRegistryName();

    static Builder builder() {
        return new Builder();
    }

    class Builder {
        private String displayName;
        private MCResourceLocation registryName;

        public Builder withDisplayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder withRegistryName(MCResourceLocation registryName) {
            this.registryName = registryName;
            return this;
        }

        public MCCreativeModeTab build() {
            String name = this.displayName;
            MCResourceLocation reg = this.registryName;
            return new MCCreativeModeTab() {
                @Override
                public String getDisplayName() {
                    return name;
                }

                @Override
                public MCResourceLocation getRegistryName() {
                    return reg;
                }
            };
        }
    }
}
