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
            .description("Queries potion effects applied to entities.");
        effects.function("Get", "Returns details for a named potion effect.")
            .function("Exists", "Returns whether a named potion effect exists.")
            .function("GetAll", "Returns the available potion effect identifiers.");
        return List.of(effects.build());
    }
}
