package dev.mlua.mc.impl.advancement;

import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mcapi.advancement.MCAdvancementProgress;
import net.minecraft.advancements.AdvancementProgress;

import java.util.HashMap;
import java.util.Map;

public class MCAdvancementProgressImpl implements MCAdvancementProgress {
    private final MCAdvancement advancement;
    private final AdvancementProgress inner;

    public MCAdvancementProgressImpl(MCAdvancement advancement, AdvancementProgress inner) {
        this.advancement = advancement;
        this.inner = inner;
    }

    public static MCAdvancementProgressImpl fromReal(MCAdvancement advancement, AdvancementProgress inner) {
        return inner == null ? null : new MCAdvancementProgressImpl(advancement, inner);
    }

    public AdvancementProgress inner() {
        return inner;
    }

    @Override
    public MCAdvancement getAdvancement() {
        return advancement;
    }

    @Override
    public boolean isDone() {
        return inner.isDone();
    }

    @Override
    public Map<String, Object> getCriteria() {
        Map<String, Object> result = new HashMap<>();
        for (String criterion : inner.getCompletedCriteria()) {
            result.put(criterion, true);
        }
        for (String criterion : inner.getRemainingCriteria()) {
            result.put(criterion, false);
        }
        return result;
    }
}
