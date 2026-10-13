package dev.mlua.mc.impl.client.renderer;

import dev.mlua.mcapi.client.renderer.MCMultiBufferSource;
import dev.mlua.mcapi.client.renderer.MCRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

public class MCMultiBufferSourceImpl implements MCMultiBufferSource {
    private final MultiBufferSource inner;

    public MCMultiBufferSourceImpl(MultiBufferSource inner) {
        this.inner = inner;
    }

    public static MCMultiBufferSourceImpl fromReal(MultiBufferSource inner) {
        return inner == null ? null : new MCMultiBufferSourceImpl(inner);
    }

    public MultiBufferSource inner() {
        return inner;
    }

    @Override
    public Object getBuffer(Object renderType) {
        if (renderType instanceof RenderType rt) {
            return inner.getBuffer(rt);
        }
        if (renderType instanceof MCRenderTypeImpl impl) {
            if (impl.inner() == null) throw new IllegalArgumentException("Render type has no Minecraft backing value");
            return inner.getBuffer(impl.inner());
        }
        if (renderType instanceof MCRenderType type) {
            RenderType resolved = switch (type.getKind()) {
                case "solid" -> RenderType.solid();
                case "cutout" -> RenderType.cutout();
                case "cutout_mipped" -> RenderType.cutoutMipped();
                case "translucent" -> RenderType.translucent();
                case "entity_translucent" -> RenderType.entityTranslucent(
                    MCRenderTypeImpl.resolveTexture(type.getTexture()), false);
                default -> throw new IllegalArgumentException("Unsupported render type: " + type);
            };
            return inner.getBuffer(resolved);
        }
        throw new IllegalArgumentException("Unsupported render type: " + renderType);
    }
}
