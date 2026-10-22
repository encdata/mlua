package dev.mlua.modules.scoreboard.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.mc.impl.scoreboard.MCObjectiveImpl;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import dev.mlua.mcapi.core.MCComponent;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
//--version 1.21.1,26.1
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
//--endversion

@MLuaDescription("Provides server-side scoreboard objective, score, display-slot, and team management. Team snapshots expose formatting and native gameplay rules; mutators return the updated snapshot.")
public final class MLuaScoreboardService extends MLuaServerThreadReflectiveService {
    private final MLuaScoreboardTeamOperations teamOperations;

    public MLuaScoreboardService(MLuaService.Context context) {
        super(context);
        this.teamOperations = new MLuaScoreboardTeamOperations(this);
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
        requireName(name, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(name);
        return objective == null ? LuaValue.NIL : objectiveTable(sb, objective);
    }

    @MLuaDescription("Displays an objective in the list, sidebar, or below-name slot. Pass nil as objectiveName to clear that slot.")
    public void SetDisplaySlot(String slotName, Optional<String> objectiveName) {
        MinecraftServer server = rawServer();
        Scoreboard scoreboard = server.getScoreboard();
        String slot = slotName == null ? "" : slotName.trim().toLowerCase(java.util.Locale.ROOT)
            .replace('-', '_').replace(' ', '_');
        if (slot.equals("belowname")) slot = "below_name";
        if (!slot.equals("list") && !slot.equals("sidebar") && !slot.equals("below_name")) {
            throw new LuaError("[MLua] Scoreboard:SetDisplaySlot slot must be 'list', 'sidebar', or 'below_name'.");
        }
        Objective objective = null;
        if (objectiveName.isPresent()) {
            objective = scoreboard.getObjective(objectiveName.get());
            if (objective == null) throw new LuaError("[MLua] Objective not found: " + objectiveName.get());
        }
        //--version 1.21.1,26.1
        DisplaySlot displaySlot = switch (slot) {
            case "list" -> DisplaySlot.LIST;
            case "sidebar" -> DisplaySlot.SIDEBAR;
            default -> DisplaySlot.BELOW_NAME;
        };
        scoreboard.setDisplayObjective(displaySlot, objective);
        //--endversion
        //--version 1.20.1
        int displaySlot = switch (slot) {
            case "list" -> Scoreboard.DISPLAY_SLOT_LIST;
            case "sidebar" -> Scoreboard.DISPLAY_SLOT_SIDEBAR;
            default -> Scoreboard.DISPLAY_SLOT_BELOW_NAME;
        };
        scoreboard.setDisplayObjective(displaySlot, objective);
        //--endversion
    }

    @MLuaDescription("Creates a scoreboard objective and returns its typed ScoreboardObjective snapshot.")
    public LuaTable CreateObjective(String name, Optional<String> criteriaValue, Optional<String> displayNameValue) {
        requireName(name, "objective");
        String criteriaName = criteriaValue.orElse("dummy");
        String displayName = displayNameValue.orElse(name);
        ObjectiveCriteria criteria =
            ObjectiveCriteria.byName(criteriaName).orElse(null);
        if (criteria == null) throw new LuaError("[MLua] Unknown criteria: " + criteriaName);
        Scoreboard rawScoreboard = rawServer().getScoreboard();
        if (rawScoreboard.getObjective(name) != null) {
            throw new LuaError("[MLua] Scoreboard objective already exists: " + name);
        }
        //--version 1.21.1,26.1
        Objective objective = rawScoreboard.addObjective(name, criteria,
            Component.literal(displayName), criteria.getDefaultRenderType(), false, BlankFormat.INSTANCE);
        //--endversion
        //--version 1.20.1
        Objective objective = rawScoreboard.addObjective(name, criteria,
            Component.literal(displayName), criteria.getDefaultRenderType());
        //--endversion
        return objectiveTable(MCScoreboardImpl.fromReal(rawScoreboard), MCObjectiveImpl.fromReal(objective));
    }

    @MLuaDescription("Removes a scoreboard objective by name.")
    public LuaValue RemoveObjective(String name) {
        requireName(name, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(name);
        if (objective != null) rawServer().getScoreboard().removeObjective(((MCObjectiveImpl) objective).inner());
        return LuaValue.NONE;
    }

    @MLuaDescription("Returns the objective's scores as ScoreboardScore instances in stable holder-name order.")
    public LuaTable GetScores(String objectiveName) {
        requireName(objectiveName, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        LuaTable result = new LuaTable();
        if (objective == null) return result;
        var scores = new java.util.ArrayList<>(sb.getPlayerScores(objective));
        scores.sort(Comparator.comparing(MCScore::getOwner));
        int index = 1;
        for (MCScore score : scores) {
            LuaTable entry = this.context.mlua().registration().createClassInstance("ScoreboardScore");
            entry.rawset("PlayerName", LuaValue.valueOf(score.getOwner()));
            entry.rawset("ObjectiveName", LuaValue.valueOf(objectiveName));
            entry.rawset("Score", LuaValue.valueOf(score.getScore()));
            entry.freeze();
            result.set(index++, entry);
        }
        return result;
    }

    @MLuaDescription("Sets a player's score for an objective.")
    public LuaValue SetScore(String playerName, String objectiveName, int scoreValue) {
        requireName(playerName, "score holder");
        requireName(objectiveName, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective == null) throw new LuaError("[MLua] Objective not found: " + objectiveName);
        //--version 1.21.1,26.1
        rawServer().getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName),
            ((MCObjectiveImpl) objective).inner()).set(scoreValue);
        //--endversion
        //--version 1.20.1
        rawServer().getScoreboard().getOrCreatePlayerScore(playerName,
            ((MCObjectiveImpl) objective).inner()).setScore(scoreValue);
        //--endversion
        return LuaValue.NONE;
    }

    @MLuaDescription("Returns a player's score for an objective.")
    public LuaValue GetScore(String playerName, String objectiveName) {
        requireName(playerName, "score holder");
        requireName(objectiveName, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective == null) return LuaValue.NIL;
        for (MCScore score : sb.getPlayerScores(objective)) {
            if (score.getOwner().equals(playerName)) return LuaValue.valueOf(score.getScore());
        }
        return LuaValue.NIL;
    }

    @MLuaDescription("Removes a player's score from an objective.")
    public LuaValue ResetScore(String playerName, String objectiveName) {
        requireName(playerName, "score holder");
        requireName(objectiveName, "objective");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective != null) {
            //--version 1.21.1,26.1
            rawServer().getScoreboard().resetSinglePlayerScore(ScoreHolder.forNameOnly(playerName),
                ((MCObjectiveImpl) objective).inner());
            //--endversion
            //--version 1.20.1
            rawServer().getScoreboard().resetPlayerScore(playerName, ((MCObjectiveImpl) objective).inner());
            //--endversion
        }
        return LuaValue.NONE;
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
        MCScoreboard sb = scoreboard();
        LuaTable result = new LuaTable();
        var objectives = new java.util.ArrayList<>(sb.getObjectives());
        objectives.sort(Comparator.comparing(MCObjective::getName));
        for (int index = 0; index < objectives.size(); index++) {
            result.set(index + 1, objectiveTable(sb, objectives.get(index)));
        }
        return result;
    }

    @MLuaDescription("Returns all teams as ScoreboardTeam instances in stable name order.")
    public LuaTable GetTeams() {
        return this.teamOperations.getTeams();
    }

    @MLuaDescription("Updates an objective display name and returns its refreshed ScoreboardObjective snapshot.")
    public LuaValue SetObjectiveDisplayName(String name, String displayName) {
        requireName(name, "objective");
        if (displayName == null) throw new LuaError("[MLua] Scoreboard display name must not be nil.");
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(name);
        if (objective == null) throw new LuaError("[MLua] Objective not found: " + name);
        objective.setDisplayName(MCComponent.literal(displayName));
        return objectiveTable(sb, objective);
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

    private LuaTable objectiveTable(MCScoreboard scoreboard, MCObjective obj) {
        LuaTable table = this.context.mlua().registration().createClassInstance("ScoreboardObjective");
        table.rawset("Name", LuaValue.valueOf(obj.getName()));
        table.rawset("DisplayName", LuaValue.valueOf(obj.getDisplayName().getString()));
        table.rawset("Criteria", LuaValue.valueOf(obj.getCriteria().getName()));

        LuaTable slots = new LuaTable();
        int slotIndex = 1;
        Scoreboard rawSb = ((MCScoreboardImpl) scoreboard).inner();
        Objective rawObj = ((MCObjectiveImpl) obj).inner();
        //--version 1.21.1,26.1
        DisplaySlot[] displaySlots = {
            DisplaySlot.LIST,
            DisplaySlot.SIDEBAR,
            DisplaySlot.BELOW_NAME
        };
        for (DisplaySlot slot : displaySlots) {
            if (rawSb.getDisplayObjective(slot) == rawObj) {
                slots.set(slotIndex++, LuaValue.valueOf(slot.getSerializedName()));
            }
        }
        //--endversion
        //--version 1.20.1
        int[] displaySlots20 = {
            Scoreboard.DISPLAY_SLOT_LIST,
            Scoreboard.DISPLAY_SLOT_SIDEBAR,
            Scoreboard.DISPLAY_SLOT_BELOW_NAME
        };
        for (int slot : displaySlots20) {
            if (rawSb.getDisplayObjective(slot) == rawObj) {
                slots.set(slotIndex++, LuaValue.valueOf(Scoreboard.getDisplaySlotName(slot)));
            }
        }
        //--endversion
        table.rawset("Slots", slots);

        table.freeze();
        return table;
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
