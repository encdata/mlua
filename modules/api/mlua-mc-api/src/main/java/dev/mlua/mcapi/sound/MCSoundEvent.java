package dev.mlua.mcapi.sound;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCSoundEvent {
    MCResourceLocation getLocation();

    static MCSoundEvent create(MCResourceLocation location) {
        return () -> location;
    }
}
