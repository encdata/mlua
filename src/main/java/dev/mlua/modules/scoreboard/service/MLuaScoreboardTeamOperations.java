package dev.mlua.modules.scoreboard.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;

import dev.mlua.mc.impl.scoreboard.MCPlayerTeamImpl;
import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

/** Owns scoreboard team mutation, validation, and stable Lua snapshots. */
final class MLuaScoreboardTeamOperations {
    private final MLuaScoreboardService service;

    MLuaScoreboardTeamOperations(MLuaScoreboardService service) {
        this.service = service;
    }

    LuaTable createTeam(String name, Optional<String> displayNameValue) {
        requireName(name, "team");
        MCScoreboard scoreboard = service.scoreboard();
        if (scoreboard.getPlayerTeam(name) != null) {
            throw new LuaError("[MLua] Scoreboard team already exists: " + name);
        }
        MCPlayerTeam team = scoreboard.addPlayerTeam(name);
        team.setDisplayName(MCComponent.literal(displayNameValue.orElse(name)));
        return teamTable(team);
    }
    LuaValue removeTeam(String name) {
        requireName(name, "team");
        MCScoreboard sb = service.scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(name);
        if (team != null) sb.removePlayerTeam(team);
        return LuaValue.NONE;
    }
    LuaValue getTeam(String name) {
        requireName(name, "team");
        MCPlayerTeam team = service.scoreboard().getPlayerTeam(name);
        return team == null ? LuaValue.NIL : teamTable(team);
    }
    LuaValue addPlayerToTeam(String playerName, String teamName) {
        requireName(playerName, "score holder");
        requireName(teamName, "team");
        MCScoreboard sb = service.scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.addPlayerToTeam(playerName, team);
        return LuaValue.NONE;
    }
    LuaValue removePlayerFromTeam(String playerName, String teamName) {
        requireName(playerName, "score holder");
        requireName(teamName, "team");
        MCScoreboard sb = service.scoreboard();
        MCPlayerTeam team = sb.getPlayerTeam(teamName);
        if (team == null) throw new LuaError("[MLua] Team not found: " + teamName);
        sb.removePlayerFromTeam(playerName, team);
        return LuaValue.NONE;
    }
    LuaTable getTeams() {
        LuaTable result = new LuaTable();
        var teams = new java.util.ArrayList<>(service.scoreboard().getPlayerTeams());
        teams.sort(Comparator.comparing(MCPlayerTeam::getName));
        for (int index = 0; index < teams.size(); index++) {
            result.set(index + 1, teamTable(teams.get(index)));
        }
        return result;
    }
    LuaTable configureTeam(String name, Optional<String> displayName, Optional<String> prefix,
                           Optional<String> suffix) {
        requireName(name, "team");
        MCPlayerTeam team = service.scoreboard().getPlayerTeam(name);
        if (team == null) throw new LuaError("[MLua] Team not found: " + name);
        displayName.ifPresent(value -> team.setDisplayName(MCComponent.literal(value)));
        prefix.ifPresent(value -> team.setPrefix(MCComponent.literal(value)));
        suffix.ifPresent(value -> team.setSuffix(MCComponent.literal(value)));
        return teamTable(team);
    }
    LuaTable setFriendlyFire(String name, boolean enabled) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setAllowFriendlyFire(enabled);
        return teamTable(team);
    }
    LuaTable setSeeFriendlyInvisibles(String name, boolean enabled) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setSeeFriendlyInvisibles(enabled);
        return teamTable(team);
    }
    LuaTable setNameTagVisibility(String name, String visibility) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setNameTagVisibility(parseVisibility(visibility, "name tag"));
        return teamTable(team);
    }
    LuaTable setDeathMessageVisibility(String name, String visibility) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setDeathMessageVisibility(parseVisibility(visibility, "death message"));
        return teamTable(team);
    }
    LuaTable setCollisionRule(String name, String rule) {
        MCPlayerTeam team = requireTeam(name);
        rawTeam(team).setCollisionRule(parseCollisionRule(rule));
        return teamTable(team);
    }
    LuaTable setColor(String name, String colorName) {
        MCPlayerTeam team = requireTeam(name);
        String normalized = normalizeEnumName(colorName, "team color");
        ChatFormatting color = ChatFormatting.getByName(normalized);
        if (color == null || (color != ChatFormatting.RESET && !color.isColor())) {
            throw new LuaError("[MLua] Unknown Minecraft team color: " + colorName);
        }
        rawTeam(team).setColor(color == ChatFormatting.RESET ? null : color);
        return teamTable(team);
    }


    MCPlayerTeam requireTeam(String name) {
        requireName(name, "team");
        MCPlayerTeam team = service.scoreboard().getPlayerTeam(name);
        if (team == null) throw new LuaError("[MLua] Team not found: " + name);
        return team;
    }

    static PlayerTeam rawTeam(MCPlayerTeam team) {
        if (team instanceof MCPlayerTeamImpl implementation) return implementation.inner();
        throw new LuaError("[MLua] Team is not backed by a live Minecraft scoreboard team.");
    }

    static PlayerTeam.Visibility parseVisibility(String value, String kind) {
        String normalized = normalizeEnumName(value, kind + " visibility");
        try {
            return PlayerTeam.Visibility.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] Invalid " + kind + " visibility '" + value
                + "'. Expected always, never, hide_for_other_teams, or hide_for_own_team.");
        }
    }

    static PlayerTeam.CollisionRule parseCollisionRule(String value) {
        String normalized = normalizeEnumName(value, "collision rule");
        try {
            return PlayerTeam.CollisionRule.valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new LuaError("[MLua] Invalid team collision rule '" + value
                + "'. Expected always, never, push_other_teams, or push_own_team.");
        }
    }

    static String normalizeEnumName(String value, String kind) {
        if (value == null || value.isBlank()) throw new LuaError("[MLua] " + kind + " must be a non-empty string.");
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }


    LuaTable teamTable(MCPlayerTeam team) {
        LuaTable table = service.createTeamInstance();
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


    private static void requireName(String value, String kind) {
        MLuaScoreboardService.requireName(value, kind);
    }

}
