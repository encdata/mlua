package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCScore;
import net.minecraft.world.scores.Score;

public class MCScoreImpl implements MCScore {
    private final Score inner;

    public MCScoreImpl(Score inner) {
        this.inner = inner;
    }

    public static MCScoreImpl fromReal(Score inner) {
        return inner == null ? null : new MCScoreImpl(inner);
    }

    public Score inner() {
        return inner;
    }

    @Override
    public String getOwner() {
        return inner.getOwner();
    }

    @Override
    public int getScore() {
        return inner.getScore();
    }

    @Override
    public void setScore(int score) {
        inner.setScore(score);
    }

    @Override
    public MCObjective getObjective() {
        return MCObjectiveImpl.fromReal(inner.getObjective());
    }

    @Override
    public boolean isLocked() {
        return inner.isLocked();
    }
}
