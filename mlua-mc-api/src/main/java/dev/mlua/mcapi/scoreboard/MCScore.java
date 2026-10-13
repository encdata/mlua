package dev.mlua.mcapi.scoreboard;

public interface MCScore {

    String getOwner();

    int getScore();

    void setScore(int score);

    MCObjective getObjective();

    boolean isLocked();

}
