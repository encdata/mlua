package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCMultiBufferSource;
import dev.mlua.mcapi.client.renderer.MCRenderType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MCMultiBufferSourceImpl implements MCMultiBufferSource {
    private final Object inner;
    public MCMultiBufferSourceImpl(Object inner) { this.inner = inner; }
    public static MCMultiBufferSourceImpl fromReal(Object inner) { return inner == null ? null : new MCMultiBufferSourceImpl(inner); }
    public Object inner() { return inner; }
    @Override public Object getBuffer(Object renderType) {
        if (inner == null) throw new IllegalStateException("Cannot get a render buffer without a buffer source");
        Object realRenderType = renderType instanceof MCRenderType type ? resolve(type) : renderType;
        if (realRenderType == null) throw new IllegalArgumentException("Unsupported render type: " + renderType);

        for (Method method : inner.getClass().getMethods()) {
            if (method.getName().equals("getBuffer") && method.getParameterCount() == 1
                && method.getParameterTypes()[0].isInstance(realRenderType)) {
                try {
                    return method.invoke(inner, realRenderType);
                } catch (IllegalAccessException | InvocationTargetException error) {
                    throw new IllegalStateException("Failed to get Minecraft render buffer", error);
                }
            }
        }
        throw new IllegalArgumentException("Render type is not accepted by this buffer source: " + renderType);
    }

    private static Object resolve(MCRenderType renderType) {
        String name = switch (renderType.toString()) {
            case "solid" -> "solid";
            case "cutout" -> "cutout";
            case "cutout_mipped" -> "cutoutMipped";
            case "translucent" -> "translucent";
            default -> null;
        };
        if (name == null) return null;

        for (String className : new String[] {
            "net.minecraft.client.renderer.rendertype.RenderType",
            "net.minecraft.client.renderer.RenderType"
        }) {
            try {
                Class<?> renderTypes = Class.forName(className);
                return renderTypes.getMethod(name).invoke(null);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                // Try the other supported Minecraft package layout.
            } catch (IllegalAccessException | InvocationTargetException error) {
                throw new IllegalStateException("Failed to resolve render type " + name, error);
            }
        }
        return null;
    }
}
