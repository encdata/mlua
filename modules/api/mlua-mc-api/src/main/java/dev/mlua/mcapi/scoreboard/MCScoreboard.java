package dev.mlua.mcapi.scoreboard;

import java.util.Collection;
import java.util.List;

public interface MCScoreboard {

    MCObjective getObjective(String name);

    MCObjective getDisplayObjective(int slot);

    Collection<MCObjective> getObjectives();

    MCPlayerTeam addPlayerTeam(String name);

    MCPlayerTeam getPlayerTeam(String name);

    void removePlayerTeam(MCPlayerTeam team);

    Collection<MCPlayerTeam> getPlayerTeams();

    void addPlayerToTeam(String playerName, MCPlayerTeam team);

    void removePlayerFromTeam(String playerName, MCPlayerTeam team);

    List<MCScore> getPlayerScores(MCObjective objective);

}
