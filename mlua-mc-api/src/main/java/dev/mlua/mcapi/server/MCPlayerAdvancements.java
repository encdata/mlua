package dev.mlua.mcapi.server;

import dev.mlua.mcapi.advancement.MCAdvancement;
import dev.mlua.mcapi.advancement.MCAdvancementProgress;

public interface MCPlayerAdvancements {
    Iterable<MCAdvancementProgress> getAdvancements();
    boolean award(MCAdvancement advancement, String criterion);
}
