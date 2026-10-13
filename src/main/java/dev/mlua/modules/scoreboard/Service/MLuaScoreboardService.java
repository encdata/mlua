package dev.mlua.modules.scoreboard.Service;

import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import dev.mlua.common.enums.Side;
import dev.mlua.core.service.MLuaService;
import dev.mlua.core.service.MLuaServiceBase;
import dev.mlua.mc.impl.scoreboard.MCPlayerTeamImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mc.impl.core.MCComponentImpl;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import dev.mlua.mc.impl.scoreboard.MCObjectiveImpl;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;

import java.util.function.Function;
//--version 1.21.1,26.1
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.ScoreHolder;
//--endversion

public final class MLuaScoreboardService extends MLuaServiceBase {
    public MLuaScoreboardService(MLuaService.Context context) {
        super(context);
    }

    public String name() {
        return "Scoreboard";
    }

    public LuaTable toLua() {
        LuaTable table = new LuaTable();
        table.set("__side", "Server");

        table.set("GetObjective", fn(args -> {
            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(args.checkjstring(2));
            if (obj == null) {
                return LuaValue.NIL;
            }
            return objectiveTable(sb, obj);
        }));

        table.set("CreateObjective", fn(args -> {
            String name = args.checkjstring(2);
            String criteriaName = args.optjstring(3, "dummy");
            String displayName = args.optjstring(4, name);

            net.minecraft.world.scores.criteria.ObjectiveCriteria criteria = net.minecraft.world.scores.criteria.ObjectiveCriteria.byName(criteriaName).orElse(null);
            if (criteria == null) {
                throw new LuaError("[MLua] Unknown criteria: " + criteriaName);
            }

            net.minecraft.world.scores.Scoreboard rawSb = rawServer().getScoreboard();
            //--version 1.21.1,26.1
            net.minecraft.world.scores.Objective obj = rawSb.addObjective(name, criteria, ((MCComponentImpl) MCComponent.literal(displayName)).inner(), criteria.getDefaultRenderType(), false, BlankFormat.INSTANCE);
            //--endversion
            //--version 1.20.1
            net.minecraft.world.scores.Objective obj = rawSb.addObjective(name, criteria, ((MCComponentImpl) MCComponent.literal(displayName)).inner(), criteria.getDefaultRenderType());
            //--endversion
            return objectiveTable(MCScoreboardImpl.fromReal(rawSb), MCObjectiveImpl.fromReal(obj));
        }));

        table.set("RemoveObjective", fn(args -> {
            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(args.checkjstring(2));
            if (obj != null) {
                rawServer().getScoreboard().removeObjective(((MCObjectiveImpl) obj).inner());
            }
            return LuaValue.NONE;
        }));

        table.set("GetScores", fn(args -> {
            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(args.checkjstring(2));
            if (obj == null) {
                return new LuaTable();
            }

            LuaTable result = new LuaTable();
            int index = 1;
            for (MCScore score : sb.getPlayerScores(obj)) {
                LuaTable entry = new LuaTable();
                entry.set("PlayerName", LuaValue.valueOf(score.getOwner()));
                entry.set("Score", LuaValue.valueOf(score.getScore()));
                result.set(index++, entry);
            }
            return result;
        }));

        table.set("SetScore", fn(args -> {
            String playerName = args.checkjstring(2);
            String objectiveName = args.checkjstring(3);
            int scoreValue = args.checkint(4);

            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(objectiveName);
            if (obj == null) {
                throw new LuaError("[MLua] Objective not found: " + objectiveName);
            }
            //--version 1.21.1,26.1
            rawServer().getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName), ((MCObjectiveImpl) obj).inner()).set(scoreValue);
            //--endversion
            //--version 1.20.1
            rawServer().getScoreboard().getOrCreatePlayerScore(playerName, ((MCObjectiveImpl) obj).inner()).setScore(scoreValue);
            //--endversion
            return LuaValue.NONE;
        }));

        table.set("GetScore", fn(args -> {
            String playerName = args.checkjstring(2);
            String objectiveName = args.checkjstring(3);

            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(objectiveName);
            if (obj == null) {
                return LuaValue.NIL;
            }

            for (MCScore score : sb.getPlayerScores(obj)) {
                if (score.getOwner().equals(playerName)) {
                    return LuaValue.valueOf(score.getScore());
                }
            }
            return LuaValue.NIL;
        }));

        table.set("ResetScore", fn(args -> {
            String playerName = args.checkjstring(2);
            String objectiveName = args.checkjstring(3);

            MCScoreboard sb = scoreboard();
            MCObjective obj = sb.getObjective(objectiveName);
            if (obj != null) {
                //--version 1.21.1,26.1
                rawServer().getScoreboard().resetSinglePlayerScore(ScoreHolder.forNameOnly(playerName), ((MCObjectiveImpl) obj).inner());
                //--endversion
                //--version 1.20.1
                rawServer().getScoreboard().resetPlayerScore(playerName, ((MCObjectiveImpl) obj).inner());
                //--endversion
            }
            return LuaValue.NONE;
        }));

        table.set("CreateTeam", fn(args -> {
            String name = args.checkjstring(2);
            String displayName = args.optjstring(3, name);

            MCScoreboard sb = scoreboard();
            MCPlayerTeam team = sb.addPlayerTeam(name);
            team.setDisplayName(MCComponent.literal(displayName));
            return teamTable(team);
        }));

        table.set("RemoveTeam", fn(args -> {
            MCScoreboard sb = scoreboard();
            MCPlayerTeam team = sb.getPlayerTeam(args.checkjstring(2));
            if (team != null) {
                sb.removePlayerTeam(team);
            }
            return LuaValue.NONE;
        }));

        table.set("GetTeam", fn(args -> {
            MCScoreboard sb = scoreboard();
            MCPlayerTeam team = sb.getPlayerTeam(args.checkjstring(2));
            if (team == null) {
                return LuaValue.NIL;
            }
            return teamTable(team);
        }));

        table.set("AddPlayerToTeam", fn(args -> {
            String playerName = args.checkjstring(2);
            String teamName = args.checkjstring(3);

            MCScoreboard sb = scoreboard();
            MCPlayerTeam team = sb.getPlayerTeam(teamName);
            if (team == null) {
                throw new LuaError("[MLua] Team not found: " + teamName);
            }
            sb.addPlayerToTeam(playerName, team);
            return LuaValue.NONE;
        }));

        table.set("RemovePlayerFromTeam", fn(args -> {
            String playerName = args.checkjstring(2);
            String teamName = args.checkjstring(3);

            MCScoreboard sb = scoreboard();
            MCPlayerTeam team = sb.getPlayerTeam(teamName);
            if (team == null) {
                throw new LuaError("[MLua] Team not found: " + teamName);
            }
            sb.removePlayerFromTeam(playerName, team);
            return LuaValue.NONE;
        }));

        return table;
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
        if (this.context.mlua().side() != Side.SERVER) {
            throw new LuaError("[MLua] Scoreboard service methods are server-side only");
        }
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

    private static VarArgFunction fn(final Function<Varargs, LuaValue> body) {
        return new VarArgFunction() {
            public Varargs invoke(Varargs args) {
                try {
                    return (Varargs) body.apply(args);
                } catch (LuaError e) {
                    throw e;
                } catch (Exception e) {
                    throw new LuaError("[MLua] " + e.getMessage());
                }
            }
        };
    }
}
