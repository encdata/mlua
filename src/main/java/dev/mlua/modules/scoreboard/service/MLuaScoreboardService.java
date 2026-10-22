package dev.mlua.modules.scoreboard.service;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

@MLuaDescription("Provides server-side scoreboard objective, score, display-slot, and team management. Team snapshots expose formatting and native gameplay rules; mutators return the updated snapshot.")
public final class MLuaScoreboardService extends MLuaServerThreadReflectiveService {
    private final MLuaScoreboardTeamOperations teamOperations;
    private final MLuaScoreboardObjectiveOperations objectiveOperations;

    public MLuaScoreboardService(MLuaService.Context context) {
        super(context);
        this.teamOperations = new MLuaScoreboardTeamOperations(this);
        this.objectiveOperations = new MLuaScoreboardObjectiveOperations(context, this);
    }

    @Override
    public String name() {
        return "Scoreboard";
    }

    @Override
    public LuaTable toLua() {
        LuaTable table = super.toLua();
        table.set("__side", "Server");
        return table;
    }

    @MLuaDescription("Returns a typed ScoreboardObjective snapshot by name, or nil when it is missing.")
    public LuaValue GetObjective(String name) {
        return this.objectiveOperations.getObjective(name);
    }

    @MLuaDescription("Displays an objective in the list, sidebar, or below-name slot. Pass nil as objectiveName to clear that slot.")
    public void SetDisplaySlot(String slotName, Optional<String> objectiveName) {
        this.objectiveOperations.setDisplaySlot(slotName, objectiveName);
    }

    @MLuaDescription("Creates a scoreboard objective and returns its typed ScoreboardObjective snapshot.")
    public LuaTable CreateObjective(String name, Optional<String> criteriaValue, Optional<String> displayNameValue) {
        return this.objectiveOperations.createObjective(name, criteriaValue, displayNameValue);
    }

    @MLuaDescription("Removes a scoreboard objective by name.")
    public LuaValue RemoveObjective(String name) {
        return this.objectiveOperations.removeObjective(name);
    }

    @MLuaDescription("Returns the objective's scores as ScoreboardScore instances in stable holder-name order.")
    public LuaTable GetScores(String objectiveName) {
        return this.objectiveOperations.getScores(objectiveName);
    }

    @MLuaDescription("Sets a player's score for an objective.")
    public LuaValue SetScore(String playerName, String objectiveName, int scoreValue) {
        return this.objectiveOperations.setScore(playerName, objectiveName, scoreValue);
    }

    @MLuaDescription("Returns a player's score for an objective.")
    public LuaValue GetScore(String playerName, String objectiveName) {
        return this.objectiveOperations.getScore(playerName, objectiveName);
    }

    @MLuaDescription("Removes a player's score from an objective.")
    public LuaValue ResetScore(String playerName, String objectiveName) {
        return this.objectiveOperations.resetScore(playerName, objectiveName);
    }

    @MLuaDescription("Creates a scoreboard team and returns its typed ScoreboardTeam snapshot.")
    public LuaTable CreateTeam(String name, Optional<String> displayNameValue) {
        return this.teamOperations.createTeam(name, displayNameValue);
    }

    @MLuaDescription("Removes a scoreboard team by name.")
    public LuaValue RemoveTeam(String name) {
        return this.teamOperations.removeTeam(name);
    }

    @MLuaDescription("Returns a typed ScoreboardTeam snapshot by name, or nil when it is missing.")
    public LuaValue GetTeam(String name) {
        return this.teamOperations.getTeam(name);
    }

    @MLuaDescription("Adds a player to a scoreboard team.")
    public LuaValue AddPlayerToTeam(String playerName, String teamName) {
        return this.teamOperations.addPlayerToTeam(playerName, teamName);
    }

    @MLuaDescription("Removes a player from a scoreboard team.")
    public LuaValue RemovePlayerFromTeam(String playerName, String teamName) {
        return this.teamOperations.removePlayerFromTeam(playerName, teamName);
    }

    @MLuaDescription("Returns all objectives as ScoreboardObjective instances in stable name order.")
    public LuaTable GetObjectives() {
        return this.objectiveOperations.getObjectives();
    }

    @MLuaDescription("Returns all teams as ScoreboardTeam instances in stable name order.")
    public LuaTable GetTeams() {
        return this.teamOperations.getTeams();
    }

    @MLuaDescription("Updates an objective display name and returns its refreshed ScoreboardObjective snapshot.")
    public LuaValue SetObjectiveDisplayName(String name, String displayName) {
        return this.objectiveOperations.setObjectiveDisplayName(name, displayName);
    }

    @MLuaDescription("Sets a team's display name, prefix, and suffix. Omitted values are left unchanged; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable ConfigureTeam(String name, Optional<String> displayName, Optional<String> prefix,
                                  Optional<String> suffix) {
        return this.teamOperations.configureTeam(name, displayName, prefix, suffix);
    }

    @MLuaDescription("Sets whether team members can damage each other and returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamFriendlyFire(String name, boolean enabled) {
        return this.teamOperations.setFriendlyFire(name, enabled);
    }

    @MLuaDescription("Sets whether team members can see invisible teammates and returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamSeeFriendlyInvisibles(String name, boolean enabled) {
        return this.teamOperations.setSeeFriendlyInvisibles(name, enabled);
    }

    @MLuaDescription("Sets team name-tag visibility: always, never, hide_for_other_teams, or hide_for_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamNameTagVisibility(String name, String visibility) {
        return this.teamOperations.setNameTagVisibility(name, visibility);
    }

    @MLuaDescription("Sets team death-message visibility: always, never, hide_for_other_teams, or hide_for_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamDeathMessageVisibility(String name, String visibility) {
        return this.teamOperations.setDeathMessageVisibility(name, visibility);
    }

    @MLuaDescription("Sets team collision: always, never, push_other_teams, or push_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamCollisionRule(String name, String rule) {
        return this.teamOperations.setCollisionRule(name, rule);
    }

    @MLuaDescription("Sets the team's formatting color by Minecraft color name, such as red or dark_green. Use reset to clear the team color; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamColor(String name, String colorName) {
        return this.teamOperations.setColor(name, colorName);
    }

    static void requireName(String value, String kind) {
        if (value == null || value.isBlank()) {
            throw new LuaError("[MLua] Scoreboard " + kind + " name must not be empty.");
        }
    }



    MinecraftServer rawServer() {
        this.requireServerSide(this.name());
        MinecraftServer server = this.context.mlua().server();
        if (server == null) {
            throw new LuaError("[MLua] Scoreboard requires an active server");
        }
        return server;
    }

    LuaTable createTeamInstance() {
        return this.context.mlua().registration().createClassInstance("ScoreboardTeam");
    }

    MCScoreboard scoreboard() {
        return MCScoreboardImpl.fromReal(rawServer().getScoreboard());
    }

}
