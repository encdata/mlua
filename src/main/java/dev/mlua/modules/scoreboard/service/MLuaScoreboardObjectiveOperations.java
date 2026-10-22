package dev.mlua.modules.scoreboard.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;

import dev.mlua.core.service.MLuaService;
import dev.mlua.mc.impl.scoreboard.MCObjectiveImpl;
import dev.mlua.mc.impl.scoreboard.MCScoreboardImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
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

/** Owns scoreboard objective, score, and display-slot behavior and typed snapshots. */
final class MLuaScoreboardObjectiveOperations {
    private final MLuaService.Context context;
    private final MLuaScoreboardService service;

    MLuaScoreboardObjectiveOperations(MLuaService.Context context, MLuaScoreboardService service) {
        this.context = context;
        this.service = service;
    }

    LuaValue getObjective(String name) {
        MLuaScoreboardService.requireName(name, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(name);
        return objective == null ? LuaValue.NIL : objectiveTable(sb, objective);
    }

    void setDisplaySlot(String slotName, Optional<String> objectiveName) {
        MinecraftServer server = this.service.rawServer();
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

    LuaTable createObjective(String name, Optional<String> criteriaValue, Optional<String> displayNameValue) {
        MLuaScoreboardService.requireName(name, "objective");
        String criteriaName = criteriaValue.orElse("dummy");
        String displayName = displayNameValue.orElse(name);
        ObjectiveCriteria criteria =
            ObjectiveCriteria.byName(criteriaName).orElse(null);
        if (criteria == null) throw new LuaError("[MLua] Unknown criteria: " + criteriaName);
        Scoreboard rawScoreboard = this.service.rawServer().getScoreboard();
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

    LuaValue removeObjective(String name) {
        MLuaScoreboardService.requireName(name, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(name);
        if (objective != null) this.service.rawServer().getScoreboard().removeObjective(((MCObjectiveImpl) objective).inner());
        return LuaValue.NONE;
    }

    LuaTable getScores(String objectiveName) {
        MLuaScoreboardService.requireName(objectiveName, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        LuaTable result = new LuaTable();
        if (objective == null) return result;
        var scores = new java.util.ArrayList<>(sb.getPlayerScores(objective));
        scores.sort(Comparator.comparing(MCScore::getOwner));
        int index = 1;
        for (MCScore score : scores) {
            LuaTable entry = this.context.registration().createClassInstance("ScoreboardScore");
            entry.rawset("PlayerName", LuaValue.valueOf(score.getOwner()));
            entry.rawset("ObjectiveName", LuaValue.valueOf(objectiveName));
            entry.rawset("Score", LuaValue.valueOf(score.getScore()));
            entry.freeze();
            result.set(index++, entry);
        }
        return result;
    }

    LuaValue setScore(String playerName, String objectiveName, int scoreValue) {
        MLuaScoreboardService.requireName(playerName, "score holder");
        MLuaScoreboardService.requireName(objectiveName, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective == null) throw new LuaError("[MLua] Objective not found: " + objectiveName);
        //--version 1.21.1,26.1
        this.service.rawServer().getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(playerName),
            ((MCObjectiveImpl) objective).inner()).set(scoreValue);
        //--endversion
        //--version 1.20.1
        this.service.rawServer().getScoreboard().getOrCreatePlayerScore(playerName,
            ((MCObjectiveImpl) objective).inner()).setScore(scoreValue);
        //--endversion
        return LuaValue.NONE;
    }

    LuaValue getScore(String playerName, String objectiveName) {
        MLuaScoreboardService.requireName(playerName, "score holder");
        MLuaScoreboardService.requireName(objectiveName, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective == null) return LuaValue.NIL;
        for (MCScore score : sb.getPlayerScores(objective)) {
            if (score.getOwner().equals(playerName)) return LuaValue.valueOf(score.getScore());
        }
        return LuaValue.NIL;
    }

    LuaValue resetScore(String playerName, String objectiveName) {
        MLuaScoreboardService.requireName(playerName, "score holder");
        MLuaScoreboardService.requireName(objectiveName, "objective");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(objectiveName);
        if (objective != null) {
            //--version 1.21.1,26.1
            this.service.rawServer().getScoreboard().resetSinglePlayerScore(ScoreHolder.forNameOnly(playerName),
                ((MCObjectiveImpl) objective).inner());
            //--endversion
            //--version 1.20.1
            this.service.rawServer().getScoreboard().resetPlayerScore(playerName, ((MCObjectiveImpl) objective).inner());
            //--endversion
        }
        return LuaValue.NONE;
    }

    LuaTable getObjectives() {
        MCScoreboard sb = this.service.scoreboard();
        LuaTable result = new LuaTable();
        var objectives = new ArrayList<>(sb.getObjectives());
        objectives.sort(Comparator.comparing(MCObjective::getName));
        for (int index = 0; index < objectives.size(); index++) {
            result.set(index + 1, objectiveTable(sb, objectives.get(index)));
        }
        return result;
    }

    LuaValue setObjectiveDisplayName(String name, String displayName) {
        MLuaScoreboardService.requireName(name, "objective");
        if (displayName == null) throw new LuaError("[MLua] Scoreboard display name must not be nil.");
        MCScoreboard sb = this.service.scoreboard();
        MCObjective objective = sb.getObjective(name);
        if (objective == null) throw new LuaError("[MLua] Objective not found: " + name);
        objective.setDisplayName(MCComponent.literal(displayName));
        return objectiveTable(sb, objective);
    }

    LuaTable objectiveTable(MCScoreboard scoreboard, MCObjective obj) {
        LuaTable table = this.context.registration().createClassInstance("ScoreboardObjective");
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
}
