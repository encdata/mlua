package dev.mlua.mc.impl.advancement;

import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mcapi.core.MCResourceLocation;
import net.minecraft.advancements.AdvancementHolder;

public class MCAdvancementImpl implements MCAdvancement {
    private final AdvancementHolder inner;

    public MCAdvancementImpl(AdvancementHolder inner) {
        this.inner = inner;
    }

    public static MCAdvancementImpl fromReal(AdvancementHolder inner) {
        return inner == null ? null : new MCAdvancementImpl(inner);
    }

    public AdvancementHolder inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getId() {
        return MCResourceLocationImpl.fromReal(inner.id());
    }

    @Override
    public Object getTitle() {
        return inner.value().display().map(display -> display.getTitle()).orElse(null);
    }

    @Override
    public Object getDescription() {
        return inner.value().display().map(display -> display.getDescription()).orElse(null);
    }
}
