package dev.mlua.modules.effects;

import dev.mlua.modules.effects.service.MLuaPotionEffectsService;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.module.MLuaModule;
import java.util.List;

public class EffectsModule implements MLuaModule {
    @Override
    public String id() { return "effects"; }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        MLuaPotionEffectClassBinding.register(registration);
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder effects = ServiceEntry.builder("PotionEffects", MLuaPotionEffectsService::new)
            .reflective(MLuaPotionEffectsService.class);
        return List.of(effects.build());
    }
}
