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
        ServiceEntry.Builder advancements = ServiceEntry.builder("Advancements", MLuaAdvancementService::new)
            .description("Provides server-side advancement lookup and progress control.");
        advancements.function("GetAdvancement", "Returns advancement details by identifier, or nil when missing.")
            .function("GetPlayerAdvancements", "Returns a player's completion status and criteria for each advancement.")
            .function("GrantCriteria", "Grants one or more criteria for an advancement to a player.")
            .function("RevokeCriteria", "Revokes one or more criteria for an advancement from a player.")
            .function("GrantAll", "Grants all advancements to a player.")
            .function("RevokeAll", "Revokes all advancements from a player.")
            .function("GetAllAdvancements", "Returns details for all registered advancements.");
        return List.of(advancements.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Advancements", "MLuaAdvancementService",
                "Provides server-side advancement lookup and progress control.",
                "services/advancements.html");
    }
}
