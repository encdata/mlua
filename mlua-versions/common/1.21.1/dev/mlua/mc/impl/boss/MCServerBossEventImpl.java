package dev.mlua.mc.impl.boss;

import dev.mlua.mc.impl.entity.MCPlayerImpl;
import dev.mlua.mcapi.boss.MCServerBossEvent;
import dev.mlua.mcapi.entity.MCPlayer;
import java.util.ArrayList;
import java.util.Collection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.level.ServerPlayer;

public class MCServerBossEventImpl implements MCServerBossEvent {
    private final CustomBossEvent inner;

    public MCServerBossEventImpl(CustomBossEvent inner) {
        this.inner = inner;
    }

    public static MCServerBossEventImpl fromReal(CustomBossEvent inner) {
        return inner == null ? null : new MCServerBossEventImpl(inner);
    }

    public CustomBossEvent inner() {
        return inner;
    }

    @Override
    public void addPlayer(MCPlayer player) {
        if (player instanceof MCPlayerImpl impl && impl.inner() instanceof ServerPlayer sp) {
            inner.addPlayer(sp);
        }
    }

    @Override
    public void removePlayer(MCPlayer player) {
        if (player instanceof MCPlayerImpl impl && impl.inner() instanceof ServerPlayer sp) {
            inner.removePlayer(sp);
        }
    }

    @Override
    public void removeAllPlayers() {
        inner.removeAllPlayers();
    }

    @Override
    public Collection<MCPlayer> getPlayers() {
        Collection<MCPlayer> result = new ArrayList<>();
        for (ServerPlayer player : inner.getPlayers()) {
            result.add(MCPlayerImpl.fromReal(player));
        }
        return result;
    }

    @Override
    public java.util.UUID getId() {
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
    public dev.mlua.mcapi.boss.MCBossEvent.MCBossEventColor getColor() {
        net.minecraft.world.BossEvent.BossBarColor color = inner.getColor();
        if (color == net.minecraft.world.BossEvent.BossBarColor.PINK) return MCBossEventColor.PINK;
        if (color == net.minecraft.world.BossEvent.BossBarColor.BLUE) return MCBossEventColor.BLUE;
        if (color == net.minecraft.world.BossEvent.BossBarColor.RED) return MCBossEventColor.RED;
        if (color == net.minecraft.world.BossEvent.BossBarColor.GREEN) return MCBossEventColor.GREEN;
        if (color == net.minecraft.world.BossEvent.BossBarColor.YELLOW) return MCBossEventColor.YELLOW;
        if (color == net.minecraft.world.BossEvent.BossBarColor.PURPLE) return MCBossEventColor.PURPLE;
        if (color == net.minecraft.world.BossEvent.BossBarColor.WHITE) return MCBossEventColor.WHITE;
        return MCBossEventColor.WHITE;
    }

    @Override
    public void setColor(MCBossEventColor color) {
        net.minecraft.world.BossEvent.BossBarColor mapped;
        switch (color) {
            case PINK -> mapped = net.minecraft.world.BossEvent.BossBarColor.PINK;
            case BLUE -> mapped = net.minecraft.world.BossEvent.BossBarColor.BLUE;
            case RED -> mapped = net.minecraft.world.BossEvent.BossBarColor.RED;
            case GREEN -> mapped = net.minecraft.world.BossEvent.BossBarColor.GREEN;
            case YELLOW -> mapped = net.minecraft.world.BossEvent.BossBarColor.YELLOW;
            case PURPLE -> mapped = net.minecraft.world.BossEvent.BossBarColor.PURPLE;
            default -> mapped = net.minecraft.world.BossEvent.BossBarColor.WHITE;
        }
        inner.setColor(mapped);
    }

    @Override
    public MCOverlay getOverlay() {
        net.minecraft.world.BossEvent.BossBarOverlay overlay = inner.getOverlay();
        if (overlay == net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS) return MCOverlay.PROGRESS;
        if (overlay == net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_6) return MCOverlay.NOTCHED_6;
        if (overlay == net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10) return MCOverlay.NOTCHED_10;
        if (overlay == net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_12) return MCOverlay.NOTCHED_12;
        if (overlay == net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_20) return MCOverlay.NOTCHED_20;
        return MCOverlay.PROGRESS;
    }

    @Override
    public void setOverlay(MCOverlay overlay) {
        net.minecraft.world.BossEvent.BossBarOverlay mapped;
        switch (overlay) {
            case NOTCHED_6 -> mapped = net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_6;
            case NOTCHED_10 -> mapped = net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10;
            case NOTCHED_12 -> mapped = net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_12;
            case NOTCHED_20 -> mapped = net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_20;
            default -> mapped = net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS;
        }
        inner.setOverlay(mapped);
    }

    public static MCServerBossEvent create() {
        return new MCServerBossEventImpl(new CustomBossEvent(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("mlua", "boss_event"),
            Component.literal("Boss")
        ));
    }
}
