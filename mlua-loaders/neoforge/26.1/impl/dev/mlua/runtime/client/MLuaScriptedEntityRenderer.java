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

/** Dedicated 26.1 NeoForge render-state adapter for scripted living entities. */
public final class MLuaScriptedEntityRenderer extends MobRenderer<Mob, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;
    public MLuaScriptedEntityRenderer(EntityRendererProvider.Context context, String key) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), shadowRadius(key));
        MLuaEntityVisualDescriptor visuals = MLuaLoaderRegistry.getEntityVisual(key);
        String value = visuals == null ? "minecraft:textures/entity/zombie/zombie.png" : visuals.normalizedTexture();
        this.texture = Identifier.parse(value);
    }
    @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
    @Override public Identifier getTextureLocation(HumanoidRenderState state) { return texture; }
    private static float shadowRadius(String key) { MLuaEntityVisualDescriptor v = MLuaLoaderRegistry.getEntityVisual(key); return v == null ? .5F : v.shadowRadius(); }
}
