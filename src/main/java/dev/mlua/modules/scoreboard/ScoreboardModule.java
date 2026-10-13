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
            .description("Provides server-side objective, team, and score management.");
        scoreboard.function("GetObjective", "Returns a scoreboard objective by name.")
            .function("CreateObjective", "Creates a scoreboard objective with a display name.")
            .function("RemoveObjective", "Removes a scoreboard objective by name.")
            .function("GetScores", "Returns the scores recorded for an objective.")
            .function("SetScore", "Sets a player's score for an objective.")
            .function("GetScore", "Returns a player's score for an objective.")
            .function("ResetScore", "Removes a player's score from an objective.")
            .function("CreateTeam", "Creates a scoreboard team.")
            .function("RemoveTeam", "Removes a scoreboard team by name.")
            .function("GetTeam", "Returns a scoreboard team by name.")
            .function("AddPlayerToTeam", "Adds a player to a scoreboard team.")
            .function("RemovePlayerFromTeam", "Removes a player from a scoreboard team.");
        return List.of(scoreboard.build());
    }

    @Override
    public void contributeDocs(MLuaDocBuilder docs) {
        docs.service("Scoreboard", "MLuaScoreboardService",
                "Provides server-side objective, team, and score management.",
                "services/scoreboard.html");
    }
}
