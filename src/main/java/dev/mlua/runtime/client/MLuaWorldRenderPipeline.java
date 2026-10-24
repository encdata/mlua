//--version 1.20.1,1.21.1
package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaBBModelRenderPlan;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import dev.mlua.runtime.client.MLuaClientRenderManager.RenderObject;
import dev.mlua.runtime.client.MLuaClientRenderManager.RenderTarget;
import dev.mlua.runtime.client.MLuaClientRenderManager.RenderType;

/** Owns world-stage context adaptation and world geometry submission for render objects. */
final class MLuaWorldRenderPipeline {
    private static final AtomicBoolean WORLD_RENDER_FAILURE_REPORTED = new AtomicBoolean();

    private MLuaWorldRenderPipeline() { }

    static void renderWorldContext(String stage, Object context) {
        try {
            if (context instanceof dev.mlua.loaderapi.ClientWorldRenderContext worldContext) {
                Object nativeContext = worldContext.nativeContext();
                Object pose = worldContext.poseStack();
                Object source = worldContext.renderSource();
                Object camera = worldContext.camera();
                if (pose == null) pose = MLuaRenderContextAccess.optional(nativeContext, "matrixStack", "getPoseStack", "poseStack");
                if (source == null) source = MLuaRenderContextAccess.optional(nativeContext, "consumers", "getRenderTypeBuffer",
                    "bufferSource", "getSubmitNodeCollector", "submitNodeCollector", "getRenderCollector");
                if (camera == null) camera = MLuaRenderContextAccess.optional(nativeContext, "camera", "getCamera");
                MLuaRenderCallbacks.renderWorld(stage, nativeContext, pose, source, camera);
                Float partialTick = MLuaRenderCallbacks.readPartialTick(nativeContext);
                if (partialTick == null) partialTick = MLuaRenderCallbacks.currentClientPartialTick();
                renderWorldModels(stage, pose, source, camera, partialTick);
                return;
            }
            Object rawPose = MLuaRenderContextAccess.optional(context, "matrixStack", "getPoseStack", "poseStack");
            Object rawBuffers = MLuaRenderContextAccess.optional(context, "consumers", "getRenderTypeBuffer",
                "bufferSource", "getSubmitNodeCollector", "submitNodeCollector", "getRenderCollector");
            Object rawCamera = MLuaRenderContextAccess.optional(context, "camera", "getCamera");
            Float renderPartialTick = MLuaRenderCallbacks.readPartialTick(context);
            if (renderPartialTick == null) renderPartialTick = MLuaRenderCallbacks.currentClientPartialTick();
            MLuaRenderCallbacks.renderWorld(stage, context, rawPose, rawBuffers, rawCamera);
            renderWorldModels(stage, rawPose, rawBuffers, rawCamera, renderPartialTick);
        } catch (RuntimeException failure) {
            reportWorldRenderFailure(failure);
        }
    }

    private static void renderWorldModels(String stage, Object rawPose, Object rawBuffers, Object rawCamera,
                                          Float partialTick) {
        if (!(rawPose instanceof PoseStack pose)) return;
        boolean immediate = rawBuffers instanceof MultiBufferSource;
        boolean deferred = dev.mlua.loaderapi.LoaderApi.client().supportsCustomGeometry(rawBuffers);
        if (!immediate && !deferred) return;
        Vec3 camera = MLuaRenderContextAccess.cameraPosition(rawCamera);
        if (camera == null) {
            Minecraft minecraft = Minecraft.getInstance();
            Camera activeCamera = minecraft.gameRenderer == null ? null : minecraft.gameRenderer.getMainCamera();
            if (activeCamera == null) return;
            camera = activeCamera.getPosition();
        }
        for (RenderObject object : MLuaClientRenderManager.orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.initialized() || !object.visible() || object.target() != RenderTarget.WORLD_MODEL
                        || !object.renderStage().equals(stage)) continue;
                try {
                    if (immediate) {
                        renderWorldModel(pose, (MultiBufferSource)rawBuffers, object, camera,
                            partialTick == null ? 1.0F : partialTick);
                    } else if (object.type == RenderType.MESH && object.mesh != null) {
                        if (!submitWorldMesh(pose, rawBuffers, object, camera)) {
                            throw new IllegalStateException("The loader rejected deferred mesh geometry submission");
                        }
                    } else if (object.type == RenderType.BBMODEL && object.bbmodel != null) {
                        if (!submitWorldBBModel(pose, rawBuffers, object, camera,
                                partialTick == null ? 1.0F : partialTick)) {
                            throw new IllegalStateException("The loader rejected deferred BBModel geometry submission");
                        }
                    } else if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.warn("[MLua] Deferred world renderer has no geometry for RenderObject {}.", object.id);
                    }
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to draw world RenderObject {}", object.id, failure);
                    }
                }
            }
        }
    }

    private static boolean submitWorldMesh(PoseStack pose, Object renderSource, RenderObject object, Vec3 camera) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) return true;
            x += entity.getX();
            y += entity.getY();
            z += entity.getZ();
        }
        double offsetX = x - camera.x;
        double offsetY = y - camera.y;
        double offsetZ = z - camera.z;
        double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (distanceSquared > object.worldRenderDistance * object.worldRenderDistance) return true;
        MLuaMeshDefinition mesh = object.mesh;
        ResourceLocation texture = MLuaClientRenderManager.resolveMeshTexture(object);
        float scale = object.modelScale;
        MLuaCFrame modelTransform = object.modelTransform;
        int light = MLuaWorldLight.packedAt(x, y, z, 15728880);
        return submitMeshGeometry(renderSource, pose, mesh, texture, light, 0, object,
            matrix -> {
                matrix.translate((float)offsetX, (float)offsetY, (float)offsetZ);
                MLuaClientRenderManager.applyTransform(matrix, modelTransform);
                matrix.scale(scale, scale, scale);
            });
    }

    private static boolean submitWorldBBModel(PoseStack pose, Object renderSource, RenderObject object,
            Vec3 camera, float partialTick) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) return true;
            x += entity.getX();
            y += entity.getY();
            z += entity.getZ();
        }
        double offsetX = x - camera.x;
        double offsetY = y - camera.y;
        double offsetZ = z - camera.z;
        double distanceSquared = offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ;
        if (distanceSquared > object.worldRenderDistance * object.worldRenderDistance) return true;
        float scale = object.modelScale;
        MLuaCFrame modelTransform = object.modelTransform;
        int light = MLuaWorldLight.packedAt(x, y, z, 15728880);
        return submitBBModelGeometry(renderSource, pose, object, light, 0, partialTick, matrix -> {
            matrix.translate((float)offsetX, (float)offsetY, (float)offsetZ);
            MLuaClientRenderManager.applyTransform(matrix, modelTransform);
            matrix.scale(scale, scale, scale);
        });
    }

    /** Snapshots Lua-mutable model state before queuing any deferred collector callbacks. */
    static boolean submitBBModelGeometry(Object renderSource, PoseStack pose, RenderObject object,
            int light, int overlay, float partialTick,
            java.util.function.Consumer<Matrix4f> objectTransform) {
        MLuaBBModelAsset asset = object.bbmodel;
        if (asset == null) return false;
        List<ResourceLocation> textures = object.modelTextures;
        float modelScale = object.modelScale;
        Map<String, MLuaCFrame> partTransforms = Map.copyOf(object.modelPartTransforms);
        Set<String> hiddenParts = Set.copyOf(object.modelPartVisibility.keySet());
        MLuaAnimationPlayer player = new MLuaAnimationPlayer();
        player.copyStateFrom(object.animationPlayer);
        String activeAnimation = player.active();
        MLuaBBModelAsset.Animation animation = activeAnimation == null
            ? null : asset.animations().get(activeAnimation);
        List<MLuaBBModelRenderPlan.TextureBatch> batches = object.bbModelRenderPlan.textureBatches();
        if (batches.isEmpty()) return true;

        boolean submittedAny = false;
        for (MLuaBBModelRenderPlan.TextureBatch batch : batches) {
            ResourceLocation texture = batch.texture();
            boolean embeddedLease = object.resolvedTextures.contains(texture);
            Runnable releaseLease = embeddedLease ? MLuaClientRenderManager.acquireEmbeddedTextureLease(texture) : () -> { };
            if (embeddedLease) MLuaClientRenderManager.deferRenderCleanup(releaseLease);
            net.minecraft.client.renderer.RenderType renderType =
                net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
            List<MLuaBBModelAsset.Cube> cubes = batch.cubes();
            boolean submitted;
            try {
                submitted = dev.mlua.loaderapi.LoaderApi.client().submitCustomGeometry(
                    renderSource, pose, renderType, (nativePose, nativeConsumer) -> {
                        try {
                            if (!(nativePose instanceof PoseStack.Pose submittedPose)
                                    || !(nativeConsumer instanceof VertexConsumer consumer)) return;
                            Matrix4f base = new Matrix4f(submittedPose.pose());
                            objectTransform.accept(base);
                            MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
                            for (MLuaBBModelAsset.Cube cube : cubes) {
                                List<MLuaBBModelAsset.Bone> chain = asset.boneChainForCube(cube.uuid());
                                boolean hidden = false;
                                for (MLuaBBModelAsset.Bone bone : chain) {
                                    if (hiddenParts.contains(bone.uuid())) {
                                        hidden = true;
                                        break;
                                    }
                                }
                                if (hidden) continue;
                                Matrix4f matrix = new Matrix4f(base);
                                for (MLuaBBModelAsset.Bone bone : chain) {
                                    player.applyBoneTransform(matrix, animation, bone, partialTick,
                                        scratch.position, scratch.rotation, scratch.scale);
                                    MLuaCFrame partTransform = partTransforms.get(bone.uuid());
                                    if (partTransform != null) MLuaClientRenderManager.applyTransform(matrix, partTransform);
                                }
                                player.applyCubeTransform(matrix, animation, cube, partialTick,
                                    scratch.position, scratch.rotation, scratch.scale);
                                MLuaRenderHelper.renderBBModelCube(matrix, consumer, cube, textures,
                                    asset.textureWidth(), asset.textureHeight(), MLuaRenderHelper.fallbackTexture(),
                                    texture, light, overlay);
                            }
                        } catch (RuntimeException failure) {
                            if (object.markRenderFailureReported()) {
                                MLuaLogger.LOGGER.error("[MLua] Deferred BBModel rendering failed for RenderObject {}",
                                    object.id, failure);
                            }
                        } finally {
                            releaseLease.run();
                        }
                    });
            } catch (RuntimeException failure) {
                releaseLease.run();
                throw failure;
            }
            if (submitted) submittedAny = true;
            else releaseLease.run();
        }
        return submittedAny;
    }

    static boolean submitMeshGeometry(Object renderSource, PoseStack pose, MLuaMeshDefinition mesh,
            ResourceLocation texture, int light, int overlay, RenderObject owner,
            java.util.function.Consumer<Matrix4f> transform) {
        if (mesh == null || texture == null) return false;
        net.minecraft.client.renderer.RenderType renderType =
            net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture);
        return dev.mlua.loaderapi.LoaderApi.client().submitCustomGeometry(renderSource, pose, renderType,
            (nativePose, nativeConsumer) -> {
                if (!(nativePose instanceof PoseStack.Pose submittedPose)
                        || !(nativeConsumer instanceof VertexConsumer consumer)) return;
                try {
                    Matrix4f matrix = new Matrix4f(submittedPose.pose());
                    transform.accept(matrix);
                    MLuaRenderHelper.renderMesh(matrix, consumer, mesh, light, overlay);
                } catch (RuntimeException failure) {
                    if (owner.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Deferred mesh submission failed for RenderObject {}",
                            owner.id, failure);
                    }
                }
            });
    }

    private static void reportWorldRenderFailure(Throwable failure) {
        if (WORLD_RENDER_FAILURE_REPORTED.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.warn("[MLua] World render hook failed; HUD rendering remains available.", failure);
        }
    }

    private static void renderWorldModel(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                         Vec3 camera, float partialTick) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = MLuaClientRenderManager.resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) return;
            x += entity.getX(); y += entity.getY(); z += entity.getZ();
        }
        double cameraX = x - camera.x;
        double cameraY = y - camera.y;
        double cameraZ = z - camera.z;
        double distanceSquared = cameraX * cameraX + cameraY * cameraY + cameraZ * cameraZ;
        double maxDistance = object.worldRenderDistance;
        if (distanceSquared > maxDistance * maxDistance) return;
        int packedLight = MLuaWorldLight.packedAt(x, y, z, 15728880);
        pose.pushPose();
        try {
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            MLuaClientRenderManager.applyTransform(pose, object.modelTransform);
            if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
            if (object.type == RenderType.MESH && object.mesh != null) {
                MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                    MLuaClientRenderManager.resolveMeshTexture(object), packedLight, 0);
            } else if (object.bbmodel != null) {
                MLuaClientRenderManager.renderBBModelWorld(pose, buffers, object, packedLight, 0, partialTick);
            }
        } finally {
            pose.popPose();
        }
    }

}
//--endversion
