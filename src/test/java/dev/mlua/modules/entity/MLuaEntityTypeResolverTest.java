package dev.mlua.modules.entity;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MLuaEntityTypeResolverTest {
    @Test
    void registryIterationFindsAKeyWhenDirectLookupIsUnavailable() {
        Object sheep = new Object();
        Object cow = new Object();

        Map<String, Object> index = MLuaEntityTypeResolver.indexByKey(
            List.of(sheep, cow), entity -> entity == sheep ? "minecraft:sheep" : "minecraft:cow");

        assertSame(sheep, index.get("minecraft:sheep"));
    }

    @Test
    void registryIterationReturnsNullForAnUnknownKey() {
        Object sheep = new Object();

        Map<String, Object> index = MLuaEntityTypeResolver.indexByKey(
            List.of(sheep), ignored -> "minecraft:sheep");

        assertNull(index.get("minecraft:missing"));
    }
}
