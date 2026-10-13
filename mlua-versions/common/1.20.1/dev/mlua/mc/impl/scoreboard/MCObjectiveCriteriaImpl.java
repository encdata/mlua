package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.scoreboard.MCObjectiveCriteria;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class MCObjectiveCriteriaImpl implements MCObjectiveCriteria {
    private final ObjectiveCriteria inner;

    public MCObjectiveCriteriaImpl(ObjectiveCriteria inner) {
        this.inner = inner;
    }

    public static MCObjectiveCriteriaImpl fromReal(ObjectiveCriteria inner) {
        return inner == null ? null : new MCObjectiveCriteriaImpl(inner);
    }

    public ObjectiveCriteria inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }
}
