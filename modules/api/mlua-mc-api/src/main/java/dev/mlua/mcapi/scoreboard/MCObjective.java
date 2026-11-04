package dev.mlua.mcapi.scoreboard;

import dev.mlua.mcapi.core.MCComponent;

public interface MCObjective {

    String getName();

    MCComponent getDisplayName();

    void setDisplayName(MCComponent displayName);

    MCObjectiveCriteria getCriteria();

    MCObjectiveRenderType getRenderType();

    enum MCObjectiveRenderType {
        INTEGER,
        HEARTS
    }

}
