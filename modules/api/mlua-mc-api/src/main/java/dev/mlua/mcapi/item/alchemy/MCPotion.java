package dev.mlua.mcapi.item.alchemy;

import dev.mlua.mcapi.MCApiRuntime;

import java.util.Collections;
import java.util.List;

public interface MCPotion {
    String getName(String prefix);
    List<Object> getEffects();

    static MCPotion byName(String name) {
        return (MCPotion) MCApiRuntime.wrapPotion(MCApiRuntime.lookupPotion(name));
    }

    static MCPotion fromEffects(String name, List<Object> effects) {
        List<Object> eff = effects != null ? effects : Collections.emptyList();
        return new MCPotion() {
            @Override
            public String getName(String prefix) {
                return prefix + name;
            }

            @Override
            public List<Object> getEffects() {
                return eff;
            }
        };
    }
}
