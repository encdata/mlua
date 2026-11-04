package dev.mlua.mc.impl.scoreboard;

import dev.mlua.mcapi.core.MCComponent;
import dev.mlua.mcapi.scoreboard.MCPlayerTeam;
import dev.mlua.mc.impl.core.MCComponentImpl;
import net.minecraft.world.scores.PlayerTeam;

import java.util.Collection;

public class MCPlayerTeamImpl implements MCPlayerTeam {
    private final PlayerTeam inner;

    public MCPlayerTeamImpl(PlayerTeam inner) {
        this.inner = inner;
    }

    public static MCPlayerTeamImpl fromReal(PlayerTeam inner) {
        return inner == null ? null : new MCPlayerTeamImpl(inner);
    }

    public PlayerTeam inner() {
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
    public MCComponent getPrefix() {
        return MCComponentImpl.fromReal(inner.getPlayerPrefix());
    }

    @Override
    public MCComponent getSuffix() {
        return MCComponentImpl.fromReal(inner.getPlayerSuffix());
    }

    @Override
    public void setDisplayName(MCComponent displayName) {
        if (displayName instanceof MCComponentImpl impl) {
            inner.setDisplayName(impl.inner());
        }
    }

    @Override
    public void setPrefix(MCComponent prefix) {
        if (prefix instanceof MCComponentImpl impl) {
            inner.setPlayerPrefix(impl.inner());
        }
    }

    @Override
    public void setSuffix(MCComponent suffix) {
        if (suffix instanceof MCComponentImpl impl) {
            inner.setPlayerSuffix(impl.inner());
        }
    }

    @Override
    public Collection<String> getPlayers() {
        return inner.getPlayers();
    }

    @Override
    public boolean isAllowFriendlyFire() {
        return inner.isAllowFriendlyFire();
    }

    @Override
    public boolean isSeeFriendlyInvisibles() {
        return inner.canSeeFriendlyInvisibles();
    }

    @Override
    public MCTeamVisibility getNameTagVisibility() {
        return mapVisibility(inner.getNameTagVisibility());
    }

    @Override
    public MCTeamVisibility getDeathMessageVisibility() {
        return mapVisibility(inner.getDeathMessageVisibility());
    }

    @Override
    public MCCollisionRule getCollisionRule() {
        return mapCollisionRule(inner.getCollisionRule());
    }

    private static MCTeamVisibility mapVisibility(PlayerTeam.Visibility visibility) {
        if (visibility == PlayerTeam.Visibility.ALWAYS) return MCTeamVisibility.ALWAYS;
        if (visibility == PlayerTeam.Visibility.NEVER) return MCTeamVisibility.NEVER;
        if (visibility == PlayerTeam.Visibility.HIDE_FOR_OTHER_TEAMS) return MCTeamVisibility.HIDE_FOR_OTHER_TEAMS;
        if (visibility == PlayerTeam.Visibility.HIDE_FOR_OWN_TEAM) return MCTeamVisibility.HIDE_FOR_OWN_TEAM;
        return MCTeamVisibility.ALWAYS;
    }

    private static MCCollisionRule mapCollisionRule(PlayerTeam.CollisionRule rule) {
        if (rule == PlayerTeam.CollisionRule.ALWAYS) return MCCollisionRule.ALWAYS;
        if (rule == PlayerTeam.CollisionRule.NEVER) return MCCollisionRule.NEVER;
        if (rule == PlayerTeam.CollisionRule.PUSH_OTHER_TEAMS) return MCCollisionRule.PUSH_OTHER_TEAMS;
        if (rule == PlayerTeam.CollisionRule.PUSH_OWN_TEAM) return MCCollisionRule.PUSH_OWN_TEAM;
        return MCCollisionRule.ALWAYS;
    }
}
