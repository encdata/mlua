package dev.mlua.mcapi.advancement;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCAdvancement {
    MCResourceLocation getId();
    Object getTitle();
    Object getDescription();
}
