//--version 1.20.1,1.21.1
package dev.mlua.runtime.client;

import dev.mlua.runtime.client.MLuaClientRenderManager.RenderObject;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.ResourceLocation;

/** Draws animated Blockbench assets in world, entity, and held-item render targets. */
final class MLuaBBModelWorldRenderer {
    private MLuaBBModelWorldRenderer() { }

    static void render(PoseStack pose, MultiBufferSource buffers, RenderObject object,
            int light, int overlay, float partialTick) {
        List<ResourceLocation> textures = object.modelTextures;
        var animation = object.animationPlayer.active() == null
            ? null : object.bbmodel.animations().get(object.animationPlayer.active());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        for (MLuaBBModelAsset.Cube cube : object.bbmodel.cubes()) {
            List<MLuaBBModelAsset.Bone> boneChain = object.bbmodel.boneChainForCube(cube.uuid());
            if (!object.isModelPartVisible(boneChain)) continue;
            pose.pushPose();
            try {
                for (MLuaBBModelAsset.Bone bone : boneChain) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        scratch.position, scratch.rotation, scratch.scale);
                    MLuaClientRenderManager.applyTransform(pose, object.modelPartTransform(bone.uuid()));
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    scratch.position, scratch.rotation, scratch.scale);
                MLuaRenderHelper.renderBBModelCube(pose, buffers, cube, textures,
                    object.bbmodel.textureWidth(), object.bbmodel.textureHeight(),
                    MLuaRenderHelper.fallbackTexture(), light, overlay);
            } finally {
                pose.popPose();
            }
        }
    }
}
//--endversion
