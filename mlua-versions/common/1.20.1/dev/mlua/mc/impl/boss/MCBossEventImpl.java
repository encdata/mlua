package dev.mlua.mc.impl.boss;

import dev.mlua.mcapi.boss.MCBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public class MCBossEventImpl implements MCBossEvent {
    private final BossEvent inner;

    public MCBossEventImpl(BossEvent inner) {
        this.inner = inner;
    }

    public static MCBossEventImpl fromReal(BossEvent inner) {
        return inner == null ? null : new MCBossEventImpl(inner);
    }

    public BossEvent inner() {
        return inner;
    }

    @Override
    public UUID getId() {
        return inner.getId();
    }

    @Override
    public Object getName() {
        return inner.getName();
    }

    @Override
    public float getProgress() {
        return inner.getProgress();
    }

    @Override
    public void setProgress(float progress) {
        inner.setProgress(progress);
    }

    @Override
    public MCBossEventColor getColor() {
        BossEvent.BossBarColor color = inner.getColor();
        if (color == BossEvent.BossBarColor.PINK) return MCBossEventColor.PINK;
        if (color == BossEvent.BossBarColor.BLUE) return MCBossEventColor.BLUE;
        if (color == BossEvent.BossBarColor.RED) return MCBossEventColor.RED;
        if (color == BossEvent.BossBarColor.GREEN) return MCBossEventColor.GREEN;
        if (color == BossEvent.BossBarColor.YELLOW) return MCBossEventColor.YELLOW;
        if (color == BossEvent.BossBarColor.PURPLE) return MCBossEventColor.PURPLE;
        if (color == BossEvent.BossBarColor.WHITE) return MCBossEventColor.WHITE;
        return MCBossEventColor.WHITE;
    }

    @Override
    public void setColor(MCBossEventColor color) {
        BossEvent.BossBarColor mapped;
        switch (color) {
            case PINK: mapped = BossEvent.BossBarColor.PINK; break;
            case BLUE: mapped = BossEvent.BossBarColor.BLUE; break;
            case RED: mapped = BossEvent.BossBarColor.RED; break;
            case GREEN: mapped = BossEvent.BossBarColor.GREEN; break;
            case YELLOW: mapped = BossEvent.BossBarColor.YELLOW; break;
            case PURPLE: mapped = BossEvent.BossBarColor.PURPLE; break;
            default: mapped = BossEvent.BossBarColor.WHITE; break;
        }
        inner.setColor(mapped);
    }

    @Override
    public MCOverlay getOverlay() {
        BossEvent.BossBarOverlay overlay = inner.getOverlay();
        if (overlay == BossEvent.BossBarOverlay.PROGRESS) return MCOverlay.PROGRESS;
        if (overlay == BossEvent.BossBarOverlay.NOTCHED_6) return MCOverlay.NOTCHED_6;
        if (overlay == BossEvent.BossBarOverlay.NOTCHED_10) return MCOverlay.NOTCHED_10;
        if (overlay == BossEvent.BossBarOverlay.NOTCHED_12) return MCOverlay.NOTCHED_12;
        if (overlay == BossEvent.BossBarOverlay.NOTCHED_20) return MCOverlay.NOTCHED_20;
        return MCOverlay.PROGRESS;
    }

    @Override
    public void setOverlay(MCOverlay overlay) {
        BossEvent.BossBarOverlay mapped;
        switch (overlay) {
            case NOTCHED_6: mapped = BossEvent.BossBarOverlay.NOTCHED_6; break;
            case NOTCHED_10: mapped = BossEvent.BossBarOverlay.NOTCHED_10; break;
            case NOTCHED_12: mapped = BossEvent.BossBarOverlay.NOTCHED_12; break;
            case NOTCHED_20: mapped = BossEvent.BossBarOverlay.NOTCHED_20; break;
            default: mapped = BossEvent.BossBarOverlay.PROGRESS; break;
        }
        inner.setOverlay(mapped);
    }
}
