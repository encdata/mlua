package dev.mlua.minecraft.binding;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class MLuaAttributeModifierIds {
    private MLuaAttributeModifierIds() { }

    public static UUID legacy(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    public static String path(String name) {
        return "modifier_" + name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_/.-]", "_");
    }
}
