package dev.mlua.modules.scoreboard;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.modules.scoreboard.Service.MLuaScoreboardService;
import java.util.List;

public final class ScoreboardModule implements MLuaModule {
    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public List<ServiceEntry> services() {
        ServiceEntry.Builder scoreboard = ServiceEntry.builder("Scoreboard", MLuaScoreboardService::new)
            .reflective(MLuaScoreboardService.class);
        return List.of(scoreboard.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.register(MLuaDocBuilder.Registration.service("Scoreboard", "MLuaScoreboardService",
                "Provides server-side objective, team, and score management.",
                "services/scoreboard.html"));
    }
}
