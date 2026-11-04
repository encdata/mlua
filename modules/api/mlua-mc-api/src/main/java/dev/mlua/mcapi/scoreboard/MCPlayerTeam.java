package dev.mlua.mcapi.scoreboard;

import dev.mlua.mcapi.core.MCComponent;

import java.util.Collection;

public interface MCPlayerTeam {

    String getName();

    MCComponent getDisplayName();

    MCComponent getPrefix();

    MCComponent getSuffix();

    void setDisplayName(MCComponent displayName);

    void setPrefix(MCComponent prefix);

    void setSuffix(MCComponent suffix);

    Collection<String> getPlayers();

    boolean isAllowFriendlyFire();

    boolean isSeeFriendlyInvisibles();

    MCTeamVisibility getNameTagVisibility();

    MCTeamVisibility getDeathMessageVisibility();

    MCCollisionRule getCollisionRule();

    enum MCTeamVisibility {
        ALWAYS,
        NEVER,
        HIDE_FOR_OTHER_TEAMS,
        HIDE_FOR_OWN_TEAM
    }

    enum MCCollisionRule {
        ALWAYS,
        NEVER,
        PUSH_OTHER_TEAMS,
        PUSH_OWN_TEAM
    }

}
