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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
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

    static void invalidateDrawOrder() {
        synchronized (MLuaClientRenderManager.class) {
            drawOrderSnapshot = new RenderObject[0];
            drawOrderDirty = true;
        }
    }

    static void invalidateRenderOrder() {
        invalidateDrawOrder();
        RENDER_TARGET_INDEX.invalidateOrder();
    }

    static void updateRenderTargetIndex(MLuaRenderObject object) {
        RENDER_TARGET_INDEX.updateHeldItem(object);
    }

    static void updateHeldItemModelIndex(MLuaRenderObject object, RenderTarget previousTarget, String previousItemId) {
        RENDER_TARGET_INDEX.updateHeldItem(object);
    }

    static void registerEntityModel(MLuaEntityScope scope, int id) {
        RENDER_TARGET_INDEX.registerEntityModel(scope, id);
    }

    static void unregisterEntityModel(MLuaEntityScope scope, int id) {
        RENDER_TARGET_INDEX.unregisterEntityModel(scope, id);
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
            Runnable releaseLease = embeddedLease ? EMBEDDED_TEXTURES.acquireLease(texture) : () -> { };
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
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        transform.toEulerAnglesXYZ(scratch.eulerAngles);
        double[] angles = scratch.eulerAngles;
        pose.mulPose(Axis.XP.rotation((float)angles[0]));
        pose.mulPose(Axis.YP.rotation((float)angles[1]));
        pose.mulPose(Axis.ZP.rotation((float)angles[2]));
    }

    private static void applyTransform(Matrix4f matrix, MLuaCFrame transform) {
        matrix.translate((float)transform.x(), (float)transform.y(), (float)transform.z());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        transform.toEulerAnglesXYZ(scratch.eulerAngles);
        double[] angles = scratch.eulerAngles;
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
                    object.resolvedTextures = EMBEDDED_TEXTURES.registerModelTextures(asset);
                    object.modelTextures = EMBEDDED_TEXTURES.resolveModelTextures(asset, object.resolvedTextures);
                    object.bbModelRenderPlan = MLuaBBModelRenderPlan.prepare(asset, object.modelTextures);
                    object.initialized = true;
                } catch (RuntimeException | Error failure) {
                    EMBEDDED_TEXTURES.releaseModelTextures(object.resolvedTextures);
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

    static ResourceLocation embeddedTextureLocation(byte[] imageBytes, int index) {
        return MLuaEmbeddedTextureRegistry.embeddedTextureLocation(imageBytes, index);
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
                EMBEDDED_TEXTURES.releaseModelTextures(removed.resolvedTextures);
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
        EMBEDDED_TEXTURES.releaseAll();
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

    /** Compatibility type retained while the render-object state lives in its own component. */
    public static final class RenderObject extends MLuaRenderObject {
        RenderObject(int id, RenderType type, String resource) {
            super(id, type, resource);
        }
    }

}
