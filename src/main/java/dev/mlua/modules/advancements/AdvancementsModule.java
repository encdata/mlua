package dev.mlua.modules.advancements;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.advancements.Service.MLuaAdvancementService;
import java.util.List;

public final class AdvancementsModule implements MLuaModule {
    @Override
    public String id() {
        return "advancements";
    }

    @Override
    public List<ServiceEntry> services() {
        return List.of(ServiceEntry.builder("Advancements", MLuaAdvancementService::new)
            .reflective(MLuaAdvancementService.class)
            .build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Advancements", "MLuaAdvancementService",
                "Provides server-side advancement lookup and progress control.",
                "services/advancements.html");
    }
}
