package dev.mlua.mcapi.resources;

import dev.mlua.mcapi.core.MCResourceLocation;

public interface MCResourceKey<T> {

    T getRegistryKey();

    MCResourceLocation location();

    static <T> MCResourceKey<T> create(MCResourceLocation location) {
        return new MCResourceKey<T>() {
            @Override
            public T getRegistryKey() {
                return null;
            }

            @Override
            public MCResourceLocation location() {
                return location;
            }
        };
    }

}
