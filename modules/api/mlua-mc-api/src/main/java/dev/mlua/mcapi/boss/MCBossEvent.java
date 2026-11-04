package dev.mlua.mcapi.boss;

import java.util.UUID;

public interface MCBossEvent {
    UUID getId();
    Object getName();
    float getProgress();
    void setProgress(float progress);
    MCBossEventColor getColor();
    void setColor(MCBossEventColor color);
    MCOverlay getOverlay();
    void setOverlay(MCOverlay overlay);

    enum MCBossEventColor {
        PINK,
        BLUE,
        RED,
        GREEN,
        YELLOW,
        PURPLE,
        WHITE
    }

    enum MCOverlay {
        PROGRESS,
        NOTCHED_6,
        NOTCHED_10,
        NOTCHED_12,
        NOTCHED_20
    }
}
