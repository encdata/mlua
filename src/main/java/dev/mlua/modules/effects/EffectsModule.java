package dev.mlua.modules.effects;

import dev.mlua.modules.effects.Service.MLuaPotionEffectsService;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class EffectsModule implements MLuaModule {
    @Override
    public String id() { return "effects"; }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder effects = ServiceEntry.builder("PotionEffects", MLuaPotionEffectsService::new)
            .description("Queries registered potion effects and exposes effect metadata to Lua.");
        effects.function("Get", "Returns metadata for a potion effect identifier.")
            .function("Exists", "Returns whether a potion effect identifier exists.")
            .function("GetAll", "Returns metadata for all registered potion effects.");
        return List.of(effects.build());
    }
}
