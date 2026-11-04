package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mcapi.scoreboard.MCScore;
import dev.mlua.mcapi.scoreboard.MCScoreboard;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

public class MCScoreboardImpl implements MCScoreboard {
    private final Scoreboard inner;

    public MCScoreboardImpl(Scoreboard inner) {
        this.inner = inner;
    }

    public static MCScoreboardImpl fromReal(Scoreboard inner) {
        return inner == null ? null : new MCScoreboardImpl(inner);
    }

    public Scoreboard inner() {
        return inner;
    }

    @Override
    public MCObjective getObjective(String name) {
        Objective obj = inner.getObjective(name);
        return MCObjectiveImpl.fromReal(obj);
    }

    @Override
    public MCObjective getDisplayObjective(int slot) {
        Objective obj = inner.getDisplayObjective(DisplaySlot.BY_ID.apply(slot));
        return MCObjectiveImpl.fromReal(obj);
    }

    @Override
    public Collection<MCObjective> getObjectives() {
        List<MCObjective> result = new ArrayList<>();
        for (Objective obj : inner.getObjectives()) {
            result.add(MCObjectiveImpl.fromReal(obj));
        }
        return result;
    }

    @Override
    public MCPlayerTeam addPlayerTeam(String name) {
        net.minecraft.world.scores.PlayerTeam team = inner.addPlayerTeam(name);
        return MCPlayerTeamImpl.fromReal(team);
    }

    @Override
    public MCPlayerTeam getPlayerTeam(String name) {
        net.minecraft.world.scores.PlayerTeam team = inner.getPlayerTeam(name);
        return MCPlayerTeamImpl.fromReal(team);
    }

    @Override
    public void removePlayerTeam(MCPlayerTeam team) {
        if (team instanceof MCPlayerTeamImpl impl) {
            inner.removePlayerTeam(impl.inner());
        }
    }

    @Override
    public Collection<MCPlayerTeam> getPlayerTeams() {
        List<MCPlayerTeam> result = new ArrayList<>();
        for (net.minecraft.world.scores.PlayerTeam team : inner.getPlayerTeams()) {
            result.add(MCPlayerTeamImpl.fromReal(team));
        }
        return result;
    }

    @Override
    public void addPlayerToTeam(String playerName, MCPlayerTeam team) {
        if (team instanceof MCPlayerTeamImpl impl) {
            inner.addPlayerToTeam(playerName, impl.inner());
        }
    }

    @Override
    public void removePlayerFromTeam(String playerName, MCPlayerTeam team) {
        if (team instanceof MCPlayerTeamImpl impl) {
            inner.removePlayerFromTeam(playerName, impl.inner());
        }
    }

    @Override
    public List<MCScore> getPlayerScores(MCObjective objective) {
        List<MCScore> result = new ArrayList<>();
        if (objective instanceof MCObjectiveImpl impl) {
            for (net.minecraft.world.scores.PlayerScoreEntry entry : inner.listPlayerScores(impl.inner())) {
                result.add(MCScoreImpl.fromEntry(inner, impl.inner(), entry));
            }
        }
        return result;
    }
}
