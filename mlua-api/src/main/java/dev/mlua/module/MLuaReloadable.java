package dev.mlua.module;

import dev.mlua.common.enums.Side;

/** Owns state that must be released when scripts or a runtime session reloads. */
public interface MLuaReloadable {
    default void onReload() {
    }

    /** Releases state belonging to one runtime side during a scoped script reload. */
    default void onReload(Side side) {
        onReload();
    }

    default void onUnload() {
    }
}
