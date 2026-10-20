package dev.mlua.modules.scoreboard.service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServerThreadReflectiveService;
import dev.mlua.mc.impl.scoreboard.MCPlayerTeamImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import dev.mlua.mc.impl.scoreboard.MCObjectiveImpl;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;

import java.util.Optional;
import java.util.Comparator;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.PlayerTeam;
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
    public MLuaScoreboardService(MLuaService.Context context) {
        super(context);
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
        requireName(name, "team");
        if (scoreboard().getPlayerTeam(name) != null) {
            throw new LuaError("[MLua] Scoreboard team already exists: " + name);
        }
        MCPlayerTeam team = scoreboard().addPlayerTeam(name);
        team.setDisplayName(MCComponent.literal(displayNameValue.orElse(name)));
        return teamTable(team);
    }

    @MLuaDescription("Removes a scoreboard team by name.")
    public LuaValue RemoveTeam(String name) {
        requireName(name, "team");
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(name);
        if (team != null) sb.removePlayerTeam(team);
        return LuaValue.NONE;
    }

    @MLuaDescription("Returns a typed ScoreboardTeam snapshot by name, or nil when it is missing.")
    public LuaValue GetTeam(String name) {
        requireName(name, "team");
        MCPlayerTeam team = scoreboard().getPlayerTeam(name);
        return team == null ? LuaValue.NIL : teamTable(team);
    }

    @MLuaDescription("Adds a player to a scoreboard team.")
    public LuaValue AddPlayerToTeam(String playerName, String teamName) {
        requireName(playerName, "score holder");
        requireName(teamName, "team");
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.addPlayerToTeam(playerName, team);
        return LuaValue.NONE;
    }

    @MLuaDescription("Removes a player from a scoreboard team.")
    public LuaValue RemovePlayerFromTeam(String playerName, String teamName) {
        requireName(playerName, "score holder");
        requireName(teamName, "team");
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.removePlayerFromTeam(playerName, team);
        return LuaValue.NONE;
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
        LuaTable result = new LuaTable();
        var teams = new java.util.ArrayList<>(scoreboard().getPlayerTeams());
        teams.sort(Comparator.comparing(MCPlayerTeam::getName));
        for (int index = 0; index < teams.size(); index++) {
            result.set(index + 1, teamTable(teams.get(index)));
        }
        return result;
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
        requireName(name, "team");
        MCPlayerTeam team = scoreboard().getPlayerTeam(name);
        if (team == null) throw new LuaError("[MLua] Team not found: " + name);
        displayName.ifPresent(value -> team.setDisplayName(MCComponent.literal(value)));
        prefix.ifPresent(value -> team.setPrefix(MCComponent.literal(value)));
        suffix.ifPresent(value -> team.setSuffix(MCComponent.literal(value)));
        return teamTable(team);
    }

    @MLuaDescription("Sets whether team members can damage each other and returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamFriendlyFire(String name, boolean enabled) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setAllowFriendlyFire(enabled);
        return teamTable(team);
    }

    @MLuaDescription("Sets whether team members can see invisible teammates and returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamSeeFriendlyInvisibles(String name, boolean enabled) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setSeeFriendlyInvisibles(enabled);
        return teamTable(team);
    }

    @MLuaDescription("Sets team name-tag visibility: always, never, hide_for_other_teams, or hide_for_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamNameTagVisibility(String name, String visibility) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setNameTagVisibility(parseVisibility(visibility, "name tag"));
        return teamTable(team);
    }

    @MLuaDescription("Sets team death-message visibility: always, never, hide_for_other_teams, or hide_for_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamDeathMessageVisibility(String name, String visibility) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setDeathMessageVisibility(parseVisibility(visibility, "death message"));
        return teamTable(team);
    }

    @MLuaDescription("Sets team collision: always, never, push_other_teams, or push_own_team; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamCollisionRule(String name, String rule) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setCollisionRule(parseCollisionRule(rule));
        return teamTable(team);
    }

    @MLuaDescription("Sets the team's formatting color by Minecraft color name, such as red or dark_green. Use reset to clear the team color; returns the refreshed ScoreboardTeam snapshot.")
    public LuaTable SetTeamColor(String name, String colorName) {
        MCPlayerTeam team = requireTeam(name);
        String normalized = normalizeEnumName(colorName, "team color");
        ChatFormatting color = ChatFormatting.getByName(normalized);
        if (color == null || (color != ChatFormatting.RESET && !color.isColor())) {
            throw new LuaError("[MLua] Unknown Minecraft team color: " + colorName);
        }
        rawTeam(team).setColor(color == ChatFormatting.RESET ? null : color);
        return teamTable(team);
    }

    private MCPlayerTeam requireTeam(String name) {
        requireName(name, "team");
        MCPlayerTeam team = scoreboard().getPlayerTeam(name);
        if (team == null) throw new LuaError("[MLua] Team not found: " + name);
        return team;
    }

    private static PlayerTeam rawTeam(MCPlayerTeam team) {
        if (team instanceof MCPlayerTeamImpl implementation) return implementation.inner();
        throw new LuaError("[MLua] Team is not backed by a live Minecraft scoreboard team.");
    }

    private static PlayerTeam.Visibility parseVisibility(String value, String kind) {
        String normalized = normalizeEnumName(value, kind + " visibility");
        try {
            return PlayerTeam.Visibility.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] Invalid " + kind + " visibility '" + value
                + "'. Expected always, never, hide_for_other_teams, or hide_for_own_team.");
        }
    }

    private static PlayerTeam.CollisionRule parseCollisionRule(String value) {
        String normalized = normalizeEnumName(value, "collision rule");
        try {
            return PlayerTeam.CollisionRule.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] Invalid team collision rule '" + value
                + "'. Expected always, never, push_other_teams, or push_own_team.");
        }
    }

    private static String normalizeEnumName(String value, String kind) {
        if (value == null || value.isBlank()) throw new LuaError("[MLua] " + kind + " must be a non-empty string.");
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static void requireName(String value, String kind) {
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

    private LuaTable teamTable(MCPlayerTeam team) {
        LuaTable table = this.context.mlua().registration().createClassInstance("ScoreboardTeam");
        table.rawset("Name", LuaValue.valueOf(team.getName()));
        table.rawset("DisplayName", LuaValue.valueOf(team.getDisplayName().getString()));

        var color = ((MCPlayerTeamImpl)team).inner().getColor();
        table.rawset("Color", LuaValue.valueOf(color != null ? color.getName() : ""));

        table.rawset("Prefix", LuaValue.valueOf(((MCPlayerTeamImpl)team).inner().getPlayerPrefix().getString()));
        table.rawset("Suffix", LuaValue.valueOf(((MCPlayerTeamImpl)team).inner().getPlayerSuffix().getString()));
        table.rawset("AllowFriendlyFire", LuaValue.valueOf(team.isAllowFriendlyFire()));
        table.rawset("SeeFriendlyInvisibles", LuaValue.valueOf(team.isSeeFriendlyInvisibles()));
        table.rawset("NameTagVisibility", LuaValue.valueOf(team.getNameTagVisibility().name().toLowerCase(Locale.ROOT)));
        table.rawset("DeathMessageVisibility", LuaValue.valueOf(team.getDeathMessageVisibility().name().toLowerCase(Locale.ROOT)));
        table.rawset("CollisionRule", LuaValue.valueOf(team.getCollisionRule().name().toLowerCase(Locale.ROOT)));

        LuaTable members = new LuaTable();
        int index = 1;
        var sortedMembers = new java.util.ArrayList<>(team.getPlayers());
        sortedMembers.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        for (String player : sortedMembers) {
            members.set(index++, LuaValue.valueOf(player));
        }
        table.rawset("Members", members);

        table.freeze();
        return table;
    }

    private MinecraftServer rawServer() {
        this.requireServerSide(this.name());
        MinecraftServer server = this.context.mlua().server();
        if (server == null) {
            throw new LuaError("[MLua] Scoreboard requires an active server");
        }
        return server;
    }

    private MCScoreboard scoreboard() {
        return MCScoreboardImpl.fromReal(rawServer().getScoreboard());
    }

}
