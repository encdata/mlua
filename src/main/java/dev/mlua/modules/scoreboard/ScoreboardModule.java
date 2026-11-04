package dev.mlua.modules.scoreboard;

import dev.mlua.module.MLuaDocBuilder;
import dev.mlua.module.MLuaModule;
import dev.mlua.module.MLuaRegistration;
import dev.mlua.modules.scoreboard.service.MLuaScoreboardService;
import java.util.List;

public final class ScoreboardModule implements MLuaModule {
    @Override
    public String id() {
        return "scoreboard";
    }

    @Override
    public List<String> dependencies() { return List.of("core-classes"); }

    @Override
    public void registerClasses(MLuaRegistration registration) {
        ScoreboardClassBindings.register(registration);
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
                "Manages server-side objectives, scores, display slots, and teams. Lookup and mutation results are typed ScoreboardObjective, ScoreboardScore, or ScoreboardTeam snapshots; team snapshots include color, prefixes, membership, friendly-fire rules, visibility, and collision.",
                "services/scoreboard.html"));
    }
}
