package dev.mlua.mc.impl.server;

import dev.mlua.mc.impl.advancement.MCAdvancementImpl;
import dev.mlua.mc.impl.advancement.MCAdvancementProgressImpl;
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mcapi.advancement.MCAdvancementProgress;
import dev.mlua.mcapi.server.MCPlayerAdvancements;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public class MCPlayerAdvancementsImpl implements MCPlayerAdvancements {
    private final PlayerAdvancements inner;
    private final List<AdvancementHolder> advancements;

    public MCPlayerAdvancementsImpl(PlayerAdvancements inner) {
        this(inner, null);
    }

    private MCPlayerAdvancementsImpl(PlayerAdvancements inner, List<AdvancementHolder> advancements) {
        this.inner = inner;
        this.advancements = advancements;
    }

    public static MCPlayerAdvancementsImpl fromReal(PlayerAdvancements inner) {
        return inner == null ? null : new MCPlayerAdvancementsImpl(inner);
    }

    public static MCPlayerAdvancementsImpl fromReal(ServerPlayer player) {
        if (player == null) return null;
        return new MCPlayerAdvancementsImpl(player.getAdvancements(),
            List.copyOf(player.getServer().getAdvancements().getAllAdvancements()));
    }

    public PlayerAdvancements inner() {
        return inner;
    }

    @Override
    public Iterable<MCAdvancementProgress> getAdvancements() {
        if (this.advancements == null) {
            throw new IllegalStateException("Player advancement progress requires a ServerPlayer-backed adapter");
        }
        List<MCAdvancementProgress> result = new ArrayList<>(this.advancements.size());
        for (AdvancementHolder advancement : this.advancements) {
            var progress = this.inner.getOrStartProgress(advancement);
            result.add(new MCAdvancementProgressImpl(MCAdvancementImpl.fromReal(advancement), progress));
        }
        return List.copyOf(result);
    }

    @Override
    public boolean award(MCAdvancement advancement, String criterion) {
        if (!(advancement instanceof MCAdvancementImpl impl)) {
            throw new IllegalArgumentException("Advancement must use the active Minecraft version adapter");
        }
        return inner.award(impl.inner(), criterion);
    }
}
