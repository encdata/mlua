package dev.mlua.mcapi.effect;

import java.util.HashMap;
import java.util.Map;

public interface MCMobEffect {
    String getDescriptionId();
    int getColor();
    boolean isBeneficial();

    static MCMobEffect byId(int rawId) {
        return registry.get(rawId);
    }

    static void register(int rawId, MCMobEffect effect) {
        registry.put(rawId, effect);
    }

    Map<Integer, MCMobEffect> registry = new HashMap<>();
}
