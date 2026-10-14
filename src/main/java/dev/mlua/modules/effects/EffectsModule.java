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
            .reflective(MLuaPotionEffectsService.class);
        return List.of(effects.build());
    }
}
