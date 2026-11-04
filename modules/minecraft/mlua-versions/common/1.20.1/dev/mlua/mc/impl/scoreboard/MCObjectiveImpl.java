package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCObjectiveCriteria;
import dev.mlua.mc.impl.core.MCComponentImpl;
import net.minecraft.world.scores.Objective;

public class MCObjectiveImpl implements MCObjective {
    private final Objective inner;

    public MCObjectiveImpl(Objective inner) {
        this.inner = inner;
    }

    public static MCObjectiveImpl fromReal(Objective inner) {
        return inner == null ? null : new MCObjectiveImpl(inner);
    }

    public Objective inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public MCComponent getDisplayName() {
        return MCComponentImpl.fromReal(inner.getDisplayName());
    }

    @Override
    public void setDisplayName(MCComponent displayName) {
        if (displayName instanceof MCComponentImpl impl) {
            inner.setDisplayName(impl.inner());
        }
    }

    @Override
    public MCObjectiveCriteria getCriteria() {
        return MCObjectiveCriteriaImpl.fromReal(inner.getCriteria());
    }

    @Override
    public MCObjectiveRenderType getRenderType() {
        net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType rt = inner.getRenderType();
        if (rt == net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.HEARTS) {
            return MCObjectiveRenderType.HEARTS;
        }
        return MCObjectiveRenderType.INTEGER;
    }
}
