package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaValue;
import org.joml.Matrix4f;

public final class MLuaClientRenderManager {
    public static final double DEFAULT_WORLD_RENDER_DISTANCE = 256.0D;
    public static final double MAX_WORLD_RENDER_DISTANCE = 4096.0D;
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, RenderObject> OBJECTS = new ConcurrentHashMap<>();
    private static final MLuaRenderTargetIndex RENDER_TARGET_INDEX = new MLuaRenderTargetIndex();
    private static final MLuaEmbeddedTextureRegistry EMBEDDED_TEXTURES = new MLuaEmbeddedTextureRegistry();
    private static final MLuaRenderOwnershipRegistry OWNERS = new MLuaRenderOwnershipRegistry();
    private static final AtomicBoolean WORLD_RENDER_FAILURE_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean DEFERRED_CLEANUP_FAILURE_REPORTED = new AtomicBoolean();
    /** Fallback release for collector submissions that Minecraft drops before invoking their renderer. */
    private static final ConcurrentLinkedQueue<Runnable> DEFERRED_RENDER_CLEANUPS = new ConcurrentLinkedQueue<>();
    private static volatile RenderObject[] drawOrderSnapshot = new RenderObject[0];
    private static volatile boolean drawOrderDirty = true;
    private static boolean animationClockRegistered;
    private static boolean worldRenderersRegistered;

    private MLuaClientRenderManager() {
    }

    public static synchronized void registerOverlay() {
        if (!animationClockRegistered) {
            animationClockRegistered = true;
            dev.mlua.loaderapi.LoaderApi.lifecycle().onClientTickEnd(MLuaClientRenderManager::tickAnimations);
        }
        dev.mlua.loaderapi.LoaderApi.client().registerOverlay("mlua_render_service", obj -> {
            if (!(obj instanceof GuiGraphics graphics)) {
                return;
            }
            renderAll(graphics);
        });
        if (!worldRenderersRegistered) {
            for (String stage : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
                dev.mlua.loaderapi.LoaderApi.client().registerWorldRenderer(stage,
                    context -> renderWorldContext(stage, context));
            }
            dev.mlua.loaderapi.LoaderApi.client().registerWorldGeometryRenderer(context -> {
                Object pose = MLuaRenderContextAccess.optional(context, "matrixStack", "getPoseStack", "poseStack");
                // NeoForge 26.1 moved custom geometry submission from a buffer source to
                // SubmitNodeCollector. Keep this lookup in the shared adapter so the Lua
                // RenderContext contract remains the same across loader generations.
                Object buffers = MLuaRenderContextAccess.optional(context, "consumers", "getRenderTypeBuffer",
                    "bufferSource", "getSubmitNodeCollector", "submitNodeCollector", "getRenderCollector");
                Object camera = MLuaRenderContextAccess.optional(context, "camera", "getCamera");
                MLuaRenderCallbacks.renderWorldGeometry(context, pose, buffers, camera);
            });
            worldRenderersRegistered = true;
        }
    }

    private static void tickAnimations() {
        releaseDeferredRenderLeases();
        for (RenderObject object : OBJECTS.values()) {
            synchronized (object.executionLock()) {
                if (object.destroyed) continue;
                if (object.bbmodel != null) object.animationPlayer.tick(1.0F / 20.0F, object.bbmodel);
            }
            // A follow-offset function enters Lua and can itself destroy this object.
            // Never call into Lua while holding the render object's lifecycle lock.
            object.sampleFollowOffset();
        }
    }

    /** Releases collector leases after the frame has drained, including leases for dropped submissions. */
    private static void releaseDeferredRenderLeases() {
        Runnable cleanup;
        while ((cleanup = DEFERRED_RENDER_CLEANUPS.poll()) != null) {
            try {
                cleanup.run();
            } catch (RuntimeException failure) {
                if (DEFERRED_CLEANUP_FAILURE_REPORTED.compareAndSet(false, true)) {
                    MLuaLogger.LOGGER.warn("[MLua] Deferred render resource cleanup failed", failure);
                }
            }
        }
    }

    private static void invalidateDrawOrder() {
        synchronized (MLuaClientRenderManager.class) {
            drawOrderSnapshot = new RenderObject[0];
            drawOrderDirty = true;
        }
    }

    private static RenderObject[] orderedRenderObjects() {
        if (!drawOrderDirty) return drawOrderSnapshot;
        synchronized (MLuaClientRenderManager.class) {
            if (!drawOrderDirty) return drawOrderSnapshot;
            List<RenderObject> ordered = new ArrayList<>(OBJECTS.values());
            ordered.sort(Comparator.comparingInt(RenderObject::zIndex).thenComparingInt(RenderObject::id));
            drawOrderSnapshot = ordered.toArray(RenderObject[]::new);
            drawOrderDirty = false;
            return drawOrderSnapshot;
        }
    }

    private static void renderWorldContext(String stage, Object context) {
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
        for (RenderObject object : orderedRenderObjects()) {
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
            Entity entity = resolveFollowEntity(object.followEntityScope);
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
        ResourceLocation texture = resolveMeshTexture(object);
        float scale = object.modelScale;
        MLuaCFrame modelTransform = object.modelTransform;
        int light = MLuaWorldLight.packedAt(x, y, z, 15728880);
        return submitMeshGeometry(renderSource, pose, mesh, texture, light, 0, object,
            matrix -> {
                matrix.translate((float)offsetX, (float)offsetY, (float)offsetZ);
                applyTransform(matrix, modelTransform);
                matrix.scale(scale, scale, scale);
            });
    }

    private static boolean submitWorldBBModel(PoseStack pose, Object renderSource, RenderObject object,
            Vec3 camera, float partialTick) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = resolveFollowEntity(object.followEntityScope);
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
            applyTransform(matrix, modelTransform);
            matrix.scale(scale, scale, scale);
        });
    }

    /** Snapshots Lua-mutable model state before queuing any deferred collector callbacks. */
    private static boolean submitBBModelGeometry(Object renderSource, PoseStack pose, RenderObject object,
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
            if (embeddedLease) EMBEDDED_TEXTURES.acquire(texture);
            AtomicBoolean leaseReleased = new AtomicBoolean();
            Runnable releaseLease = () -> {
                if (embeddedLease && leaseReleased.compareAndSet(false, true)
                        && EMBEDDED_TEXTURES.release(texture)) releaseEmbeddedTexture(texture);
            };
            if (embeddedLease) DEFERRED_RENDER_CLEANUPS.add(releaseLease);
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
                                    if (partTransform != null) applyTransform(matrix, partTransform);
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

    private static boolean submitMeshGeometry(Object renderSource, PoseStack pose, MLuaMeshDefinition mesh,
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

    static Vec3 cameraLook(Object camera) {
        return MLuaRenderContextAccess.cameraLook(camera);
    }

    static Entity resolveFollowEntity(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope.side() != dev.mlua.common.enums.Side.CLIENT || minecraft.level == null
            || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static void renderWorldModel(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                         Vec3 camera, float partialTick) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = resolveFollowEntity(object.followEntityScope);
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
            applyTransform(pose, object.modelTransform);
            if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
            if (object.type == RenderType.MESH && object.mesh != null) {
                MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                    resolveMeshTexture(object), packedLight, 0);
            } else if (object.bbmodel != null) {
                renderBBModelWorld(pose, buffers, object, packedLight, 0, partialTick);
            }
        } finally {
            pose.popPose();
        }
    }

    private static void renderBBModelWorld(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                          float partialTick) {
        renderBBModelWorld(pose, buffers, object, 15728880, 0, partialTick);
    }

    static void renderBBModelWorld(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                   int light, int overlay, float partialTick) {
        List<ResourceLocation> textures = object.modelTextures;
        var animation = object.animationPlayer.active() == null ? null : object.bbmodel.animations().get(object.animationPlayer.active());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        for (var cube : object.bbmodel.cubes()) {
            List<MLuaBBModelAsset.Bone> boneChain = object.bbmodel.boneChainForCube(cube.uuid());
            if (!object.isModelPartVisible(boneChain)) continue;
            pose.pushPose();
            try {
                for (var bone : boneChain) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        scratch.position, scratch.rotation, scratch.scale);
                    applyTransform(pose, object.modelPartTransform(bone.uuid()));
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    scratch.position, scratch.rotation, scratch.scale);
                MLuaRenderHelper.renderBBModelCube(pose, buffers, cube, textures,
                    object.bbmodel.textureWidth(), object.bbmodel.textureHeight(), MLuaRenderHelper.fallbackTexture(), light, overlay);
            } finally {
                pose.popPose();
            }
        }
    }

    /** Draws Lua-owned 3D objects after Minecraft has posed an entity model. */
    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay) {
        renderEntityAttachments(model, scope, pose, buffers, light, overlay, 1.0F);
    }

    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, float partialTick) {
        if (scope == null || pose == null || buffers == null) return;
        List<RenderObject> orderedAttachments = RENDER_TARGET_INDEX.orderedEntityModels(scope);
        if (orderedAttachments.isEmpty()) return;
        MLuaModelAdapterRegistry.recordResolvedModel(model, scope);
        for (RenderObject object : orderedAttachments) {
            int renderId = object.id;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                        || !scope.equals(object.followEntityScope)) continue;
                pose.pushPose();
                try {
                    // A missing bone means the entity model's root. This keeps root-level
                    // attachments usable with living models that have no registered adapter.
                    if (object.followBoneName != null && (model == null
                            || !MLuaModelAdapterRegistry.applyPartTransform(model, object.followBoneName, pose, scope))) continue;
                    object.applyFollowOffset(pose);
                    pose.translate(object.worldX, object.worldY, object.worldZ);
                    applyTransform(pose, object.modelTransform);
                    if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.type == RenderType.MESH && object.mesh != null) {
                        MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                            resolveMeshTexture(object), light, overlay);
                    } else if (object.bbmodel != null) {
                        renderBBModelWorld(pose, buffers, object, light, overlay, partialTick);
                    }
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to render entity attachment {}", renderId, failure);
                    }
                } finally {
                    pose.popPose();
                }
            }
        }
    }

    /** Draws addon-owned model objects in the already transformed first- or third-person hand pose. */
    public static void renderHeldItemModels(RenderTarget target, String itemId, Object rawPose,
            Object renderSource, int packedLight, float partialTick) {
        if ((target != RenderTarget.FIRST_PERSON_ITEM && target != RenderTarget.THIRD_PERSON_ITEM)
                || itemId == null || !(rawPose instanceof PoseStack pose)) return;
        boolean immediate = renderSource instanceof MultiBufferSource;
        boolean deferred = !immediate && dev.mlua.loaderapi.LoaderApi.client().supportsCustomGeometry(renderSource);
        if (!immediate && !deferred) return;
        for (RenderObject object : RENDER_TARGET_INDEX.orderedHeldItemModels(target, itemId)) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.initialized() || !object.visible || object.target() != target
                        || !itemId.equals(object.heldItemId)
                        || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) continue;
                pose.pushPose();
                try {
                    if (immediate) {
                        object.applyFollowOffset(pose);
                        applyTransform(pose, object.modelTransform);
                        if (object.modelScale != 1.0F) {
                            pose.scale(object.modelScale, object.modelScale, object.modelScale);
                        }
                        if (object.type == RenderType.MESH && object.mesh != null) {
                            MLuaRenderHelper.renderMesh(pose, (MultiBufferSource)renderSource, object.mesh,
                                resolveMeshTexture(object), packedLight, 0);
                        } else if (object.bbmodel != null) {
                            renderBBModelWorld(pose, (MultiBufferSource)renderSource, object,
                                packedLight, 0, partialTick);
                        }
                    } else {
                        MLuaCFrame offset = object.followOffset;
                        MLuaCFrame modelTransform = object.modelTransform;
                        float modelScale = object.modelScale;
                        java.util.function.Consumer<Matrix4f> transform = matrix -> {
                            applyTransform(matrix, offset);
                            applyTransform(matrix, modelTransform);
                            matrix.scale(modelScale);
                        };
                        boolean submitted;
                        if (object.type == RenderType.MESH && object.mesh != null) {
                            submitted = submitMeshGeometry(renderSource, pose, object.mesh,
                                resolveMeshTexture(object), packedLight, 0, object, transform);
                        } else {
                            submitted = submitBBModelGeometry(renderSource, pose, object,
                                packedLight, 0, partialTick, transform);
                        }
                        if (!submitted) throw new IllegalStateException(
                            "The loader rejected held-item model geometry submission");
                    }
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to draw held-item RenderObject {}", object.id, failure);
                    }
                } finally {
                    pose.popPose();
                }
            }
        }
    }

    public static boolean hasHeldItemModels(RenderTarget target, String itemId) {
        if ((target != RenderTarget.FIRST_PERSON_ITEM && target != RenderTarget.THIRD_PERSON_ITEM)
                || itemId == null) return false;
        for (RenderObject object : RENDER_TARGET_INDEX.orderedHeldItemModels(target, itemId)) {
            if (!object.destroyed() && object.visible() && object.target() == target
                    && itemId.equals(object.heldItemId())) return true;
        }
        return false;
    }

    /** Whether this loader can submit a managed model through the supplied render source. */
    public static boolean supportsDrawObjectSource(Object source) {
        return source instanceof MultiBufferSource
            || dev.mlua.loaderapi.LoaderApi.client().supportsCustomGeometry(source);
    }

    /** Draws a Lua-owned model using the live pose and buffers of the current render callback. */
    public static boolean drawObjectInRenderContext(int renderId, MLuaRenderOwnershipRegistry.Owner owner,
            Object poseValue, Object bufferValue, MLuaCFrame transform, int light, int overlay, float partialTick) {
        RenderObject object = get(renderId, owner);
        if (object == null) throw new IllegalArgumentException("RenderObject is destroyed or belongs to another addon runtime");
        if (object.type != RenderType.MESH && object.type != RenderType.BBMODEL) {
            throw new IllegalArgumentException("RenderContext:DrawObject requires a Mesh or BBModel RenderObject");
        }
        if (!(poseValue instanceof PoseStack pose)) {
            throw new IllegalStateException("This render callback does not expose a PoseStack");
        }
        synchronized (object.executionLock()) {
            if (object.destroyed) throw new IllegalStateException("RenderObject was destroyed before it could be drawn");
            if (!object.visible) return false;
            if (!(bufferValue instanceof MultiBufferSource buffers)) {
                if (!dev.mlua.loaderapi.LoaderApi.client().supportsCustomGeometry(bufferValue)) {
                    throw new IllegalStateException("This render callback does not expose a usable geometry source");
                }
                float scale = object.modelScale;
                MLuaCFrame offset = transform == null ? MLuaCFrame.IDENTITY : transform;
                MLuaCFrame modelTransform = object.modelTransform;
                boolean submitted;
                if (object.mesh != null) {
                    MLuaMeshDefinition mesh = object.mesh;
                    ResourceLocation texture = resolveMeshTexture(object);
                    submitted = submitMeshGeometry(bufferValue, pose, mesh, texture, light, overlay, object, matrix -> {
                        applyTransform(matrix, offset);
                        applyTransform(matrix, modelTransform);
                        matrix.scale(scale, scale, scale);
                    });
                } else {
                    submitted = submitBBModelGeometry(bufferValue, pose, object, light, overlay, partialTick, matrix -> {
                        applyTransform(matrix, offset);
                        applyTransform(matrix, modelTransform);
                        matrix.scale(scale, scale, scale);
                    });
                }
                if (!submitted) throw new IllegalStateException("The loader rejected deferred model geometry submission");
                return true;
            }
            pose.pushPose();
            try {
                applyTransform(pose, transform == null ? MLuaCFrame.IDENTITY : transform);
                applyTransform(pose, object.modelTransform);
                if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                if (object.mesh != null) {
                    MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                        resolveMeshTexture(object), light, overlay);
                } else if (object.bbmodel != null) {
                    renderBBModelWorld(pose, buffers, object, light, overlay, partialTick);
                }
                return true;
            } catch (RuntimeException failure) {
                if (object.markRenderFailureReported()) {
                    MLuaLogger.LOGGER.error("[MLua] Failed to draw RenderObject {}", renderId, failure);
                }
                return false;
            } finally {
                pose.popPose();
            }
        }
    }

    private static void applyTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        double[] angles = transform.toEulerAnglesXYZ();
        pose.mulPose(Axis.XP.rotation((float)angles[0]));
        pose.mulPose(Axis.YP.rotation((float)angles[1]));
        pose.mulPose(Axis.ZP.rotation((float)angles[2]));
    }

    private static void applyTransform(Matrix4f matrix, MLuaCFrame transform) {
        matrix.translate((float)transform.x(), (float)transform.y(), (float)transform.z());
        double[] angles = transform.toEulerAnglesXYZ();
        matrix.rotateX((float)angles[0]).rotateY((float)angles[1]).rotateZ((float)angles[2]);
    }

    public static int create(RenderType type, String resource) {
        return create(type, resource, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static synchronized int create(RenderType type, String resource, MLuaRenderOwnershipRegistry.Owner owner) {
        java.util.Objects.requireNonNull(type, "Render type cannot be null");
        return publish(new RenderObject(nextRenderId(), type, resource), owner);
    }

    public static int createMesh(MLuaMeshDefinition mesh) {
        return createMesh(mesh, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static synchronized int createMesh(MLuaMeshDefinition mesh, MLuaRenderOwnershipRegistry.Owner owner) {
        java.util.Objects.requireNonNull(mesh, "Mesh definition cannot be null");
        RenderObject object = new RenderObject(nextRenderId(), RenderType.MESH, mesh.texture());
        object.mesh = mesh;
        return publish(object, owner);
    }

    public static int createBBModel(MLuaBBModelAsset asset) {
        return createBBModel(asset, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static synchronized int createBBModel(MLuaBBModelAsset asset, MLuaRenderOwnershipRegistry.Owner owner) {
        java.util.Objects.requireNonNull(asset, "Blockbench model asset cannot be null");
        RenderObject object = new RenderObject(nextRenderId(), RenderType.BBMODEL, null);
        object.bbmodel = asset;
        object.initialized = false;
        int id = publish(object, owner);
        Runnable initialize = () -> {
            synchronized (object.executionLock()) {
                if (object.destroyed || OBJECTS.get(id) != object) return;
                try {
                    object.resolvedTextures = registerEmbeddedTextures(asset);
                    object.modelTextures = resolveModelTextures(asset, object.resolvedTextures);
                    object.bbModelRenderPlan = MLuaBBModelRenderPlan.prepare(asset, object.modelTextures);
                    object.initialized = true;
                } catch (RuntimeException | Error failure) {
                    releaseEmbeddedTextures(object);
                    object.resolvedTextures = List.of();
                    object.bbModelRenderPlan = MLuaBBModelRenderPlan.empty();
                    MLuaLogger.LOGGER.error("[MLua] Failed to initialize BBModel render resources for RenderObject {}", id, failure);
                    remove(id, owner);
                }
            }
        };
        Minecraft minecraft = Minecraft.getInstance();
        if (dev.mlua.runtime.client.MLuaClientThread.isClientThread()) initialize.run();
        else minecraft.execute(initialize);
        return id;
    }

    private static int nextRenderId() {
        return MLuaHandleIdAllocator.nextAvailable(NEXT_ID,
            candidate -> OBJECTS.containsKey(candidate) || OWNERS.ownerOf(candidate) != null,
            MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS);
    }

    /** Publishes a fully initialized object, rolling back its ownership reservation on failure. */
    private static int publish(RenderObject object, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = object.id;
        if (!OWNERS.tryRegister(id, owner, MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS,
                MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER)) {
            throw new IllegalStateException("[MLua] RenderService limit reached ("
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER + " live objects per addon runtime, "
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS + " total). Destroy unused render objects before creating more.");
        }
        try {
            if (OBJECTS.putIfAbsent(id, object) != null) {
                throw new IllegalStateException("[MLua] Render object handle was allocated while already in use: " + id);
            }
            invalidateDrawOrder();
            return id;
        } catch (RuntimeException | Error failure) {
            OBJECTS.remove(id, object);
            OWNERS.remove(id);
            throw failure;
        }
    }

    private static List<ResourceLocation> registerEmbeddedTextures(MLuaBBModelAsset asset) {
        List<ResourceLocation> result = new ArrayList<>();
        for (int index = 0; index < asset.textures().size(); index++) {
            String data = index < asset.embeddedTextures().size() ? asset.embeddedTextures().get(index) : "";
            if (data == null || !data.startsWith("data:")) {
                result.add(null);
                continue;
            }
            try {
                int comma = data.indexOf(',');
                byte[] bytes = Base64.getDecoder().decode(data.substring(comma + 1));
                ResourceLocation location = embeddedTextureLocation(bytes, index);
                boolean acquired = EMBEDDED_TEXTURES.acquire(location);
                if (acquired) {
                    try {
                        NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                        Minecraft.getInstance().getTextureManager().register(location, new DynamicTexture(image));
                    } catch (RuntimeException | java.io.IOException e) {
                        if (EMBEDDED_TEXTURES.release(location)) releaseEmbeddedTexture(location);
                        throw e;
                    }
                }
                result.add(location);
            } catch (RuntimeException | java.io.IOException ignored) {
                result.add(null);
            }
        }
        return result;
    }

    private static List<ResourceLocation> resolveModelTextures(MLuaBBModelAsset asset,
                                                               List<ResourceLocation> embeddedTextures) {
        List<ResourceLocation> result = new ArrayList<>(asset.textures().size());
        for (int index = 0; index < asset.textures().size(); index++) {
            ResourceLocation embedded = index < embeddedTextures.size() ? embeddedTextures.get(index) : null;
            result.add(embedded != null ? embedded : resolveModelTexture(asset.textures().get(index)));
        }
        return java.util.Collections.unmodifiableList(result);
    }

    static ResourceLocation embeddedTextureLocation(byte[] imageBytes, int index) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(imageBytes);
            return MLuaResourceLocations.build("mlua", "bbmodel/" + HexFormat.of().formatHex(digest) + "/" + index);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static RenderObject get(int id) {
        return OBJECTS.get(id);
    }

    public static RenderObject get(int id, MLuaRenderOwnershipRegistry.Owner owner) {
        return OWNERS.owns(id, owner) ? OBJECTS.get(id) : null;
    }

    public static List<Integer> ids() {
        return OBJECTS.keySet().stream().sorted().toList();
    }

    public static List<Integer> ids(MLuaRenderOwnershipRegistry.Owner owner) {
        return OWNERS.ids(owner);
    }

    public static void remove(int id) {
        RenderObject removed = OBJECTS.remove(id);
        if (removed != null) {
            synchronized (removed.executionLock()) {
                removed.markDestroyed();
                releaseEmbeddedTextures(removed);
            }
            invalidateDrawOrder();
        }
        OWNERS.remove(id);
        RENDER_TARGET_INDEX.removeObject(id);
    }

    public static void remove(int id, MLuaRenderOwnershipRegistry.Owner owner) {
        if (OWNERS.owns(id, owner)) remove(id);
    }

    public static void clearOwned(MLuaRenderOwnershipRegistry.Owner owner) {
        OWNERS.clearOwned(owner).forEach(MLuaClientRenderManager::remove);
    }

    public static void clearRuntime(Object runtimeOwner) {
        OWNERS.clearRuntime(runtimeOwner).forEach(MLuaClientRenderManager::remove);
    }

    /** Removes render objects tied to the current world while preserving HUD and manual objects. */
    public static void clearWorldState() {
        List<Integer> ids = OBJECTS.values().stream()
            .filter(object -> {
                RenderTarget target = object.target();
                return target == RenderTarget.WORLD_OVERLAY || target == RenderTarget.WORLD_MODEL
                    || target == RenderTarget.ENTITY_MODEL;
            })
            .map(RenderObject::id)
            .toList();
        ids.forEach(MLuaClientRenderManager::remove);
    }

    /** Removes render objects attached to an entity that has left the client level. */
    public static void clearEntity(Entity entity) {
        if (entity == null) return;
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.removeEntityModels(MLuaEntityScope.of(entity));
        if (renderIds == null) return;
        List.copyOf(renderIds).forEach(MLuaClientRenderManager::remove);
    }

    public static void clearAll() {
        List<Integer> ids = List.copyOf(OBJECTS.keySet());
        ids.forEach(MLuaClientRenderManager::remove);
        // Clear pending per-submission leases before draining the registry. Otherwise
        // an old queued release could decrement a texture registered after this reset.
        releaseDeferredRenderLeases();
        invalidateDrawOrder();
        RENDER_TARGET_INDEX.clear();
        OWNERS.clear();
        EMBEDDED_TEXTURES.drain().forEach(MLuaClientRenderManager::releaseEmbeddedTexture);
    }

    private static void releaseEmbeddedTextures(RenderObject object) {
        for (ResourceLocation location : object.resolvedTextures) {
            if (location != null && EMBEDDED_TEXTURES.release(location)) releaseEmbeddedTexture(location);
        }
    }

    private static void releaseEmbeddedTexture(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        Runnable release = () -> {
            if (EMBEDDED_TEXTURES.isReferenced(location)) return;
            try {
                minecraft.getTextureManager().release(location);
            } catch (RuntimeException failure) {
                MLuaLogger.LOGGER.warn("Could not release embedded MLua render texture {}", location, failure);
            }
        };
        if (dev.mlua.runtime.client.MLuaClientThread.isClientThread()) release.run();
        else minecraft.execute(release);
    }

    public static void attachToEntity(int renderId, int entityId) {
        attachToEntity(renderId, MLuaEntityScope.legacy(entityId));
    }

    public static void attachToEntity(int renderId, Entity entity) {
        attachToEntity(renderId, MLuaEntityScope.of(entity));
    }

    public static boolean attachToEntity(int renderId, Entity entity, Object runtimeOwner) {
        MLuaRenderOwnershipRegistry.Owner owner = OWNERS.ownerOf(renderId);
        if (owner == null || owner.runtimeOwner() != runtimeOwner) return false;
        return attachToEntity(renderId, entity, owner);
    }

    public static boolean attachToEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (entity == null || !OWNERS.owns(renderId, owner)) return false;
        RenderObject object = OBJECTS.get(renderId);
        if (object == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return false;
        synchronized (object.executionLock()) {
            if (object.destroyed) return false;
            object.setFollowEntityScope(MLuaEntityScope.of(entity));
            return true;
        }
    }

    /** Detaches an addon-owned model from exactly this entity and leaves it manually drawable. */
    public static boolean detachFromEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (entity == null || !OWNERS.owns(renderId, owner)) return false;
        RenderObject object = OBJECTS.get(renderId);
        if (object == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return false;
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        synchronized (object.executionLock()) {
            if (object.destroyed || !scope.equals(object.followEntityScope)) return false;
            object.setTarget(RenderTarget.MANUAL);
            return true;
        }
    }

    private static void attachToEntity(int renderId, MLuaEntityScope scope) {
        RenderObject object = OBJECTS.get(renderId);
        if (object == null || scope == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return;
        synchronized (object.executionLock()) {
            if (!object.destroyed) object.setFollowEntityScope(scope);
        }
    }

    public static void resetEntityModel(int entityId) {
        resetEntityModel(MLuaEntityScope.legacy(entityId));
    }

    public static void resetEntityModel(Entity entity) {
        resetEntityModel(MLuaEntityScope.of(entity));
    }

    public static void resetEntityModel(MLuaEntityScope scope) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.removeEntityModels(scope);
        if (renderIds != null) renderIds.forEach(MLuaClientRenderManager::remove);
    }

    /** Removes only this addon's model attachments from one entity. */
    public static int resetEntityModel(MLuaEntityScope scope, MLuaRenderOwnershipRegistry.Owner owner) {
        if (scope == null || owner == null) return 0;
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return 0;
        int removed = 0;
        for (int renderId : List.copyOf(renderIds)) {
            if (!OWNERS.owns(renderId, owner)) continue;
            remove(renderId);
            removed++;
        }
        return removed;
    }

    public static boolean hasEntityModel(int entityId) {
        return hasEntityModel(MLuaEntityScope.legacy(entityId));
    }

    public static boolean hasEntityModel(Entity entity) {
        return hasEntityModel(MLuaEntityScope.of(entity));
    }

    public static boolean hasEntityBoneAttachments(Entity entity) {
        if (entity == null) return false;
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(MLuaEntityScope.of(entity));
        if (renderIds == null) return false;
        for (Integer renderId : renderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.visible && object.followBoneName != null) return true;
            }
        }
        return false;
    }

    private static boolean hasEntityModel(MLuaEntityScope scope) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        return renderIds != null && renderIds.stream().map(OBJECTS::get)
                .anyMatch(object -> object != null && !object.destroyed && object.visible());
    }

    public static void playEntityAnimation(int entityId, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.legacy(entityId), name, loop, speed);
    }

    public static void playEntityAnimation(Entity entity, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.of(entity), name, loop, speed);
    }

    private static void playEntityAnimation(MLuaEntityScope scope, String name, boolean loop, float speed) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.bbmodel != null) {
                    object.animationPlayer.play(name, object.bbmodel, loop, speed);
                }
            }
        }
    }

    public static void stopEntityAnimation(int entityId) {
        stopEntityAnimation(MLuaEntityScope.legacy(entityId));
    }

    public static void stopEntityAnimation(Entity entity) {
        stopEntityAnimation(MLuaEntityScope.of(entity));
    }

    private static void stopEntityAnimation(MLuaEntityScope scope) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.bbmodel != null) object.animationPlayer.stop();
            }
        }
    }

    public static void renderAll(GuiGraphics graphics) {
        MLuaRenderCallbacks.renderHud(graphics, graphics.guiWidth(), graphics.guiHeight());
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible) continue;
                if (object.target() == RenderTarget.MANUAL || object.target() == RenderTarget.WORLD_MODEL
                        || object.target() == RenderTarget.ENTITY_MODEL
                        || object.target() == RenderTarget.FIRST_PERSON_ITEM
                        || object.target() == RenderTarget.THIRD_PERSON_ITEM) continue;
                render(graphics, object);
            }
        }
    }

    static void render(GuiGraphics graphics, RenderObject object) {
        if (!object.initialized()) return;
        if (object.target() == RenderTarget.ENTITY_MODEL) return;
        if (object.target() == RenderTarget.WORLD_OVERLAY) {
            MLuaWorldOverlayRenderer.renderWorld(graphics, object);
            return;
        }
        switch (object.type) {
            case RECT -> MLuaHudObjectRenderer.renderRect(graphics, object);
            case IMAGE -> MLuaHudObjectRenderer.renderImage(graphics, object);
            case ITEM -> MLuaHudObjectRenderer.renderItem(graphics, object, false);
            case BLOCK -> MLuaHudObjectRenderer.renderItem(graphics, object, true);
            case TEXT -> MLuaHudObjectRenderer.renderText(graphics, object);
            case LINE, BEAM -> MLuaHudObjectRenderer.renderLine2d(graphics, object.x, object.y, object.endX, object.endY, object);
            case BOX -> MLuaHudObjectRenderer.renderRect(graphics, object);
            case MESH, BBMODEL -> MLuaHudObjectRenderer.renderModelPreview(graphics, object);
        }
    }

    static ResourceLocation resolveTexture(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        ResourceLocation location = path.contains(":") ? MLuaResourceLocations.parse(path) : MLuaResourceLocations.build("mlua", path);
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : null;
    }

    /** Resolves a mesh's unqualified texture in the addon that owns the render object. */
    private static ResourceLocation resolveMeshTexture(RenderObject object) {
        if (object == null || object.mesh == null) return MLuaRenderHelper.fallbackTexture();
        String path = object.mesh.texture();
        if (path == null || path.isBlank()) return MLuaRenderHelper.fallbackTexture();
        MLuaRenderOwnershipRegistry.Owner owner = OWNERS.ownerOf(object.id);
        String candidate = path.indexOf(':') >= 0 || owner == null || owner.ownerModId().isBlank()
            ? path : owner.ownerModId() + ":" + path;
        ResourceLocation location = MLuaResourceLocations.tryParseOrNull(candidate);
        return location != null && Minecraft.getInstance().getResourceManager().getResource(location).isPresent()
            ? location : MLuaRenderHelper.fallbackTexture();
    }

    private static ResourceLocation resolveModelTexture(String path) {
        if (path == null || path.isBlank()) return null;
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("data:")) return null;
        if (normalized.startsWith("assets/")) {
            String remainder = normalized.substring("assets/".length());
            int slash = remainder.indexOf('/');
            if (slash > 0) {
                return MLuaResourceLocations.tryParseOrNull(remainder.substring(0, slash) + ":" + remainder.substring(slash + 1));
            }
        }
        int namespaceSlash = normalized.indexOf('/');
        if (namespaceSlash > 0 && normalized.substring(namespaceSlash + 1).startsWith("textures/")) {
            return MLuaResourceLocations.tryParseOrNull(normalized.substring(0, namespaceSlash) + ":" + normalized.substring(namespaceSlash + 1));
        }
        return MLuaResourceLocations.tryParseOrNull(normalized);
    }

    static ItemStack resolveStack(String id, boolean block) {
        if (id == null || id.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation location = MLuaResourceLocations.parse(id);
        if (block) {
            var blockValue = BuiltInRegistries.BLOCK.get(location);
            Item item = blockValue.asItem();
            return item instanceof BlockItem || item != null ? new ItemStack(item) : ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        return item != null ? new ItemStack(item) : ItemStack.EMPTY;
    }

    public enum RenderType {
        RECT,
        IMAGE,
        ITEM,
        BLOCK,
        TEXT,
        LINE,
        BEAM,
        BOX,
        MESH,
        BBMODEL
    }

    public enum RenderTarget {
        MANUAL,
        HUD,
        WORLD_OVERLAY,
        WORLD_MODEL,
        ENTITY_MODEL,
        FIRST_PERSON_ITEM,
        THIRD_PERSON_ITEM
    }

    public static final class RenderObject {
        private final int id;
        private final RenderType type;
        private volatile String luaClassName = "RenderObject";
        private volatile String resource;
        private volatile double x = 0.0;
        private volatile double y = 0.0;
        private volatile double width = 16.0;
        private volatile double height = 16.0;
        private volatile int color = 0xFFFFFF;
        private volatile double transparency = 0.0;
        private volatile double rotation = 0.0;
        private volatile boolean visible = true;
        private volatile boolean initialized = true;
        private volatile boolean destroyed;
        private final AtomicBoolean renderFailureReported = new AtomicBoolean();
        private volatile int zIndex = 0;
        private volatile RenderTarget renderTarget = RenderTarget.HUD;
        private volatile String followBoneName;
        private volatile MLuaCFrame followOffset = MLuaCFrame.IDENTITY;
        private volatile FollowOffsetBinding followOffsetBinding = FollowOffsetBinding.NONE;
        private volatile double worldX = 0.0;
        private volatile double worldY = 0.0;
        private volatile double worldZ = 0.0;
        private volatile double endX = 0.0;
        private volatile double endY = 0.0;
        private volatile double endWorldX = 0.0;
        private volatile double endWorldY = 0.0;
        private volatile double endWorldZ = 0.0;
        private volatile double depth = 16.0;
        private volatile double thickness = 1.0;
        private volatile boolean scaleWithDistance = true;
        private volatile double worldRenderDistance = DEFAULT_WORLD_RENDER_DISTANCE;
        private volatile MLuaEntityScope followEntityScope;
        private volatile MLuaMeshDefinition mesh;
        private volatile MLuaBBModelAsset bbmodel;
        private volatile List<ResourceLocation> resolvedTextures = List.of();
        private volatile List<ResourceLocation> modelTextures = List.of();
        private volatile MLuaBBModelRenderPlan bbModelRenderPlan = MLuaBBModelRenderPlan.empty();
        private volatile float modelScale = 1.0F;
        private volatile MLuaCFrame modelTransform = MLuaCFrame.IDENTITY;
        private volatile String renderStage;
        private volatile String heldItemId;
        private final MLuaAnimationPlayer animationPlayer = new MLuaAnimationPlayer();
        private final ConcurrentHashMap<String, MLuaCFrame> modelPartTransforms = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> modelPartVisibility = new ConcurrentHashMap<>();

        private RenderObject(int id, RenderType type, String resource) {
            this.id = id;
            this.type = type;
            this.resource = resource;
            this.renderStage = dev.mlua.loaderapi.LoaderApi.client().defaultWorldModelRenderStage();
        }

        public int id() { return id; }
        public String luaClassName() { return luaClassName; }
        public synchronized void setLuaClassName(String luaClassName) {
            if (luaClassName == null || luaClassName.isBlank()) {
                throw new IllegalArgumentException("Render object Lua class name cannot be empty");
            }
            this.luaClassName = luaClassName;
        }
        public Object executionLock() { return this; }
        public RenderType type() { return type; }
        public String resource() { return resource; }
        public double x() { return x; }
        public double y() { return y; }
        public double width() { return width; }
        public double height() { return height; }
        public int color() { return color; }
        public double transparency() { return transparency; }
        public double rotation() { return rotation; }
        public boolean visible() { return visible; }
        public boolean initialized() { return initialized; }
        public boolean destroyed() { return destroyed; }
        public boolean markRenderFailureReported() { return renderFailureReported.compareAndSet(false, true); }
        public int zIndex() { return zIndex; }
        public boolean worldSpace() {
            return renderTarget == RenderTarget.WORLD_OVERLAY || renderTarget == RenderTarget.WORLD_MODEL
                || renderTarget == RenderTarget.ENTITY_MODEL;
        }
        public RenderTarget target() { return renderTarget; }
        public double worldX() { return worldX; }
        public double worldY() { return worldY; }
        public double worldZ() { return worldZ; }
        public double endX() { return endX; }
        public double endY() { return endY; }
        public double endWorldX() { return endWorldX; }
        public double endWorldY() { return endWorldY; }
        public double endWorldZ() { return endWorldZ; }
        public double depth() { return depth; }
        public double thickness() { return thickness; }
        public boolean scaleWithDistance() { return scaleWithDistance; }
        public MLuaEntityScope followEntityScope() { return followEntityScope; }
        public String followBoneName() { return followBoneName; }
        public MLuaCFrame followOffset() { return followOffset; }
        public MLuaMeshDefinition mesh() { return mesh; }
        public MLuaBBModelAsset bbmodel() { return bbmodel; }
        public MLuaCFrame modelPartTransform(String boneId) {
            // This accessor is called for every model bone on every rendered cube.
            // The renderer already walks bone ids from the immutable parsed asset,
            // so validating by scanning all bones here would make rendering O(cubes*bones).
            return modelPartTransforms.getOrDefault(boneId, MLuaCFrame.IDENTITY);
        }
        public boolean isModelPartVisible(List<MLuaBBModelAsset.Bone> boneChain) {
            if (boneChain == null) return true;
            for (MLuaBBModelAsset.Bone bone : boneChain) {
                if (Boolean.FALSE.equals(modelPartVisibility.get(bone.uuid()))) return false;
            }
            return true;
        }
        public boolean modelPartVisible(String boneId) {
            requireModelBone(boneId);
            MLuaBBModelAsset.Bone bone = bbmodel.boneById(boneId);
            while (bone != null) {
                if (Boolean.FALSE.equals(modelPartVisibility.get(bone.uuid()))) return false;
                String parentId = bone.parent();
                bone = parentId == null ? null : bbmodel.boneById(parentId);
            }
            return true;
        }
        public void setModelPartVisible(String boneId, boolean visible) {
            requireModelBone(boneId);
            if (visible) modelPartVisibility.remove(boneId);
            else modelPartVisibility.put(boneId, Boolean.FALSE);
        }
        public void resetModelPartVisibility(String boneId) {
            requireModelBone(boneId);
            modelPartVisibility.remove(boneId);
        }
        public void setModelPartTransform(String boneId, MLuaCFrame transform) {
            requireModelBone(boneId);
            if (transform == null) throw new IllegalArgumentException("Model part transform cannot be null");
            modelPartTransforms.put(boneId, transform);
        }
        public void resetModelPartTransform(String boneId) {
            requireModelBone(boneId);
            modelPartTransforms.remove(boneId);
        }
        private void requireModelBone(String boneId) {
            MLuaBBModelAsset asset = this.bbmodel;
            if (asset == null) throw new IllegalStateException("Model parts are available only on a loaded Blockbench model");
            if (asset.boneById(boneId) == null) {
                throw new IllegalArgumentException("Unknown Blockbench model part id: " + boneId);
            }
        }
        public MLuaAnimationPlayer animationPlayer() { return animationPlayer; }
        public float modelScale() { return modelScale; }
        public MLuaCFrame modelTransform() { return modelTransform; }
        public double worldRenderDistance() { return worldRenderDistance; }
        public String renderStage() { return renderStage; }
        public String heldItemId() { return heldItemId; }

        public synchronized void setResource(String resource) { this.resource = resource; }
        public synchronized void setPosition(double x, double y) {
            double nextX = finite(x, "X");
            double nextY = finite(y, "Y");
            this.x = nextX;
            this.y = nextY;
        }
        public synchronized void setSize(double width, double height) {
            double nextWidth = finite(width, "Width");
            double nextHeight = finite(height, "Height");
            this.width = nextWidth;
            this.height = nextHeight;
        }
        public synchronized void setColor(int color) { this.color = color; }
        public synchronized void setTransparency(double transparency) {
            this.transparency = Math.max(0.0, Math.min(1.0, finite(transparency, "Transparency")));
        }
        public synchronized void setRotation(double rotation) { this.rotation = finite(rotation, "Rotation"); }
        public synchronized void setVisible(boolean visible) { this.visible = visible; }
        private synchronized void markDestroyed() {
            if (destroyed) return;
            destroyed = true;
            visible = false;
            clearEntityAttachment();
            followOffsetBinding = FollowOffsetBinding.NONE;
            animationPlayer.stop();
            modelPartTransforms.clear();
            modelPartVisibility.clear();
        }
        public synchronized void setZIndex(int zIndex) {
            if (this.zIndex == zIndex) return;
            this.zIndex = zIndex;
            invalidateDrawOrder();
            RENDER_TARGET_INDEX.invalidateOrder();
        }
        public synchronized void setWorldSpace(boolean worldSpace) {
            if (worldSpace && renderTarget == RenderTarget.ENTITY_MODEL) return;
            if (worldSpace && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            }
            if (!worldSpace) clearEntityAttachment();
            renderTarget = !worldSpace ? RenderTarget.HUD
                : (type == RenderType.MESH || type == RenderType.BBMODEL
                    ? RenderTarget.WORLD_MODEL : RenderTarget.WORLD_OVERLAY);
            RENDER_TARGET_INDEX.updateHeldItem(this);
        }
        public synchronized void setTarget(RenderTarget target) {
            if (target == null) throw new IllegalArgumentException("Render target cannot be null");
            boolean model = type == RenderType.MESH || type == RenderType.BBMODEL;
            if (target == RenderTarget.MANUAL) {
                if (!model) throw new IllegalArgumentException("Manual target requires a Mesh or BBModel object");
                clearEntityAttachment();
                followBoneName = null;
                renderTarget = RenderTarget.MANUAL;
                RENDER_TARGET_INDEX.updateHeldItem(this);
                return;
            }
            if ((target == RenderTarget.WORLD_MODEL || target == RenderTarget.ENTITY_MODEL
                    || target == RenderTarget.FIRST_PERSON_ITEM || target == RenderTarget.THIRD_PERSON_ITEM) && !model) {
                throw new IllegalArgumentException("WorldModel, EntityModel, FirstPersonItem, and ThirdPersonItem targets require a Mesh or BBModel object");
            }
            if (target == RenderTarget.WORLD_MODEL) {
                dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            }
            if (target == RenderTarget.WORLD_OVERLAY && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                throw new IllegalArgumentException("Mesh and BBModel objects use the WorldModel or EntityModel target");
            }
            if (target != RenderTarget.ENTITY_MODEL) {
                clearEntityAttachment();
                followBoneName = null;
            }
            renderTarget = target;
            RENDER_TARGET_INDEX.updateHeldItem(this);
        }
        public synchronized void setHeldItemId(String itemId) {
            if (itemId == null || itemId.isBlank()) {
                this.heldItemId = null;
                RENDER_TARGET_INDEX.updateHeldItem(this);
                return;
            }
            String oldItemId = this.heldItemId;
            try {
                this.heldItemId = dev.mlua.minecraft.MLuaResourceLocations.parse(itemId).toString();
            } catch (IllegalArgumentException invalid) {
                this.heldItemId = oldItemId;
                throw new IllegalArgumentException("Invalid held item id: " + itemId, invalid);
            }
            RENDER_TARGET_INDEX.updateHeldItem(this);
        }
        public synchronized void setWorldPosition(double x, double y, double z) {
            double nextX = finite(x, "WorldX");
            double nextY = finite(y, "WorldY");
            double nextZ = finite(z, "WorldZ");
            this.worldX = nextX;
            this.worldY = nextY;
            this.worldZ = nextZ;
        }
        public synchronized void setEndPosition(double x, double y) {
            double nextX = finite(x, "EndX");
            double nextY = finite(y, "EndY");
            this.endX = nextX;
            this.endY = nextY;
        }
        public synchronized void setEndWorldPosition(double x, double y, double z) {
            double nextX = finite(x, "EndWorldX");
            double nextY = finite(y, "EndWorldY");
            double nextZ = finite(z, "EndWorldZ");
            this.endWorldX = nextX;
            this.endWorldY = nextY;
            this.endWorldZ = nextZ;
        }
        public synchronized void setDepth(double depth) { this.depth = Math.max(0.0, finite(depth, "Depth")); }
        public synchronized void setThickness(double thickness) { this.thickness = Math.max(1.0, finite(thickness, "Thickness")); }
        public synchronized void setScaleWithDistance(boolean scaleWithDistance) { this.scaleWithDistance = scaleWithDistance; }
        public synchronized void setModelScale(float modelScale) {
            if (!Float.isFinite(modelScale)) throw new IllegalArgumentException("ModelScale must be finite");
            this.modelScale = Math.max(0.001F, modelScale);
        }
        public synchronized void setModelTransform(MLuaCFrame modelTransform) {
            if (type != RenderType.MESH && type != RenderType.BBMODEL) {
                throw new IllegalArgumentException("is available only on Mesh and BBModel objects");
            }
            if (modelTransform == null) throw new IllegalArgumentException("must be a CFrame");
            requireFiniteCFrame(modelTransform);
            this.modelTransform = modelTransform;
        }
        public synchronized void setWorldRenderDistance(double distance) {
            if (!Double.isFinite(distance) || distance < 1.0D || distance > MAX_WORLD_RENDER_DISTANCE) {
                throw new IllegalArgumentException("World render distance must be between 1 and "
                    + (int)MAX_WORLD_RENDER_DISTANCE + " blocks");
            }
            this.worldRenderDistance = distance;
        }
        public synchronized void setRenderStage(String renderStage) {
            String normalized;
            try {
                normalized = dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            } catch (IllegalArgumentException notAModelStage) {
                if (target() == RenderTarget.WORLD_MODEL) throw notAModelStage;
                normalized = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(renderStage);
            }
            this.renderStage = normalized;
        }
        public synchronized void setFollowEntityScope(MLuaEntityScope followEntityScope) {
            if (followEntityScope == null) {
                clearEntityAttachment();
                if (renderTarget == RenderTarget.ENTITY_MODEL) {
                    renderTarget = type == RenderType.MESH || type == RenderType.BBMODEL
                        ? RenderTarget.WORLD_MODEL : RenderTarget.WORLD_OVERLAY;
                    if (renderTarget == RenderTarget.WORLD_MODEL) {
                        normalizeWorldModelStageAfterDetach();
                    }
                }
                return;
            }
            if (this.followEntityScope != null && !this.followEntityScope.equals(followEntityScope)) {
                RENDER_TARGET_INDEX.unregisterEntityModel(this.followEntityScope, this.id);
            }
            this.followEntityScope = followEntityScope;
            renderTarget = type == RenderType.MESH || type == RenderType.BBMODEL
                ? RenderTarget.ENTITY_MODEL : RenderTarget.WORLD_OVERLAY;
            RENDER_TARGET_INDEX.registerEntityModel(followEntityScope, this.id);
        }

        private void normalizeWorldModelStageAfterDetach() {
            dev.mlua.loaderapi.ClientBridge bridge = dev.mlua.loaderapi.LoaderApi.client();
            try {
                renderStage = bridge.normalizeSupportedWorldModelStage(renderStage);
            } catch (IllegalArgumentException unsupportedStage) {
                renderStage = bridge.defaultWorldModelRenderStage();
            }
        }

        private void clearEntityAttachment() {
            if (followEntityScope != null) RENDER_TARGET_INDEX.unregisterEntityModel(followEntityScope, id);
            followEntityScope = null;
            followBoneName = null;
        }

        public synchronized void setFollowBoneName(String boneName) {
            if (type != RenderType.MESH && type != RenderType.BBMODEL) {
                throw new IllegalArgumentException("FollowBone requires a Mesh or BBModel object");
            }
            if (boneName == null || boneName.isBlank()) {
                this.followBoneName = null;
                return;
            }
            this.followBoneName = boneName.trim();
            this.renderTarget = RenderTarget.ENTITY_MODEL;
        }
        public synchronized void setFollowOffset(MLuaCFrame offset) {
            if (offset == null) throw new IllegalArgumentException("FollowOffset must be a CFrame");
            requireFiniteCFrame(offset);
            this.followOffset = offset;
            this.followOffsetBinding = FollowOffsetBinding.NONE;
        }
        public synchronized void setFollowOffsetCallback(LuaValue callback, MLuaContext context) {
            if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("FollowOffset callback must be a function");
            if (context == null) throw new IllegalArgumentException("FollowOffset callback needs its owning Lua context");
            this.followOffsetBinding = new FollowOffsetBinding(callback, context);
        }
        public LuaValue followOffsetValue() {
            FollowOffsetBinding binding = followOffsetBinding;
            return binding.callback().isfunction() ? binding.callback() : followOffset.toLua();
        }
        private void sampleFollowOffset() {
            FollowOffsetBinding binding = followOffsetBinding;
            LuaValue callback = binding.callback();
            MLuaContext context = binding.context();
            if (!callback.isfunction() || context == null) return;
            try {
                MLuaCFrame resolved = MLuaLuaBindings.withContext(context, () -> {
                    if (followOffsetBinding != binding) return null;
                    return MLuaCallbackBudget.run(context, () -> {
                        MLuaCFrame result = MLuaCFrame.fromLua(callback.invoke(LuaValue.NONE).arg1());
                        requireFiniteCFrame(result);
                        return result;
                    });
                });
                // A script may replace the callback while it is executing from another thread.
                // Do not let the older result overwrite the newer value.
                if (resolved != null && followOffsetBinding == binding) followOffset = resolved;
            } catch (VirtualMachineError | ThreadDeath fatal) {
                throw fatal;
            } catch (RuntimeException failure) {
                if (followOffsetBinding != binding) return;
                followOffsetBinding = FollowOffsetBinding.NONE;
                MLuaLogger.LOGGER.error("RenderObject FollowOffset callback failed; disabled it and kept the last valid CFrame", failure);
            }
        }

        private record FollowOffsetBinding(LuaValue callback, MLuaContext context) {
            private static final FollowOffsetBinding NONE = new FollowOffsetBinding(LuaValue.NIL, null);
        }

        private static void requireFiniteCFrame(MLuaCFrame value) {
            if (!Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z())
                    || !Double.isFinite(value.r00()) || !Double.isFinite(value.r01())
                    || !Double.isFinite(value.r02()) || !Double.isFinite(value.r10())
                    || !Double.isFinite(value.r11()) || !Double.isFinite(value.r12())
                    || !Double.isFinite(value.r20()) || !Double.isFinite(value.r21())
                    || !Double.isFinite(value.r22())) {
                throw new IllegalArgumentException("components must be finite");
            }
        }

        private static double finite(double value, String property) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException(property + " must be finite");
            return value;
        }

        private void applyFollowOffset(PoseStack pose) {
            pose.translate(followOffset.x(), followOffset.y(), followOffset.z());
            double[] angles = followOffset.toEulerAnglesXYZ();
            pose.mulPose(com.mojang.math.Axis.XP.rotation((float) angles[0]));
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) angles[1]));
            pose.mulPose(com.mojang.math.Axis.ZP.rotation((float) angles[2]));
        }

        RenderObject copy() {
            RenderObject copy = new RenderObject(id, type, resource);
            copy.luaClassName = luaClassName;
            copy.x = x;
            copy.y = y;
            copy.width = width;
            copy.height = height;
            copy.color = color;
            copy.transparency = transparency;
            copy.rotation = rotation;
            copy.visible = visible;
            copy.zIndex = zIndex;
            copy.renderTarget = renderTarget;
            copy.heldItemId = heldItemId;
            copy.followBoneName = followBoneName;
            copy.followOffset = followOffset;
            copy.worldX = worldX;
            copy.worldY = worldY;
            copy.worldZ = worldZ;
            copy.endX = endX;
            copy.endY = endY;
            copy.endWorldX = endWorldX;
            copy.endWorldY = endWorldY;
            copy.endWorldZ = endWorldZ;
            copy.depth = depth;
            copy.thickness = thickness;
            copy.scaleWithDistance = scaleWithDistance;
            copy.worldRenderDistance = worldRenderDistance;
            copy.followEntityScope = followEntityScope;
            copy.mesh = mesh;
            copy.bbmodel = bbmodel;
            copy.modelPartTransforms.putAll(modelPartTransforms);
            copy.modelPartVisibility.putAll(modelPartVisibility);
            copy.resolvedTextures = resolvedTextures;
            copy.modelTextures = modelTextures;
            copy.bbModelRenderPlan = bbModelRenderPlan;
            copy.modelScale = modelScale;
            copy.modelTransform = modelTransform;
            copy.renderStage = renderStage;
            copy.animationPlayer.copyStateFrom(animationPlayer);
            return copy;
        }
    }

}
