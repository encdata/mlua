package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.scoreboard.MCObjective;
import dev.mlua.mcapi.scoreboard.MCScore;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.ScoreHolder;

public class MCScoreImpl implements MCScore {
    private final net.minecraft.world.scores.Scoreboard scoreboard;
    private final Objective objective;
    private final String owner;

    public MCScoreImpl(net.minecraft.world.scores.Scoreboard scoreboard, Objective objective, String owner) {
        this.scoreboard = scoreboard;
        this.objective = objective;
        this.owner = owner;
    }

    public static MCScoreImpl fromEntry(net.minecraft.world.scores.Scoreboard scoreboard, Objective objective,
                                        net.minecraft.world.scores.PlayerScoreEntry entry) {
        return new MCScoreImpl(scoreboard, objective, entry.owner());
    }

    private ScoreAccess access() {
        return scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), objective);
    }

    @Override
    public String getOwner() {
        return owner;
    }

    @Override
    public int getScore() {
        return access().get();
    }

    @Override
    public void setScore(int score) {
        access().set(score);
    }

    @Override
    public MCObjective getObjective() {
        return MCObjectiveImpl.fromReal(objective);
    }

    @Override
    public boolean isLocked() {
        return access().locked();
    }
}
