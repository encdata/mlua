package dev.mlua.mcapi.entity.attributes;

import java.util.HashMap;
import java.util.Map;

public interface MCAttributeSupplier {
    double get(MCAttribute attribute);

    static Builder builder() {
        return new Builder() {
            private final Map<MCAttribute, Double> map = new HashMap<>();

            @Override
            public Builder add(MCAttribute attribute, double defaultValue) {
                map.put(attribute, defaultValue);
                return this;
            }

            @Override
            public MCAttributeSupplier build() {
                Map<MCAttribute, Double> copy = new HashMap<>(map);
                return attribute -> copy.getOrDefault(attribute, 0.0);
            }
        };
    }

    interface Builder {
        Builder add(MCAttribute attribute, double defaultValue);
        MCAttributeSupplier build();
    }
}
