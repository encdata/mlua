package dev.mlua.mc.impl.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mlua.mcapi.client.MCKeyMapping;
import java.lang.reflect.Field;
import net.minecraft.client.KeyMapping;

public class MCKeyMappingImpl implements MCKeyMapping {
    private static final Field KEY_FIELD;
    static {
        try {
            KEY_FIELD = KeyMapping.class.getDeclaredField("key");
            KEY_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to access KeyMapping.key field", e);
        }
    }

    private final KeyMapping inner;

    public MCKeyMappingImpl(KeyMapping inner) {
        this.inner = inner;
    }

    public static MCKeyMappingImpl fromReal(KeyMapping inner) {
        return inner == null ? null : new MCKeyMappingImpl(inner);
    }

    public KeyMapping inner() {
        return inner;
    }

    @Override
    public String getName() {
        return inner.getName();
    }

    @Override
    public String getCategory() {
        return inner.getCategory();
    }

    @Override
    public boolean isDown() {
        return inner.isDown();
    }

    @Override
    public boolean consumeClick() {
        return inner.consumeClick();
    }

    @Override
    public int getKeyCode() {
        try {
            return ((InputConstants.Key) KEY_FIELD.get(inner)).getValue();
        } catch (Exception e) {
            return -1;
        }
    }
}
