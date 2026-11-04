package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.core.MLuaLogger;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
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
import org.joml.Matrix4f;

public final class MLuaClientRenderManager {
    public static final double DEFAULT_WORLD_RENDER_DISTANCE = 256.0D;
    public static final double MAX_WORLD_RENDER_DISTANCE = 4096.0D;
    private static final MLuaRenderObjectRegistry RENDER_OBJECTS = new MLuaRenderObjectRegistry();
    private static final MLuaRenderTickIndex<MLuaRenderObject> TICKING_OBJECTS = new MLuaRenderTickIndex<>();
    private static final MLuaRenderTargetIndex RENDER_TARGET_INDEX = new MLuaRenderTargetIndex();
    private static final MLuaEmbeddedTextureRegistry EMBEDDED_TEXTURES = new MLuaEmbeddedTextureRegistry();
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
                Object buffers = MLuaRenderContextAccess.optional(context, "renderSource", "consumers", "getRenderTypeBuffer",
                    "bufferSource", "getSubmitNodeCollector", "submitNodeCollector", "getRenderCollector");
                Object camera = MLuaRenderContextAccess.optional(context, "camera", "getCamera");
                MLuaRenderCallbacks.renderWorldGeometry(context, pose, buffers, camera);
            });
            worldRenderersRegistered = true;
        }
    }

    private static void tickAnimations() {
        releaseDeferredRenderLeases();
        for (MLuaRenderObject tickingObject : TICKING_OBJECTS.snapshot()) {
            if (!(tickingObject instanceof RenderObject object)) continue;
            synchronized (object.executionLock()) {
                if (object.destroyed) continue;
                if (object.bbmodel != null) object.animationPlayer.tick(1.0F / 20.0F, object.bbmodel);
            }
            // A follow-offset function enters Lua and can itself destroy this object.
            // Never call into Lua while holding the render object's lifecycle lock.
            object.sampleFollowOffset();
            updateTickRegistration(object);
        }
    }

    static void updateTickRegistration(MLuaRenderObject object) {
        if (object == null) return;
        TICKING_OBJECTS.update(object.id, object, object.requiresClientTick());
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

    static List<RenderObject> orderedHeldItemModels(RenderTarget target, String itemId) {
        return RENDER_TARGET_INDEX.orderedHeldItemModels(target, itemId);
    }

    static List<RenderObject> orderedEntityModels(MLuaEntityScope scope) {
        return RENDER_TARGET_INDEX.orderedEntityModels(scope);
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

    static RenderObject[] orderedRenderObjects() {
        if (!drawOrderDirty) return drawOrderSnapshot;
        synchronized (MLuaClientRenderManager.class) {
            if (!drawOrderDirty) return drawOrderSnapshot;
            List<RenderObject> ordered = new ArrayList<>(RENDER_OBJECTS.values());
            ordered.sort(Comparator.comparingInt(RenderObject::zIndex).thenComparingInt(RenderObject::id));
            drawOrderSnapshot = ordered.toArray(RenderObject[]::new);
            drawOrderDirty = false;
            return drawOrderSnapshot;
        }
    }

    private static void renderWorldContext(String stage, Object context) {
        MLuaWorldRenderPipeline.renderWorldContext(stage, context);
    }

    static Vec3 cameraLook(Object camera) {
        return MLuaRenderContextAccess.cameraLook(camera);
    }

    static Entity resolveFollowEntity(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope.side() != dev.mlua.common.enums.Side.CLIENT || minecraft.level == null
            || !dev.mlua.minecraft.MLuaDimensionId.from(minecraft.level).equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static void renderBBModelWorld(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                          float partialTick) {
        renderBBModelWorld(pose, buffers, object, 15728880, 0, partialTick);
    }

    static void renderBBModelWorld(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                   int light, int overlay, float partialTick) {
        MLuaBBModelWorldRenderer.render(pose, buffers, object, light, overlay, partialTick);
    }

    /** Draws Lua-owned 3D objects after Minecraft has posed an entity model. */
    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay) {
        renderEntityAttachments(model, scope, pose, buffers, light, overlay, 1.0F);
    }

    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               MultiBufferSource buffers, int light, int overlay, float partialTick) {
        MLuaRenderTargetDispatcher.renderEntityAttachments(model, scope, pose, buffers, light, overlay, partialTick);
    }

    /** Draws addon-owned model objects in the already transformed first- or third-person hand pose. */
    public static void renderHeldItemModels(RenderTarget target, String itemId, Object rawPose,
            Object renderSource, int packedLight, float partialTick) {
        MLuaRenderTargetDispatcher.renderHeldItemModels(target, itemId, rawPose, renderSource, packedLight, partialTick);
    }

    public static boolean hasHeldItemModels(RenderTarget target, String itemId) {
        return MLuaRenderTargetDispatcher.hasHeldItemModels(target, itemId);
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
                    submitted = MLuaWorldRenderPipeline.submitMeshGeometry(bufferValue, pose, mesh, texture, light, overlay, object, matrix -> {
                        applyTransform(matrix, offset);
                        applyTransform(matrix, modelTransform);
                        matrix.scale(scale, scale, scale);
                    });
                } else {
                    submitted = MLuaWorldRenderPipeline.submitBBModelGeometry(bufferValue, pose, object, light, overlay, partialTick, matrix -> {
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

    static void applyTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        transform.toEulerAnglesXYZ(scratch.eulerAngles);
        double[] angles = scratch.eulerAngles;
        pose.mulPose(Axis.XP.rotation((float)angles[0]));
        pose.mulPose(Axis.YP.rotation((float)angles[1]));
        pose.mulPose(Axis.ZP.rotation((float)angles[2]));
    }

    static void applyTransform(Matrix4f matrix, MLuaCFrame transform) {
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
                if (object.destroyed || !RENDER_OBJECTS.contains(id, object)) return;
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
        return RENDER_OBJECTS.nextId();
    }

    /** Publishes a fully initialized object, rolling back its ownership reservation on failure. */
    private static int publish(RenderObject object, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = RENDER_OBJECTS.publish(object, owner);
        invalidateDrawOrder();
        return id;
    }

    static void deferRenderCleanup(Runnable cleanup) {
        DEFERRED_RENDER_CLEANUPS.add(cleanup);
    }

    static Runnable acquireEmbeddedTextureLease(ResourceLocation texture) {
        return EMBEDDED_TEXTURES.acquireLease(texture);
    }

    static ResourceLocation embeddedTextureLocation(byte[] imageBytes, int index) {
        return MLuaEmbeddedTextureRegistry.embeddedTextureLocation(imageBytes, index);
    }

    public static RenderObject get(int id) {
        return RENDER_OBJECTS.get(id);
    }

    public static RenderObject get(int id, MLuaRenderOwnershipRegistry.Owner owner) {
        return RENDER_OBJECTS.owns(id, owner) ? RENDER_OBJECTS.get(id) : null;
    }

    public static List<Integer> ids() {
        return RENDER_OBJECTS.ids();
    }

    public static List<Integer> ids(MLuaRenderOwnershipRegistry.Owner owner) {
        return RENDER_OBJECTS.ids(owner);
    }

    public static void remove(int id) {
        RenderObject removed = RENDER_OBJECTS.remove(id);
        TICKING_OBJECTS.remove(id);
        if (removed != null) {
            synchronized (removed.executionLock()) {
                removed.markDestroyed();
                EMBEDDED_TEXTURES.releaseModelTextures(removed.resolvedTextures);
            }
            invalidateDrawOrder();
        }
        RENDER_TARGET_INDEX.removeObject(id);
    }

    public static void remove(int id, MLuaRenderOwnershipRegistry.Owner owner) {
        if (RENDER_OBJECTS.owns(id, owner)) remove(id);
    }

    public static void clearOwned(MLuaRenderOwnershipRegistry.Owner owner) {
        RENDER_OBJECTS.clearOwned(owner).forEach(MLuaClientRenderManager::remove);
    }

    public static void clearRuntime(Object runtimeOwner) {
        RENDER_OBJECTS.clearRuntime(runtimeOwner).forEach(MLuaClientRenderManager::remove);
    }

    /** Removes render objects tied to the current world while preserving HUD and manual objects. */
    public static void clearWorldState() {
        List<Integer> ids = RENDER_OBJECTS.values().stream()
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
        List<Integer> ids = RENDER_OBJECTS.ids();
        ids.forEach(MLuaClientRenderManager::remove);
        // Clear pending per-submission leases before draining the registry. Otherwise
        // an old queued release could decrement a texture registered after this reset.
        releaseDeferredRenderLeases();
        invalidateDrawOrder();
        RENDER_TARGET_INDEX.clear();
        RENDER_OBJECTS.clearOwners();
        EMBEDDED_TEXTURES.releaseAll();
    }

    public static void attachToEntity(int renderId, int entityId) {
        attachToEntity(renderId, MLuaEntityScope.legacy(entityId));
    }

    public static void attachToEntity(int renderId, Entity entity) {
        attachToEntity(renderId, MLuaEntityScope.of(entity));
    }

    public static boolean attachToEntity(int renderId, Entity entity, Object runtimeOwner) {
        MLuaRenderOwnershipRegistry.Owner owner = RENDER_OBJECTS.ownerOf(renderId);
        if (owner == null || owner.runtimeOwner() != runtimeOwner) return false;
        return attachToEntity(renderId, entity, owner);
    }

    public static boolean attachToEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (entity == null || !RENDER_OBJECTS.owns(renderId, owner)) return false;
        RenderObject object = RENDER_OBJECTS.get(renderId);
        if (object == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return false;
        synchronized (object.executionLock()) {
            if (object.destroyed) return false;
            object.setFollowEntityScope(MLuaEntityScope.of(entity));
            return true;
        }
    }

    /** Detaches an addon-owned model from exactly this entity and leaves it manually drawable. */
    public static boolean detachFromEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (entity == null || !RENDER_OBJECTS.owns(renderId, owner)) return false;
        RenderObject object = RENDER_OBJECTS.get(renderId);
        if (object == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return false;
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        synchronized (object.executionLock()) {
            if (object.destroyed || !scope.equals(object.followEntityScope)) return false;
            object.setTarget(RenderTarget.MANUAL);
            return true;
        }
    }

    private static void attachToEntity(int renderId, MLuaEntityScope scope) {
        RenderObject object = RENDER_OBJECTS.get(renderId);
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
            if (!RENDER_OBJECTS.owns(renderId, owner)) continue;
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
            RenderObject object = RENDER_OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.visible && object.followBoneName != null) return true;
            }
        }
        return false;
    }

    private static boolean hasEntityModel(MLuaEntityScope scope) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        return renderIds != null && renderIds.stream().map(RENDER_OBJECTS::get)
                .anyMatch(object -> object != null && !object.destroyed && object.visible());
    }

    public static void playEntityAnimation(int entityId, String name, Boolean loopOverride, float speed) {
        playEntityAnimation(entityId, name, loopOverride, speed, 0.0F);
    }

    public static void playEntityAnimation(int entityId, String name, Boolean loopOverride,
                                           float speed, float fadeDuration) {
        playEntityAnimation(MLuaEntityScope.legacy(entityId), name, loopOverride, speed, fadeDuration);
    }

    public static void playEntityAnimation(int entityId, String name, boolean loop, float speed) {
        playEntityAnimation(entityId, name, Boolean.valueOf(loop), speed);
    }

    public static void playEntityAnimation(Entity entity, String name, Boolean loopOverride, float speed) {
        playEntityAnimation(entity, name, loopOverride, speed, 0.0F);
    }

    public static void playEntityAnimation(Entity entity, String name, Boolean loopOverride,
                                           float speed, float fadeDuration) {
        playEntityAnimation(MLuaEntityScope.of(entity), name, loopOverride, speed, fadeDuration);
    }

    public static void playEntityAnimation(Entity entity, String name, boolean loop, float speed) {
        playEntityAnimation(entity, name, Boolean.valueOf(loop), speed);
    }

    private static void playEntityAnimation(MLuaEntityScope scope, String name, Boolean loopOverride,
                                            float speed, float fadeDuration) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = RENDER_OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.bbmodel != null) {
                    object.animationPlayer.play(name, object.bbmodel, loopOverride, speed, fadeDuration);
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
            RenderObject object = RENDER_OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (!object.destroyed && object.bbmodel != null) object.animationPlayer.stop();
            }
        }
    }

    public static void renderAll(GuiGraphics graphics) {
        MLuaRenderCallbacks.renderHud(graphics, graphics.guiWidth(), graphics.guiHeight());
        for (RenderObject object : orderedRenderObjects()) {
            // Skip objects owned by other render passes before taking the per-object lock.
            if (object.destroyed || !object.visible || !object.initialized
                    || !MLuaHudRenderPass.isTarget(object.target())) continue;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || !object.initialized
                        || !MLuaHudRenderPass.isTarget(object.target())) continue;
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
        return resolveTexture(path, "mlua");
    }

    static ResourceLocation resolveTexture(String path, String ownerModId) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            String namespace = ownerModId == null || ownerModId.isBlank() ? "mlua" : ownerModId;
            ResourceLocation location = dev.mlua.runtime.registry.MLuaAssetResolver.isTextureUri(path)
                ? dev.mlua.runtime.registry.MLuaAssetResolver.resolveTextureFile(namespace, path, "gui")
                : path.contains(":") ? MLuaResourceLocations.parse(path)
                    : MLuaResourceLocations.build(namespace, path);
            return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : null;
        } catch (RuntimeException invalidTexture) {
            return null;
        }
    }

    /** Resolves a mesh's unqualified texture in the addon that owns the render object. */
    static ResourceLocation resolveMeshTexture(RenderObject object) {
        if (object == null || object.mesh == null) return MLuaRenderHelper.fallbackTexture();
        String path = object.mesh.texture();
        if (path == null || path.isBlank()) return MLuaRenderHelper.fallbackTexture();
        MLuaRenderOwnershipRegistry.Owner owner = RENDER_OBJECTS.ownerOf(object.id);
        String namespace = owner == null || owner.ownerModId().isBlank() ? "mlua" : owner.ownerModId();
        ResourceLocation location;
        try {
            location = dev.mlua.runtime.registry.MLuaAssetResolver.isTextureUri(path)
                ? dev.mlua.runtime.registry.MLuaAssetResolver.resolveTextureFile(namespace, path, "models")
                : path.contains(":") ? MLuaResourceLocations.parse(path)
                    : MLuaResourceLocations.build(namespace, path);
        } catch (RuntimeException invalidTexture) {
            return MLuaRenderHelper.fallbackTexture();
        }
        return location != null && Minecraft.getInstance().getResourceManager().getResource(location).isPresent()
            ? location : MLuaRenderHelper.fallbackTexture();
    }

    static String ownerModId(int renderId) {
        MLuaRenderOwnershipRegistry.Owner owner = RENDER_OBJECTS.ownerOf(renderId);
        return owner == null || owner.ownerModId().isBlank() ? "mlua" : owner.ownerModId();
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

        RenderObject(int id, RenderType type, String resource, boolean trackAnimationActivity) {
            super(id, type, resource, trackAnimationActivity);
        }

        RenderObject(int id, RenderType type, String resource, boolean trackAnimationActivity,
                     String defaultRenderStage) {
            super(id, type, resource, trackAnimationActivity, defaultRenderStage);
        }
    }

}
