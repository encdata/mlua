package dev.mlua.modules.scoreboard.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import dev.mlua.common.enums.Side;
import dev.mlua.api.MLuaDescription;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaReflectiveService;
import dev.mlua.mc.impl.scoreboard.MCPlayerTeamImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import dev.mlua.mc.impl.scoreboard.MCObjectiveImpl;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;

import java.util.Optional;
//--version 1.21.1,26.1
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
//--endversion

@MLuaDescription("Provides server-side objective, team, and score management.")
public final class MLuaScoreboardService extends MLuaReflectiveService {
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

    @MLuaDescription("Returns a scoreboard objective by name.")
    public LuaValue GetObjective(String name) {
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(name);
        return objective == null ? LuaValue.NIL : objectiveTable(sb, objective);
    }

    @MLuaDescription("Creates a scoreboard objective with a display name.")
    public LuaTable CreateObjective(String name, Optional<String> criteriaValue, Optional<String> displayNameValue) {
        String criteriaName = criteriaValue.orElse("dummy");
        String displayName = displayNameValue.orElse(name);
        net.minecraft.world.scores.criteria.ObjectiveCriteria criteria =
            net.minecraft.world.scores.criteria.ObjectiveCriteria.byName(criteriaName).orElse(null);
        if (criteria == null) throw new LuaError("[MLua] Unknown criteria: " + criteriaName);
        net.minecraft.world.scores.Scoreboard rawScoreboard = rawServer().getScoreboard();
        //--version 1.21.1,26.1
        net.minecraft.world.scores.Objective objective = rawScoreboard.addObjective(name, criteria,
            ((MCComponentImpl) MCComponent.literal(displayName)).inner(), criteria.getDefaultRenderType(), false, BlankFormat.INSTANCE);
        //--endversion
        //--version 1.20.1
        net.minecraft.world.scores.Objective objective = rawScoreboard.addObjective(name, criteria,
            ((MCComponentImpl) MCComponent.literal(displayName)).inner(), criteria.getDefaultRenderType());
        //--endversion
        return objectiveTable(MCScoreboardImpl.fromReal(rawScoreboard), MCObjectiveImpl.fromReal(objective));
    }

    @MLuaDescription("Removes a scoreboard objective by name.")
    public LuaValue RemoveObjective(String name) {
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(name);
        if (objective != null) rawServer().getScoreboard().removeObjective(((MCObjectiveImpl) objective).inner());
        return LuaValue.NONE;
    }

    @MLuaDescription("Returns the scores recorded for an objective.")
    public LuaTable GetScores(String objectiveName) {
        MCScoreboard sb = scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        LuaTable result = new LuaTable();
        if (objective == null) return result;
        int index = 1;
        for (MCScore score : sb.getPlayerScores(objective)) {
            LuaTable entry = new LuaTable();
            entry.set("PlayerName", score.getOwner());
            entry.set("Score", score.getScore());
            result.set(index++, entry);
        }
        return result;
    }

    @MLuaDescription("Sets a player's score for an objective.")
    public LuaValue SetScore(String playerName, String objectiveName, int scoreValue) {
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

    @MLuaDescription("Creates a scoreboard team.")
    public LuaTable CreateTeam(String name, Optional<String> displayNameValue) {
        MCPlayerTeam team = scoreboard().addPlayerTeam(name);
        team.setDisplayName(MCComponent.literal(displayNameValue.orElse(name)));
        return teamTable(team);
    }

    @MLuaDescription("Removes a scoreboard team by name.")
    public LuaValue RemoveTeam(String name) {
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(name);
        if (team != null) sb.removePlayerTeam(team);
        return LuaValue.NONE;
    }

    @MLuaDescription("Returns a scoreboard team by name.")
    public LuaValue GetTeam(String name) {
        MCPlayerTeam team = scoreboard().getPlayerTeam(name);
        return team == null ? LuaValue.NIL : teamTable(team);
    }

    @MLuaDescription("Adds a player to a scoreboard team.")
    public LuaValue AddPlayerToTeam(String playerName, String teamName) {
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.addPlayerToTeam(playerName, team);
        return LuaValue.NONE;
    }

    @MLuaDescription("Removes a player from a scoreboard team.")
    public LuaValue RemovePlayerFromTeam(String playerName, String teamName) {
        MCScoreboard sb = scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.removePlayerFromTeam(playerName, team);
        return LuaValue.NONE;
    }

    private LuaTable objectiveTable(MCScoreboard scoreboard, MCObjective obj) {
        LuaTable table = new LuaTable();
        table.set("Name", LuaValue.valueOf(obj.getName()));
        table.set("DisplayName", LuaValue.valueOf(obj.getDisplayName().getString()));
        table.set("Criteria", LuaValue.valueOf(obj.getCriteria().getName()));

        LuaTable slots = new LuaTable();
        int slotIndex = 1;
        net.minecraft.world.scores.Scoreboard rawSb = ((MCScoreboardImpl) scoreboard).inner();
        net.minecraft.world.scores.Objective rawObj = ((MCObjectiveImpl) obj).inner();
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
            net.minecraft.world.scores.Scoreboard.DISPLAY_SLOT_LIST,
            net.minecraft.world.scores.Scoreboard.DISPLAY_SLOT_SIDEBAR,
            net.minecraft.world.scores.Scoreboard.DISPLAY_SLOT_BELOW_NAME
        };
        for (int slot : displaySlots20) {
            if (rawSb.getDisplayObjective(slot) == rawObj) {
                slots.set(slotIndex++, LuaValue.valueOf(net.minecraft.world.scores.Scoreboard.getDisplaySlotName(slot)));
            }
        }
        //--endversion
        table.set("Slots", slots);

        return table;
    }

    private LuaTable teamTable(MCPlayerTeam team) {
        LuaTable table = new LuaTable();
        table.set("Name", LuaValue.valueOf(team.getName()));
        table.set("DisplayName", LuaValue.valueOf(team.getDisplayName().getString()));

        var color = ((MCPlayerTeamImpl)team).inner().getColor();
        table.set("Color", LuaValue.valueOf(color != null ? color.getName() : ""));

        table.set("Prefix", LuaValue.valueOf(((MCPlayerTeamImpl)team).inner().getPlayerPrefix().getString()));
        table.set("Suffix", LuaValue.valueOf(((MCPlayerTeamImpl)team).inner().getPlayerSuffix().getString()));

        LuaTable members = new LuaTable();
        int index = 1;
        for (String player : team.getPlayers()) {
            members.set(index++, LuaValue.valueOf(player));
        }
        table.set("Members", members);

        return table;
    }

    private void requireServer() {
        this.requireServerSide(this.name());
    }

    private net.minecraft.server.MinecraftServer rawServer() {
        requireServer();
        net.minecraft.server.MinecraftServer server = this.context.mlua().server();
        if (server == null) {
            throw new LuaError("[MLua] Scoreboard requires an active server");
        }
        return server;
    }

    private MCScoreboard scoreboard() {
        return MCScoreboardImpl.fromReal(rawServer().getScoreboard());
    }

}
