package dev.mlua.runtime.client;

import dev.mlua.runtime.registry.MLuaEntityVisualDescriptor;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/** 26.1 render-state adapter for scripted living entities. */
public final class MLuaScriptedEntityRenderer extends MobRenderer<Mob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    public MLuaScriptedEntityRenderer(EntityRendererProvider.Context context, String registryKey) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), shadowRadius(registryKey));
        this.texture = texture(registryKey);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }

    private static Identifier texture(String key) {
        MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(key);
        String value = visuals == null ? "minecraft:textures/entity/zombie/zombie.png" : visuals.normalizedTexture();
        String[] parts = value.split(":", 2);
        return parts.length == 2 ? Identifier.fromNamespaceAndPath(parts[0], parts[1]) : Identifier.fromNamespaceAndPath("minecraft", value);
    }

    private static float shadowRadius(String key) {
        MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(key);
        return visuals == null ? 0.5F : visuals.shadowRadius();
    }
}
