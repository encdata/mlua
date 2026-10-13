package dev.mlua.mcapi.advancement;

import java.util.Map;

public interface MCAdvancementProgress {
    MCAdvancement getAdvancement();
    boolean isDone();
    Map<String, Object> getCriteria();
}
