package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mlua.minecraft.scripted.MLuaScriptedProjectile;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** 26.1 render-state adapter for scripted projectiles. */
public final class MLuaProjectileRenderer extends EntityRenderer<MLuaScriptedProjectile, EntityRenderState> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/misc/white.png");

    public MLuaProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.mulPose(camera.orientation);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.3F, 0.3F, 0.3F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE),
            (pose, vertices) -> renderQuad(vertices, pose, state.lightCoords));
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void renderQuad(VertexConsumer vertices, PoseStack.Pose pose, int light) {
        vertex(vertices, pose, -0.5F, -0.5F, 0.0F, 0.0F, 1.0F, light);
        vertex(vertices, pose, 0.5F, -0.5F, 0.0F, 1.0F, 1.0F, light);
        vertex(vertices, pose, 0.5F, 0.5F, 0.0F, 1.0F, 0.0F, light);
        vertex(vertices, pose, -0.5F, 0.5F, 0.0F, 0.0F, 0.0F, light);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v, int light) {
        vertices.addVertex(pose, x, y, z)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
