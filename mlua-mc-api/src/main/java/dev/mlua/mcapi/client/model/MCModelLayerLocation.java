package dev.mlua.mcapi.client.model;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCModelLayerLocation {
    MCResourceLocation getModel();
    String getLayer();

    static MCModelLayerLocation of(MCResourceLocation model, String layer) {
        return new MCModelLayerLocation() {
            @Override
            public MCResourceLocation getModel() {
                return model;
            }

            @Override
            public String getLayer() {
                return layer;
            }

            @Override
            public String toString() {
                return "MCModelLayerLocation{model=" + model + ", layer='" + layer + "'}";
            }
        };
    }
}
