package dev.mlua.mc.impl.advancement;

import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mcapi.core.MCResourceLocation;
import dev.mlua.mc.impl.core.MCResourceLocationImpl;
import net.minecraft.advancements.Advancement;

public class MCAdvancementImpl implements MCAdvancement {
    private final Advancement inner;

    public MCAdvancementImpl(Advancement inner) {
        this.inner = inner;
    }

    public static MCAdvancementImpl fromReal(Advancement inner) {
        return inner == null ? null : new MCAdvancementImpl(inner);
    }

    public Advancement inner() {
        return inner;
    }

    @Override
    public MCResourceLocation getId() {
        return MCResourceLocationImpl.fromReal(inner.getId());
    }

    @Override
    public Object getTitle() {
        if (inner.getDisplay() != null) {
            return inner.getDisplay().getTitle();
        }
        return null;
    }

    @Override
    public Object getDescription() {
        if (inner.getDisplay() != null) {
            return inner.getDisplay().getDescription();
        }
        return null;
    }
}
