package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.runtime.registry.MLuaEntityVisualDescriptor;
import dev.mlua.runtime.registry.MLuaLoaderRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

abstract class MLuaScriptedEntityRendererBase extends MobRenderer<Mob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;

    protected MLuaScriptedEntityRendererBase(EntityRendererProvider.Context context, String registryKey) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), shadowRadius(registryKey));
        MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(registryKey);
        String textureId = visuals == null ? "minecraft:textures/entity/zombie/zombie.png" : visuals.normalizedTexture();
        this.texture = MLuaResourceLocations.parse(textureId);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }

    private static float shadowRadius(String registryKey) {
        MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(registryKey);
        return visuals == null ? 0.5F : visuals.shadowRadius();
    }
}
