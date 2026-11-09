package dev.mlua.runtime.client;

import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.loaderapi.ClientWorldRenderContext;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import dev.mlua.loaderapi.LoaderApi;
import dev.mlua.loaderapi.WorldToScreenProjection;
import org.luaj.vm2.LuaValue;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

public final class MLuaClientRenderManager {
    public static final double DEFAULT_WORLD_RENDER_DISTANCE = 256.0D;
    public static final double MAX_WORLD_RENDER_DISTANCE = 4096.0D;
    private static final MLuaRenderObjectRegistry RENDER_OBJECTS = new MLuaRenderObjectRegistry();
    private static final MLuaRenderTickIndex<MLuaRenderObject> TICKING_OBJECTS = new MLuaRenderTickIndex<>();
    private static final MLuaRenderTargetIndex RENDER_TARGET_INDEX = new MLuaRenderTargetIndex();
    private static volatile RenderObject[] drawOrderSnapshot = new RenderObject[0];
    private static volatile boolean drawOrderDirty = true;
    private static final Identifier ATTACHMENT_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/stone.png");
    private static boolean animationClockRegistered;
    private static boolean worldModelSubmitRegistered;
    private static boolean worldRenderersRegistered;
    private static final AtomicBoolean WORLD_MODEL_STAGE_WARNING_REPORTED = new AtomicBoolean();

    private MLuaClientRenderManager() {
    }

    public static synchronized void registerOverlay() {
        if (!animationClockRegistered) {
            animationClockRegistered = true;
            LoaderApi.lifecycle().onClientTickEnd(MLuaClientRenderManager::tickAnimations);
        }
        LoaderApi.client().registerOverlay("mlua_render_service", graphics -> {
            if (graphics instanceof GuiGraphicsExtractor extractor) {
                MLuaRenderCallbacks.renderHud(extractor, extractor.guiWidth(), extractor.guiHeight());
                renderOverlayObjects(extractor);
                renderWorldOverlayObjects(extractor);
            }
        });
        if (!worldRenderersRegistered) {
            for (String stage : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
                LoaderApi.client().registerWorldRenderer(stage, context -> {
                    Object nativeContext = context.nativeContext();
                    MLuaRenderCallbacks.renderWorld(stage, nativeContext,
                        context.poseStack(), context.renderSource(), context.camera());
                });
            }
            for (String stage : LoaderApi.client().worldModelRenderStages()) {
                if (dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES.contains(stage)) continue;
                LoaderApi.client().registerWorldModelRenderer(stage, context -> {
                    Object nativeEvent = context.nativeContext();
                    if (!(nativeEvent instanceof SubmitCustomGeometryEvent event)) return;
                    var levelState = event.getLevelRenderState();
                    var cameraState = levelState.cameraRenderState;
                    Vec3 cameraPosition = cameraState == null || cameraState.pos == null
                        ? Minecraft.getInstance().gameRenderer.getMainCamera().position() : cameraState.pos;
                    Float partialTick = MLuaRenderCallbacks.readPartialTick(levelState);
                    if (partialTick == null) partialTick = MLuaRenderCallbacks.currentClientPartialTick();
                    renderWorldModelsSubmitted(stage, event.getPoseStack(), event.getSubmitNodeCollector(),
                        cameraPosition, partialTick == null ? 1.0F : partialTick);
                });
            }
            worldRenderersRegistered = true;
        }
        if (!worldModelSubmitRegistered) {
            worldModelSubmitRegistered = true;
            LoaderApi.client().registerWorldGeometryRenderer(context -> {
                Object nativeEvent = context.nativeContext();
                if (!(nativeEvent instanceof SubmitCustomGeometryEvent event)) return;
                MLuaRenderCallbacks.renderWorldGeometry(event, event.getPoseStack(),
                    event.getSubmitNodeCollector(), Minecraft.getInstance().gameRenderer.getMainCamera());
            });
        }
    }

    private static void tickAnimations() {
        for (MLuaRenderObject tickingObject : TICKING_OBJECTS.snapshot()) {
            if (!(tickingObject instanceof RenderObject object)) continue;
            synchronized (object.executionLock()) {
                if (object.destroyed) continue;
                if (object.bbmodel != null) object.animationPlayer.tick(1.0F / 20.0F, object.bbmodel);
            }
            // Follow-offset functions enter Lua and may destroy this object.
            // Run them outside the lifecycle lock, as on the shared targets.
            object.sampleFollowOffset();
            updateTickRegistration(object);
        }
    }

    static void updateTickRegistration(MLuaRenderObject object) {
        if (object == null) return;
        TICKING_OBJECTS.update(object.id, object, object.requiresClientTick());
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
            List<RenderObject> ordered = new ArrayList<>(RENDER_OBJECTS.values());
            ordered.sort(Comparator.comparingInt(RenderObject::zIndex).thenComparingInt(RenderObject::id));
            drawOrderSnapshot = ordered.toArray(RenderObject[]::new);
            drawOrderDirty = false;
            return drawOrderSnapshot;
        }
    }

    static void updateHeldItemModelIndex(MLuaRenderObject object, RenderTarget oldTarget, String oldItemId) {
        RENDER_TARGET_INDEX.updateHeldItem(object);
    }

    static void invalidateRenderOrder() {
        invalidateDrawOrder();
        RENDER_TARGET_INDEX.invalidateOrder();
    }

    private static List<RenderObject> orderedHeldItemModels(RenderTarget target, String itemId) {
        return RENDER_TARGET_INDEX.orderedHeldItemModels(target, itemId);
    }

    private static Object invokeOptional(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    private static void renderWorldModelsSubmitted(String stage, PoseStack pose, SubmitNodeCollector collector,
                                                   Vec3 cameraPosition, float partialTick) {
        if (pose == null || collector == null || cameraPosition == null) return;
        Vec3 look = cameraLook(Minecraft.getInstance().gameRenderer.getMainCamera());
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.WORLD_MODEL) continue;
                if (!object.renderStage.equals(stage)) {
                    if (WORLD_MODEL_STAGE_WARNING_REPORTED.compareAndSet(false, true)) {
                        MLuaLogger.LOGGER.warn("[MLua] NeoForge 26.1 submits WorldModel objects at its native custom-geometry point; only CUSTOM_GEOMETRY is currently supported.");
                    }
                    continue;
                }
                Entity followed = resolveFollowEntity(object.followEntityScope);
                if (object.followEntityScope != null && (followed == null || !followed.isAlive())) continue;
                double x = object.worldX + (followed == null ? 0.0 : followed.getX());
                double y = object.worldY + (followed == null ? 0.0 : followed.getY());
                double z = object.worldZ + (followed == null ? 0.0 : followed.getZ());
                int packedLight = MLuaWorldLight.packedAt(x, y, z, 15728880);
                Vec3 relative = new Vec3(x - cameraPosition.x, y - cameraPosition.y, z - cameraPosition.z);
                double distanceSquared = relative.lengthSqr();
                if (distanceSquared > object.worldRenderDistance * object.worldRenderDistance) continue;
                double radius = object.bbmodel != null ? 4.0 : 2.0;
                if (distanceSquared > 0.01 && relative.dot(look) < -radius) continue;
                pose.pushPose();
                try {
                    pose.translate(relative.x, relative.y, relative.z);
                    object.applyFollowOffset(pose, partialTick);
                    pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.bbmodel != null) submitBBModelAttachment(pose, collector, object, packedLight, partialTick);
                    else if (object.mesh != null) submitMeshAttachment(pose, collector, object, packedLight);
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to submit world render object {}", object.id, failure);
                    }
                } finally {
                    pose.popPose();
                }
            }
        }
    }

    private static Vec3 cameraLook(Object camera) {
        Object value = invokeOptional(camera, "getLookVector", "getLookDirection", "getForwardVector", "getLookAngle", "lookVector", "lookDirection");
        if (value == null) return new Vec3(0, 0, 1);
        Object x = invokeOptional(value, "x");
        Object y = invokeOptional(value, "y");
        Object z = invokeOptional(value, "z");
        return x instanceof Number && y instanceof Number && z instanceof Number
            ? new Vec3(((Number)x).doubleValue(), ((Number)y).doubleValue(), ((Number)z).doubleValue())
            : new Vec3(0, 0, 1);
    }

    private static Entity resolveFollowEntity(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope == null || scope.side() != dev.mlua.common.enums.Side.CLIENT || minecraft.level == null
                || !dev.mlua.minecraft.MLuaDimensionId.from(minecraft.level).equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    /** Submits 3D render objects after Minecraft has posed a living entity model. */
    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               SubmitNodeCollector collector, int light) {
        renderEntityAttachments(model, scope, pose, collector, light, 1.0F);
    }

    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               SubmitNodeCollector collector, int light, float partialTick) {
        if (scope == null || pose == null || collector == null) return;
        for (RenderObject object : RENDER_TARGET_INDEX.orderedEntityModels(scope)) {
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
                    object.applyFollowOffset(pose, partialTick);
                    pose.translate(object.worldX, object.worldY, object.worldZ);
                    if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.bbmodel != null) submitBBModelAttachment(pose, collector, object, light, partialTick);
                    else if (object.mesh != null) submitMeshAttachment(pose, collector, object, light);
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to submit entity attachment {}", renderId, failure);
                    }
                } finally {
                    pose.popPose();
                }
            }
        }
    }

    /** Submits addon model objects in the already transformed first- or third-person hand pose. */
    public static void renderHeldItemModels(RenderTarget target, String itemId, Object rawPose,
            Object renderSource, int packedLight, float partialTick) {
        if ((target != RenderTarget.FIRST_PERSON_ITEM && target != RenderTarget.THIRD_PERSON_ITEM)
                || itemId == null || !(rawPose instanceof PoseStack pose)
                || !(renderSource instanceof SubmitNodeCollector collector)) return;
        for (RenderObject object : orderedHeldItemModels(target, itemId)) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != target
                        || !itemId.equals(object.heldItemId)
                        || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) continue;
                pose.pushPose();
                try {
                    object.applyFollowOffset(pose, partialTick);
                    pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.bbmodel != null) submitBBModelAttachment(pose, collector, object, packedLight, partialTick);
                    else if (object.mesh != null) submitMeshAttachment(pose, collector, object, packedLight);
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to submit held-item RenderObject {}", object.id, failure);
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
        for (RenderObject object : orderedHeldItemModels(target, itemId)) {
            if (!object.destroyed() && object.visible() && object.target() == target
                    && itemId.equals(object.heldItemId())
                    && (object.type() == RenderType.MESH || object.type() == RenderType.BBMODEL)) return true;
        }
        return false;
    }

    /** Draws living-model attachments while the renderer's animated model parts are live. */
    public static void renderEntityAttachmentsAtModelDraw(Object model, MLuaEntityScope scope, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, float partialTick) {
        if (scope == null || pose == null || buffers == null
                || !MLuaClientRenderThread.require("entity model attachments")) return;
        for (RenderObject object : RENDER_TARGET_INDEX.orderedEntityModels(scope)) {
            int renderId = object.id;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                        || !scope.equals(object.followEntityScope)) continue;
                pose.pushPose();
                try {
                    if (object.followBoneName != null && (model == null
                            || !MLuaModelAdapterRegistry.applyCurrentPartTransform(
                                model, object.followBoneName, pose, scope))) continue;
                    object.applyFollowOffset(pose, partialTick);
                    pose.translate(object.worldX, object.worldY, object.worldZ);
                    if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.mesh != null) renderModelMesh(pose, buffers, object, light, overlay);
                    else if (object.bbmodel != null) renderModelBBModel(pose, buffers, object, light, overlay, partialTick);
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

    /** Whether this loader can submit a managed model through the supplied render source. */
    public static boolean supportsDrawObjectSource(Object source) {
        return source instanceof SubmitNodeCollector;
    }

    /** Submits a Lua-owned model through the active frame's render collector. */
    public static boolean drawObjectInRenderContext(int renderId, MLuaRenderOwnershipRegistry.Owner owner,
            Object poseValue, Object collectorValue, MLuaCFrame transform, int light, int overlay, float partialTick) {
        RenderObject object = get(renderId, owner);
        if (object == null) throw new IllegalArgumentException("RenderObject is destroyed or belongs to another addon runtime");
        if (object.mesh == null && object.bbmodel == null) {
            throw new IllegalArgumentException("RenderContext:DrawObject requires a Mesh or BBModel RenderObject");
        }
        if (!(poseValue instanceof PoseStack pose) || !(collectorValue instanceof SubmitNodeCollector collector)) {
            throw new IllegalStateException("This render callback does not expose a PoseStack and SubmitNodeCollector");
        }
        synchronized (object.executionLock()) {
            if (object.destroyed) throw new IllegalStateException("RenderObject was destroyed before it could be drawn");
            if (!object.visible) return false;
            pose.pushPose();
            try {
                applyRenderTransform(pose, transform == null ? MLuaCFrame.IDENTITY : transform);
                if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                if (object.bbmodel != null) submitBBModelAttachment(pose, collector, object, light, partialTick);
                else submitMeshAttachment(pose, collector, object, light);
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

    private static void applyRenderTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        transform.toEulerAnglesXYZ(scratch.eulerAngles);
        double[] angles = scratch.eulerAngles;
        pose.mulPose(Axis.XP.rotation((float)angles[0]));
        pose.mulPose(Axis.YP.rotation((float)angles[1]));
        pose.mulPose(Axis.ZP.rotation((float)angles[2]));
    }

    private static void submitMeshAttachment(PoseStack pose, SubmitNodeCollector collector,
                                             RenderObject object, int light) {
        var mesh = object.mesh;
        Identifier texture = resolveWorldTexture(mesh.texture(), object.id);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(texture), (matrix, vertices) -> {
            for (int i = 0; i + 2 < mesh.indices().size(); i += 3) {
                var a = mesh.vertices().get(mesh.indices().get(i));
                var b = mesh.vertices().get(mesh.indices().get(i + 1));
                var c = mesh.vertices().get(mesh.indices().get(i + 2));
                MLuaRenderVertexEmitter.submitVertex(vertices, matrix, a.x(), a.y(), a.z(), a.u(), a.v(), a.color(), light);
                MLuaRenderVertexEmitter.submitVertex(vertices, matrix, b.x(), b.y(), b.z(), b.u(), b.v(), b.color(), light);
                MLuaRenderVertexEmitter.submitVertex(vertices, matrix, c.x(), c.y(), c.z(), c.u(), c.v(), c.color(), light);
            }
        });
    }

    private static Identifier resolveWorldTexture(String path, int renderId) {
        if (path == null || path.isBlank()) return ATTACHMENT_TEXTURE;
        var owner = RENDER_OBJECTS.ownerOf(renderId);
        String candidate = path.indexOf(':') >= 0 || owner == null || owner.ownerModId().isBlank()
            ? path : owner.ownerModId() + ":" + path;
        Identifier location = MLuaResourceLocations.tryParseOrNull(candidate);
        return location != null && Minecraft.getInstance().getResourceManager().getResource(location).isPresent()
            ? location : ATTACHMENT_TEXTURE;
    }

    private static void submitBBModelAttachment(PoseStack pose, SubmitNodeCollector collector,
                                                 RenderObject object, int light, float partialTick) {
        var asset = object.bbmodel;
        var animationName = object.animationPlayer.active();
        var animation = animationName == null ? null : asset.animations().get(animationName);
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        for (var cube : asset.cubes()) {
            pose.pushPose();
            try {
                for (var bone : asset.boneChainForCube(cube.uuid())) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        scratch.position, scratch.rotation, scratch.scale);
                    MLuaRenderVertexEmitter.applyTransform(pose, object.modelPartTransform(bone.uuid()));
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    scratch.position, scratch.rotation, scratch.scale);
                for (var entry : cube.faces().entrySet()) {
                    var face = entry.getValue();
                    collector.submitCustomGeometry(pose, RenderTypes.entityCutout(attachmentTexture(object, face)),
                        (matrix, vertices) -> MLuaRenderVertexEmitter.submitBBFace(vertices, matrix, entry.getKey(), cube,
                            face, asset.textureWidth(), asset.textureHeight(), light));
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static void renderModelMesh(PoseStack pose, MultiBufferSource buffers,
                                       RenderObject object, int light, int overlay) {
        var mesh = object.mesh;
        VertexConsumer vertices = buffers.getBuffer(RenderTypes.entityCutout(resolveWorldTexture(mesh.texture(), object.id)));
        PoseStack.Pose matrix = pose.last();
        for (int i = 0; i + 2 < mesh.indices().size(); i += 3) {
            var a = mesh.vertices().get(mesh.indices().get(i));
            var b = mesh.vertices().get(mesh.indices().get(i + 1));
            var c = mesh.vertices().get(mesh.indices().get(i + 2));
            MLuaRenderVertexEmitter.submitVertex(vertices, matrix, a.x(), a.y(), a.z(), a.u(), a.v(), a.color(), light, overlay, a.nx(), a.ny(), a.nz());
            MLuaRenderVertexEmitter.submitVertex(vertices, matrix, b.x(), b.y(), b.z(), b.u(), b.v(), b.color(), light, overlay, b.nx(), b.ny(), b.nz());
            MLuaRenderVertexEmitter.submitVertex(vertices, matrix, c.x(), c.y(), c.z(), c.u(), c.v(), c.color(), light, overlay, c.nx(), c.ny(), c.nz());
        }
    }

    private static void renderModelBBModel(PoseStack pose, MultiBufferSource buffers,
            RenderObject object, int light, int overlay, float partialTick) {
        var asset = object.bbmodel;
        var animationName = object.animationPlayer.active();
        var animation = animationName == null ? null : asset.animations().get(animationName);
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        for (var cube : asset.cubes()) {
            pose.pushPose();
            try {
                for (var bone : asset.boneChainForCube(cube.uuid())) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        scratch.position, scratch.rotation, scratch.scale);
                    MLuaRenderVertexEmitter.applyTransform(pose, object.modelPartTransform(bone.uuid()));
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    scratch.position, scratch.rotation, scratch.scale);
                for (var entry : cube.faces().entrySet()) {
                    var face = entry.getValue();
                    VertexConsumer vertices = buffers.getBuffer(RenderTypes.entityCutout(attachmentTexture(object, face)));
                    MLuaRenderVertexEmitter.submitBBFace(vertices, pose.last(), entry.getKey(), cube, face,
                        asset.textureWidth(), asset.textureHeight(), light, overlay);
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static Identifier attachmentTexture(RenderObject object, MLuaBBModelAsset.Face face) {
        if (object.bbmodel == null) return ATTACHMENT_TEXTURE;
        return face.texture() >= 0 && face.texture() < object.resolvedTextures.size()
            ? object.resolvedTextures.get(face.texture()) : ATTACHMENT_TEXTURE;
    }

    private static void renderOverlayObjects(GuiGraphicsExtractor graphics) {
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible() || object.target() == RenderTarget.MANUAL
                        || object.target() == RenderTarget.FIRST_PERSON_ITEM
                        || object.target() == RenderTarget.THIRD_PERSON_ITEM || object.worldSpace()) continue;
                int alpha = (int) ((1.0 - object.transparency()) * 255.0);
                int color = ((alpha & 0xff) << 24) | (object.color() & 0x00ffffff);
                int x1 = (int) Math.round(object.x());
                int y1 = (int) Math.round(object.y());
                int x2 = (int) Math.round(object.x() + object.width());
                int y2 = (int) Math.round(object.y() + object.height());
                switch (object.type()) {
                    case RECT, BOX -> graphics.fill(x1, y1, x2, y2, color);
                    case LINE, BEAM -> {
                        int endX = (int) Math.round(object.endX());
                        int endY = (int) Math.round(object.endY());
                        int steps = Math.max(Math.abs(endX - x1), Math.abs(endY - y1));
                        int radius = Math.max(0, (int) Math.round(object.thickness() / 2.0));
                        for (int step = 0; step <= steps; step++) {
                            double t = steps == 0 ? 0.0 : (double) step / steps;
                            int x = (int) Math.round(x1 + (endX - x1) * t);
                            int y = (int) Math.round(y1 + (endY - y1) * t);
                            graphics.fill(x - radius, y - radius, x + radius + 1, y + radius + 1, color);
                        }
                    }
                    case TEXT -> graphics.text(Minecraft.getInstance().font,
                        object.resource() == null ? "" : object.resource(), x1, y1, color);
                    case IMAGE, ITEM, BLOCK -> drawProjectedObject(graphics, object, x1, y1,
                        Math.max(1, x2 - x1), Math.max(1, y2 - y1), color);
                    default -> { }
                }
            }
        }
    }

    /** Draws world-space 2D annotations in the GUI extraction pass, projecting them with the active camera. */
    private static void renderWorldOverlayObjects(GuiGraphicsExtractor graphics) {
        WorldToScreenProjection.Projector projector = WorldToScreenProjection.create(
            invokeOptional(Minecraft.getInstance().gameRenderer, "getMainCamera"), graphics.guiWidth(), graphics.guiHeight());
        if (projector == null) return;
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible() || object.target() != RenderTarget.WORLD_OVERLAY) continue;
                try {
                    int alpha = (int) Math.round((1.0 - object.transparency()) * 255.0);
                    int color = ((alpha & 0xff) << 24) | (object.color() & 0x00ffffff);
                    if (object.type() == RenderType.LINE || object.type() == RenderType.BEAM) {
                        Entity followed = object.followEntityScope() == null ? null : resolveFollowEntity(object.followEntityScope());
                        if (object.followEntityScope() != null && (followed == null || !followed.isAlive())) continue;
                        double sx = object.worldX() + (followed == null ? 0.0 : followed.getX());
                        double sy = object.worldY() + (followed == null ? 0.0 : followed.getY() + followed.getBbHeight() * 0.5);
                        double sz = object.worldZ() + (followed == null ? 0.0 : followed.getZ());
                        WorldToScreenProjection.Point start = projector.project(sx, sy, sz);
                        WorldToScreenProjection.Point end = projector.project(object.endWorldX(), object.endWorldY(), object.endWorldZ());
                        if (start != null && end != null) drawProjectedLine(graphics, start, end, color, object.thickness());
                        continue;
                    }
                    if (object.type() == RenderType.BOX) {
                        renderProjectedBox(graphics, object, color, projector);
                        continue;
                    }
                    Entity followed = object.followEntityScope() == null ? null : resolveFollowEntity(object.followEntityScope());
                    if (object.followEntityScope() != null && (followed == null || !followed.isAlive())) continue;
                    double wx = object.worldX() + (followed == null ? 0.0 : followed.getX());
                    double wy = object.worldY() + (followed == null ? 0.0 : followed.getY() + followed.getBbHeight() * 0.5);
                    double wz = object.worldZ() + (followed == null ? 0.0 : followed.getZ());
                    WorldToScreenProjection.Point point = projector.project(wx, wy, wz);
                    if (point == null) continue;
                    double scale = object.scaleWithDistance() ? point.scale() : 1.0;
                    int width = Math.max(1, (int) Math.round(object.width() * scale));
                    int height = Math.max(1, (int) Math.round(object.height() * scale));
                    int x = (int) Math.round(point.x() - width / 2.0);
                    int y = (int) Math.round(point.y() - height / 2.0);
                    drawProjectedObject(graphics, object, x, y, width, height, color);
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to render world overlay object {}", object.id(), failure);
                    }
                }
            }
        }
    }

    private static void renderProjectedBox(GuiGraphicsExtractor graphics, RenderObject object, int color,
                                           WorldToScreenProjection.Projector projector) {
        Entity followed = object.followEntityScope() == null ? null : resolveFollowEntity(object.followEntityScope());
        if (object.followEntityScope() != null && (followed == null || !followed.isAlive())) return;
        double cx = object.worldX() + (followed == null ? 0.0 : followed.getX());
        double cy = object.worldY() + (followed == null ? 0.0 : followed.getY() + followed.getBbHeight() * 0.5);
        double cz = object.worldZ() + (followed == null ? 0.0 : followed.getZ());
        double hx = object.width() / 2.0, hy = object.height() / 2.0, hz = object.depth() / 2.0;
        WorldToScreenProjection.Point[] corners = new WorldToScreenProjection.Point[] {
            projector.project(cx-hx, cy-hy, cz-hz),
            projector.project(cx+hx, cy-hy, cz-hz),
            projector.project(cx+hx, cy+hy, cz-hz),
            projector.project(cx-hx, cy+hy, cz-hz),
            projector.project(cx-hx, cy-hy, cz+hz),
            projector.project(cx+hx, cy-hy, cz+hz),
            projector.project(cx+hx, cy+hy, cz+hz),
            projector.project(cx-hx, cy+hy, cz+hz)
        };
        int[][] edges = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        for (int[] edge : edges) {
            WorldToScreenProjection.Point a = corners[edge[0]], b = corners[edge[1]];
            if (a != null && b != null) drawProjectedLine(graphics, a, b, color, object.thickness());
        }
    }

    private static void drawProjectedLine(GuiGraphicsExtractor graphics, WorldToScreenProjection.Point a,
                                          WorldToScreenProjection.Point b, int color, double thickness) {
        int x1 = (int) Math.round(a.x()), y1 = (int) Math.round(a.y());
        int x2 = (int) Math.round(b.x()), y2 = (int) Math.round(b.y());
        int steps = Math.min(2048, Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)));
        int radius = Math.max(0, Math.min(32, (int) Math.round(thickness / 2.0)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0.0 : (double) i / steps;
            int x = (int) Math.round(x1 + (x2 - x1) * t), y = (int) Math.round(y1 + (y2 - y1) * t);
            graphics.fill(x-radius, y-radius, x+radius+1, y+radius+1, color);
        }
    }

    private static void drawProjectedObject(GuiGraphicsExtractor graphics, RenderObject object,
                                            int x, int y, int width, int height, int color) {
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate((float) x, (float) y);
            pose.rotate((float) Math.toRadians(object.rotation()));
            double baseWidth = Math.max(1.0, object.width()), baseHeight = Math.max(1.0, object.height());
            pose.scale((float) (width / baseWidth), (float) (height / baseHeight));
            switch (object.type()) {
                case RECT -> graphics.fill(0, 0, (int) Math.round(baseWidth), (int) Math.round(baseHeight), color);
                case TEXT -> graphics.text(Minecraft.getInstance().font,
                    object.resource() == null ? "" : object.resource(), 0, 0, color);
                case IMAGE -> {
                    Identifier texture = resolveWorldTexture(object.resource(), object.id());
                    graphics.blit(texture, 0, 0, (int) Math.round(baseWidth), (int) Math.round(baseHeight), 0.0F, 0.0F, 1.0F, 1.0F);
                }
                case ITEM, BLOCK -> {
                    Identifier id = MLuaResourceLocations.tryParseOrNull(object.resource());
                    if (id == null && object.resource() != null && !object.resource().contains(":")) {
                        id = MLuaResourceLocations.tryParseOrNull("minecraft:" + object.resource());
                    }
                    if (id != null) {
                        var item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                        if (item != null) {
                            pose.scale((float) (baseWidth / 16.0), (float) (baseHeight / 16.0));
                            graphics.item(new ItemStack(item), 0, 0);
                        }
                    }
                }
                default -> { }
            }
        } finally {
            pose.popMatrix();
        }
    }


    public static int create(RenderType type, String resource) {
        return create(type, resource, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static synchronized int create(RenderType type, String resource, MLuaRenderOwnershipRegistry.Owner owner) {
        java.util.Objects.requireNonNull(type, "Render type cannot be null");
        int id = RENDER_OBJECTS.publish(new RenderObject(RENDER_OBJECTS.nextId(), type, resource), owner);
        invalidateDrawOrder();
        return id;
    }

    public static int createMesh(MLuaMeshDefinition mesh) {
        return createMesh(mesh, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int createMesh(MLuaMeshDefinition mesh, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = create(RenderType.MESH, mesh.texture(), owner);
        RENDER_OBJECTS.get(id).mesh = mesh;
        return id;
    }

    public static int createBBModel(MLuaBBModelAsset asset) {
        return createBBModel(asset, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int createBBModel(MLuaBBModelAsset asset, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = create(RenderType.BBMODEL, null, owner);
        try {
            RenderObject object = RENDER_OBJECTS.get(id);
            object.bbmodel = asset;
            String namespace = owner == null ? "" : owner.ownerModId();
            List<Identifier> textures = new ArrayList<>();
            for (String path : asset.textures()) {
                try { textures.add(MLuaAssetResolver.resolveTextureFile(namespace, path, "models")); }
                catch (RuntimeException ignored) { textures.add(ATTACHMENT_TEXTURE); }
            }
            object.resolvedTextures = List.copyOf(textures);
            return id;
        } catch (RuntimeException failure) {
            remove(id, owner);
            throw failure;
        }
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
        invalidateDrawOrder();
        RENDER_TARGET_INDEX.clear();
        RENDER_OBJECTS.clearOwners();
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
        attachToEntity(renderId, MLuaEntityScope.of(entity));
        return true;
    }

    public static boolean attachToEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (!RENDER_OBJECTS.owns(renderId, owner)) return false;
        attachToEntity(renderId, MLuaEntityScope.of(entity));
        return true;
    }

    public static boolean detachFromEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (entity == null || !RENDER_OBJECTS.owns(renderId, owner)) return false;
        RenderObject object = RENDER_OBJECTS.get(renderId);
        if (object == null || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) return false;
        if (!MLuaEntityScope.of(entity).equals(object.followEntityScope)) return false;
        object.setTarget(RenderTarget.MANUAL);
        return true;
    }

    private static void attachToEntity(int renderId, MLuaEntityScope scope) {
        RenderObject object = RENDER_OBJECTS.get(renderId);
        if (object == null) return;
        object.setFollowEntityScope(scope);
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

    static void registerEntityModel(MLuaEntityScope scope, int id) {
        RENDER_TARGET_INDEX.registerEntityModel(scope, id);
    }

    static void unregisterEntityModel(MLuaEntityScope scope, int id) {
        RENDER_TARGET_INDEX.unregisterEntityModel(scope, id);
    }

    public static void playEntityAnimation(int entityId, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.legacy(entityId), name, loop, speed);
    }

    public static void playEntityAnimation(Entity entity, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.of(entity), name, loop, speed);
    }

    public static void playEntityAnimation(Entity entity, String name, Boolean loopOverride,
                                           float speed, float fadeDuration) {
        playEntityAnimation(MLuaEntityScope.of(entity), name, loopOverride, speed, fadeDuration);
    }

    private static void playEntityAnimation(MLuaEntityScope scope, String name, Boolean loopOverride,
                                            float speed, float fadeDuration) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = RENDER_OBJECTS.get(renderId);
            if (object != null && object.bbmodel != null) {
                object.animationPlayer.play(name, object.bbmodel, loopOverride, speed, fadeDuration);
            }
        }
    }

    private static void playEntityAnimation(MLuaEntityScope scope, String name, boolean loop, float speed) {
        java.util.Set<Integer> renderIds = RENDER_TARGET_INDEX.entityModelIds(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = RENDER_OBJECTS.get(renderId);
            if (object != null && object.bbmodel != null) object.animationPlayer.play(name, object.bbmodel, loop, speed);
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
            if (object != null && object.bbmodel != null) object.animationPlayer.stop();
        }
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

    /** Shared render-object state and lifecycle are implemented by the common component. */
    public static final class RenderObject extends MLuaRenderObject {
        RenderObject(int id, RenderType type, String resource) {
            super(id, type, resource);
        }

        RenderObject(int id, RenderType type, String resource, boolean trackAnimationActivity) {
            super(id, type, resource, trackAnimationActivity);
        }
    }

}
