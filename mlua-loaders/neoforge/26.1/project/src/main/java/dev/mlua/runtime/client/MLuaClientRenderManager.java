package dev.mlua.runtime.client;

import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import dev.mlua.loaderapi.LoaderApi;
import org.luaj.vm2.LuaValue;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class MLuaClientRenderManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, RenderObject> OBJECTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MLuaEntityScope, java.util.Set<Integer>> ENTITY_MODELS = new ConcurrentHashMap<>();
    private static final MLuaRenderOwnershipRegistry OWNERS = new MLuaRenderOwnershipRegistry();
    private static volatile RenderObject[] drawOrderSnapshot = new RenderObject[0];
    private static volatile boolean drawOrderDirty = true;
    private static final Identifier ATTACHMENT_TEXTURE = Identifier.fromNamespaceAndPath(
        "minecraft", "textures/block/stone.png");
    private static boolean animationClockRegistered;
    private static boolean worldModelSubmitRegistered;
    private static final AtomicBoolean WORLD_MODEL_STAGE_WARNING_REPORTED = new AtomicBoolean();

    private MLuaClientRenderManager() {
    }

    public static void registerOverlay() {
        if (!animationClockRegistered) {
            animationClockRegistered = true;
            LoaderApi.lifecycle().onClientTickEnd(MLuaClientRenderManager::tickAnimations);
        }
        if (!worldModelSubmitRegistered) {
            worldModelSubmitRegistered = true;
            NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent event) -> {
                var levelState = event.getLevelRenderState();
                var state = levelState.cameraRenderState;
                Vec3 cameraPosition = state == null || state.pos == null
                    ? Minecraft.getInstance().gameRenderer.getMainCamera().position() : state.pos;
                Float partialTick = MLuaRenderCallbacks.readPartialTick(levelState);
                if (partialTick == null) partialTick = MLuaRenderCallbacks.currentClientPartialTick();
                renderWorldModelsSubmitted("AFTER_ENTITIES", event.getPoseStack(),
                    event.getSubmitNodeCollector(), cameraPosition, partialTick == null ? 1.0F : partialTick);
            });
        }
        LoaderApi.client().registerOverlay("mlua_render_service", graphics -> {
            if (graphics instanceof GuiGraphicsExtractor extractor) {
                MLuaRenderCallbacks.renderHud(extractor, extractor.guiWidth(), extractor.guiHeight());
                renderOverlayObjects(extractor);
            }
        });
        for (String stage : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
            LoaderApi.client().registerWorldRenderer(stage, context -> MLuaRenderCallbacks.renderWorld(stage,
                context, invokeOptional(context, "getPoseStack", "poseStack", "matrixStack"),
                invokeOptional(context, "consumers", "getConsumers", "getBufferSource", "bufferSource"),
                invokeOptional(context, "getCamera", "camera")));
        }
    }

    private static void tickAnimations() {
        for (RenderObject object : OBJECTS.values()) {
            synchronized (object.executionLock()) {
                if (object.destroyed) continue;
                if (object.bbmodel != null) object.animationPlayer.tick(1.0F / 20.0F, object.bbmodel);
                object.sampleFollowOffset();
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
                        MLuaLogger.LOGGER.warn("[MLua] NeoForge 26.1 submits WorldModel objects at its native custom-geometry point; only AFTER_ENTITIES is currently supported.");
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
                if (distanceSquared > 256.0 * 256.0) continue;
                double radius = object.bbmodel != null ? 4.0 : 2.0;
                if (distanceSquared > 0.01 && relative.dot(look) < -radius) continue;
                pose.pushPose();
                try {
                    pose.translate(relative.x, relative.y, relative.z);
                    object.applyFollowOffset(pose);
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
                || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
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
        java.util.Set<Integer> attachedRenderIds = ENTITY_MODELS.get(scope);
        if (attachedRenderIds == null || attachedRenderIds.isEmpty()) return;
        for (Integer renderId : attachedRenderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                        || !scope.equals(object.followEntityScope)) continue;
                pose.pushPose();
                try {
                    // A missing bone means the entity model's root. This keeps root-level
                    // attachments usable with living models that have no registered adapter.
                    if (object.followBoneName != null && (model == null
                            || !MLuaModelAdapterRegistry.applyPartTransform(model, object.followBoneName, pose))) continue;
                    object.applyFollowOffset(pose);
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
            } finally {
                pose.popPose();
            }
        }
    }

    private static void applyRenderTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        double[] angles = transform.toEulerAnglesXYZ();
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
                submitVertex(vertices, matrix, a.x(), a.y(), a.z(), a.u(), a.v(), a.color(), light);
                submitVertex(vertices, matrix, b.x(), b.y(), b.z(), b.u(), b.v(), b.color(), light);
                submitVertex(vertices, matrix, c.x(), c.y(), c.z(), c.u(), c.v(), c.color(), light);
            }
        });
    }

    private static Identifier resolveWorldTexture(String path, int renderId) {
        if (path == null || path.isBlank()) return ATTACHMENT_TEXTURE;
        var owner = OWNERS.ownerOf(renderId);
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
        float[] animatedPosition = new float[3];
        float[] animatedRotation = new float[3];
        float[] animatedScale = new float[3];
        for (var cube : asset.cubes()) {
            pose.pushPose();
            try {
                for (var bone : asset.boneChainForCube(cube.uuid())) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        animatedPosition, animatedRotation, animatedScale);
                    applyTransform(pose, object.modelPartTransform(bone.uuid()));
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    animatedPosition, animatedRotation, animatedScale);
                for (var entry : cube.faces().entrySet()) {
                    var face = entry.getValue();
                    collector.submitCustomGeometry(pose, RenderTypes.entityCutout(attachmentTexture(object, face)),
                        (matrix, vertices) -> submitBBFace(vertices, matrix, entry.getKey(), cube,
                            face, asset.textureWidth(), asset.textureHeight(), light));
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static void applyTransform(PoseStack pose, MLuaCFrame transform) {
        pose.translate(transform.x(), transform.y(), transform.z());
        double[] angles = transform.toEulerAnglesXYZ();
        pose.mulPose(Axis.XP.rotation((float) angles[0]));
        pose.mulPose(Axis.YP.rotation((float) angles[1]));
        pose.mulPose(Axis.ZP.rotation((float) angles[2]));
    }

    private static Identifier attachmentTexture(RenderObject object, MLuaBBModelAsset.Face face) {
        if (object.bbmodel == null) return ATTACHMENT_TEXTURE;
        return face.texture() >= 0 && face.texture() < object.resolvedTextures.size()
            ? object.resolvedTextures.get(face.texture()) : ATTACHMENT_TEXTURE;
    }

    private static void submitBBFace(VertexConsumer out, PoseStack.Pose pose, String direction,
                                     MLuaBBModelAsset.Cube cube, MLuaBBModelAsset.Face face,
                                     int textureWidth, int textureHeight, int light) {
        float x1 = cube.from(0) / 16.0F, y1 = cube.from(1) / 16.0F, z1 = cube.from(2) / 16.0F;
        float x2 = cube.to(0) / 16.0F, y2 = cube.to(1) / 16.0F, z2 = cube.to(2) / 16.0F;
        float nx = 0, ny = 0, nz = 0;
        float[][] points = switch (direction) {
            case "north" -> { nz = -1; yield new float[][]{{x1,y1,z1},{x2,y1,z1},{x2,y2,z1},{x1,y2,z1}}; }
            case "south" -> { nz = 1; yield new float[][]{{x2,y1,z2},{x1,y1,z2},{x1,y2,z2},{x2,y2,z2}}; }
            case "west" -> { nx = -1; yield new float[][]{{x1,y1,z2},{x1,y1,z1},{x1,y2,z1},{x1,y2,z2}}; }
            case "east" -> { nx = 1; yield new float[][]{{x2,y1,z1},{x2,y1,z2},{x2,y2,z2},{x2,y2,z1}}; }
            case "up" -> { ny = 1; yield new float[][]{{x1,y2,z1},{x2,y2,z1},{x2,y2,z2},{x1,y2,z2}}; }
            case "down" -> { ny = -1; yield new float[][]{{x1,y1,z2},{x2,y1,z2},{x2,y1,z1},{x1,y1,z1}}; }
            default -> null;
        };
        if (points == null) return;
        float u1 = (face.hasUv() ? face.uv(0) : 0) / Math.max(1, textureWidth);
        float v1 = (face.hasUv() ? face.uv(1) : 0) / Math.max(1, textureHeight);
        float u2 = (face.hasUv() ? face.uv(2) : textureWidth) / Math.max(1, textureWidth);
        float v2 = (face.hasUv() ? face.uv(3) : textureHeight) / Math.max(1, textureHeight);
        float[][] corners = {{u1,v2},{u2,v2},{u2,v1},{u1,v1}};
        int turns = Math.floorMod(face.rotation(), 360) / 90;
        for (int i = 0; i < 4; i++) {
            float[] point = points[i];
            float[] tex = corners[Math.floorMod(i - turns, 4)];
            submitVertex(out, pose, point[0],point[1],point[2], tex[0],tex[1], 0xFFFFFFFF, light, nx,ny,nz);
        }
    }

    private static void submitVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                                     float u, float v, int color, int light) {
        submitVertex(out, pose, x,y,z,u,v,color,light,0,1,0);
    }

    private static void submitVertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z,
                                     float u, float v, int color, int light, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z)
            .setColor((color >>> 16) & 255, (color >>> 8) & 255, color & 255, (color >>> 24) & 255)
            .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    private static void renderOverlayObjects(GuiGraphicsExtractor graphics) {
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible() || object.target() == RenderTarget.MANUAL
                        || object.worldSpace()) continue;
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
                    default -> { }
                }
            }
        }
    }

    public static int create(RenderType type, String resource) {
        return create(type, resource, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int create(RenderType type, String resource, MLuaRenderOwnershipRegistry.Owner owner) {
        java.util.Objects.requireNonNull(type, "Render type cannot be null");
        int id = NEXT_ID.getAndIncrement();
        if (!OWNERS.tryRegister(id, owner, MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS,
                MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER)) {
            throw new IllegalStateException("[MLua] RenderService limit reached ("
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS_PER_OWNER + " live objects per addon runtime, "
                + MLuaRenderOwnershipRegistry.MAX_RENDER_OBJECTS + " total). Destroy unused render objects before creating more.");
        }
        OBJECTS.put(id, new RenderObject(id, type, resource));
        invalidateDrawOrder();
        return id;
    }

    public static int createMesh(MLuaMeshDefinition mesh) {
        return createMesh(mesh, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int createMesh(MLuaMeshDefinition mesh, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = create(RenderType.MESH, mesh.texture(), owner);
        OBJECTS.get(id).mesh = mesh;
        return id;
    }

    public static int createBBModel(MLuaBBModelAsset asset) {
        return createBBModel(asset, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int createBBModel(MLuaBBModelAsset asset, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = create(RenderType.BBMODEL, null, owner);
        try {
            RenderObject object = OBJECTS.get(id);
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
            }
            invalidateDrawOrder();
        }
        OWNERS.remove(id);
        ENTITY_MODELS.forEach((scope, ids) -> {
            ids.remove(id);
            if (ids.isEmpty()) ENTITY_MODELS.remove(scope, ids);
        });
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

    public static void clearAll() {
        List<Integer> ids = List.copyOf(OBJECTS.keySet());
        ids.forEach(MLuaClientRenderManager::remove);
        invalidateDrawOrder();
        ENTITY_MODELS.clear();
        OWNERS.clear();
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
        attachToEntity(renderId, MLuaEntityScope.of(entity));
        return true;
    }

    public static boolean attachToEntity(int renderId, Entity entity, MLuaRenderOwnershipRegistry.Owner owner) {
        if (!OWNERS.owns(renderId, owner)) return false;
        attachToEntity(renderId, MLuaEntityScope.of(entity));
        return true;
    }

    private static void attachToEntity(int renderId, MLuaEntityScope scope) {
        RenderObject object = OBJECTS.get(renderId);
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
        java.util.Set<Integer> renderIds = ENTITY_MODELS.remove(scope);
        if (renderIds != null) renderIds.forEach(MLuaClientRenderManager::remove);
    }

    public static boolean hasEntityModel(int entityId) {
        return hasEntityModel(MLuaEntityScope.legacy(entityId));
    }

    public static boolean hasEntityModel(Entity entity) {
        return hasEntityModel(MLuaEntityScope.of(entity));
    }

    private static boolean hasEntityModel(MLuaEntityScope scope) {
        java.util.Set<Integer> renderIds = ENTITY_MODELS.get(scope);
        return renderIds != null && renderIds.stream().map(OBJECTS::get)
                .anyMatch(object -> object != null && !object.destroyed && object.visible());
    }

    private static void registerEntityModel(MLuaEntityScope scope, int id) {
        if (scope == null) return;
        ENTITY_MODELS.computeIfAbsent(scope, ignored -> ConcurrentHashMap.newKeySet()).add(id);
    }

    private static void unregisterEntityModel(MLuaEntityScope scope, int id) {
        if (scope == null) return;
        java.util.Set<Integer> ids = ENTITY_MODELS.get(scope);
        if (ids == null) return;
        ids.remove(id);
        if (ids.isEmpty()) ENTITY_MODELS.remove(scope, ids);
    }

    public static void playEntityAnimation(int entityId, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.legacy(entityId), name, loop, speed);
    }

    public static void playEntityAnimation(Entity entity, String name, boolean loop, float speed) {
        playEntityAnimation(MLuaEntityScope.of(entity), name, loop, speed);
    }

    private static void playEntityAnimation(MLuaEntityScope scope, String name, boolean loop, float speed) {
        java.util.Set<Integer> renderIds = ENTITY_MODELS.get(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = OBJECTS.get(renderId);
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
        java.util.Set<Integer> renderIds = ENTITY_MODELS.get(scope);
        if (renderIds == null) return;
        for (Integer renderId : renderIds) {
            RenderObject object = OBJECTS.get(renderId);
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
        ENTITY_MODEL
    }

    public static final class RenderObject {
        private final int id;
        private final RenderType type;
        private String luaClassName = "RenderObject";
        private String resource;
        private double x = 0.0;
        private double y = 0.0;
        private double width = 16.0;
        private double height = 16.0;
        private int color = 0xFFFFFF;
        private double transparency = 0.0;
        private double rotation = 0.0;
        private boolean visible = true;
        private volatile boolean destroyed;
        private final AtomicBoolean renderFailureReported = new AtomicBoolean();
        private int zIndex = 0;
        private boolean worldSpace = false;
        private boolean manualTarget;
        private boolean entityModelTarget;
        private String followBoneName;
        private MLuaCFrame followOffset = MLuaCFrame.IDENTITY;
        private LuaValue followOffsetCallback = LuaValue.NIL;
        private MLuaContext followOffsetContext;
        private double worldX = 0.0;
        private double worldY = 0.0;
        private double worldZ = 0.0;
        private double endX = 0.0;
        private double endY = 0.0;
        private double endWorldX = 0.0;
        private double endWorldY = 0.0;
        private double endWorldZ = 0.0;
        private double depth = 16.0;
        private double thickness = 1.0;
        private boolean scaleWithDistance = true;
        private MLuaEntityScope followEntityScope;
        private MLuaMeshDefinition mesh;
        private MLuaBBModelAsset bbmodel;
        private List<Identifier> resolvedTextures = List.of();
        private float modelScale = 1.0F;
        private String renderStage = "AFTER_ENTITIES";
        private final MLuaAnimationPlayer animationPlayer = new MLuaAnimationPlayer();
        private final ConcurrentHashMap<String, MLuaCFrame> modelPartTransforms = new ConcurrentHashMap<>();
        private volatile Object executionLock = this;

        private RenderObject(int id, RenderType type, String resource) {
            this.id = id;
            this.type = type;
            this.resource = resource;
        }

        public int id() { return id; }
        public String luaClassName() { return luaClassName; }
        public void setLuaClassName(String luaClassName) {
            if (luaClassName == null || luaClassName.isBlank()) {
                throw new IllegalArgumentException("Render object Lua class name cannot be empty");
            }
            this.luaClassName = luaClassName;
        }
        public Object executionLock() { return executionLock; }
        public void setExecutionLock(Object executionLock) {
            this.executionLock = executionLock == null ? this : executionLock;
        }
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
        public boolean destroyed() { return destroyed; }
        public boolean markRenderFailureReported() { return renderFailureReported.compareAndSet(false, true); }
        public int zIndex() { return zIndex; }
        public boolean worldSpace() { return worldSpace; }
        public RenderTarget target() {
            if (manualTarget) return RenderTarget.MANUAL;
            if (entityModelTarget || followBoneName != null) return RenderTarget.ENTITY_MODEL;
            if (!worldSpace) return RenderTarget.HUD;
            return type == RenderType.MESH || type == RenderType.BBMODEL
                ? RenderTarget.WORLD_MODEL : RenderTarget.WORLD_OVERLAY;
        }
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
        public LuaValue followOffsetValue() { return followOffsetCallback.isfunction() ? followOffsetCallback : followOffset.toLua(); }
        public MLuaMeshDefinition mesh() { return mesh; }
        public MLuaBBModelAsset bbmodel() { return bbmodel; }
        public MLuaCFrame modelPartTransform(String boneId) {
            return modelPartTransforms.getOrDefault(boneId, MLuaCFrame.IDENTITY);
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
            if (boneId == null || asset.boneById(boneId) == null) {
                throw new IllegalArgumentException("Unknown Blockbench model part id: " + boneId);
            }
        }
        public MLuaAnimationPlayer animationPlayer() { return animationPlayer; }
        public float modelScale() { return modelScale; }
        public String renderStage() { return renderStage; }

        public void setResource(String resource) { this.resource = resource; }
        public void setPosition(double x, double y) { this.x = x; this.y = y; }
        public void setSize(double width, double height) { this.width = width; this.height = height; }
        public void setColor(int color) { this.color = color; }
        public void setTransparency(double transparency) { this.transparency = Math.max(0.0, Math.min(1.0, transparency)); }
        public void setRotation(double rotation) { this.rotation = rotation; }
        public void setVisible(boolean visible) { this.visible = visible; }
        private void markDestroyed() {
            if (destroyed) return;
            destroyed = true;
            visible = false;
            setFollowEntityScope(null);
            followOffsetCallback = LuaValue.NIL;
            followOffsetContext = null;
            animationPlayer.stop();
            modelPartTransforms.clear();
        }
        public void setZIndex(int zIndex) {
            if (this.zIndex == zIndex) return;
            this.zIndex = zIndex;
            invalidateDrawOrder();
        }
        public void setWorldSpace(boolean worldSpace) {
            manualTarget = false;
            if (worldSpace && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            }
            if (!worldSpace) {
                setFollowEntityScope(null);
                followBoneName = null;
                entityModelTarget = false;
            }
            this.worldSpace = worldSpace;
        }
        public void setTarget(RenderTarget target) {
            if (target == null) throw new IllegalArgumentException("Render target must be provided");
            boolean model = type == RenderType.MESH || type == RenderType.BBMODEL;
            if (target == RenderTarget.MANUAL) {
                if (!model) throw new IllegalArgumentException("Manual target requires a Mesh or BBModel object");
                manualTarget = true;
                setFollowEntityScope(null);
                followBoneName = null;
                entityModelTarget = false;
                worldSpace = false;
                return;
            }
            manualTarget = false;
            if ((target == RenderTarget.WORLD_MODEL || target == RenderTarget.ENTITY_MODEL) && !model) {
                throw new IllegalArgumentException("WorldModel and EntityModel targets require a Mesh or BBModel object");
            }
            if (target == RenderTarget.WORLD_MODEL) LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            if (target == RenderTarget.WORLD_OVERLAY && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                throw new IllegalArgumentException("Mesh and BBModel objects use the WorldModel target");
            }
            if (target == RenderTarget.HUD) setFollowEntityScope(null);
            if (target != RenderTarget.ENTITY_MODEL) followBoneName = null;
            entityModelTarget = target == RenderTarget.ENTITY_MODEL;
            this.worldSpace = target != RenderTarget.HUD;
        }
        public void setWorldPosition(double x, double y, double z) { this.worldX = x; this.worldY = y; this.worldZ = z; }
        public void setEndPosition(double x, double y) { this.endX = x; this.endY = y; }
        public void setEndWorldPosition(double x, double y, double z) { this.endWorldX = x; this.endWorldY = y; this.endWorldZ = z; }
        public void setDepth(double depth) { this.depth = Math.max(0.0, depth); }
        public void setThickness(double thickness) { this.thickness = Math.max(1.0, thickness); }
        public void setScaleWithDistance(boolean scaleWithDistance) { this.scaleWithDistance = scaleWithDistance; }
        public void setModelScale(float modelScale) { this.modelScale = Math.max(0.001F, modelScale); }
        public void setRenderStage(String renderStage) {
            String normalized = target() == RenderTarget.WORLD_MODEL
                ? LoaderApi.client().normalizeSupportedWorldModelStage(renderStage)
                : dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(renderStage);
            this.renderStage = normalized;
        }
        public void setFollowEntityScope(MLuaEntityScope followEntityScope) {
            if (followEntityScope != null) manualTarget = false;
            if (this.followEntityScope != null && !this.followEntityScope.equals(followEntityScope)) {
                unregisterEntityModel(this.followEntityScope, this.id);
            }
            this.followEntityScope = followEntityScope;
            if (followEntityScope == null) {
                followBoneName = null;
                entityModelTarget = false;
            }
            if (followEntityScope != null) {
                registerEntityModel(followEntityScope, this.id);
                this.worldSpace = true;
            }
        }
        public void setFollowBoneName(String boneName) {
            if (type != RenderType.MESH && type != RenderType.BBMODEL) {
                throw new IllegalArgumentException("FollowBone requires a Mesh or BBModel object");
            }
            if (boneName == null || boneName.isBlank()) {
                followBoneName = null;
                entityModelTarget = followEntityScope != null;
                return;
            }
            manualTarget = false;
            followBoneName = boneName.trim();
            entityModelTarget = true;
            worldSpace = true;
        }
        public void setFollowOffset(MLuaCFrame offset) {
            if (offset == null) throw new IllegalArgumentException("FollowOffset must be a CFrame");
            followOffset = offset;
            followOffsetCallback = LuaValue.NIL;
            followOffsetContext = null;
        }
        public void setFollowOffsetCallback(LuaValue callback, MLuaContext context) {
            if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("FollowOffset callback must be a function");
            if (context == null) throw new IllegalArgumentException("FollowOffset callback needs its owning Lua context");
            followOffsetCallback = callback;
            followOffsetContext = context;
        }
        private void sampleFollowOffset() {
            if (!followOffsetCallback.isfunction()) return;
            try {
                if (followOffsetContext != null) {
                    MLuaLuaBindings.bindContext(followOffsetContext.globals(), followOffsetContext);
                }
                followOffset = MLuaCFrame.fromLua(followOffsetCallback.invoke(LuaValue.NONE).arg1());
            } catch (RuntimeException failure) {
                followOffsetCallback = LuaValue.NIL;
                followOffsetContext = null;
                MLuaLogger.LOGGER.error("RenderObject FollowOffset callback failed; disabled it and kept the last valid CFrame", failure);
            }
        }
        private void applyFollowOffset(PoseStack pose) {
            pose.translate(followOffset.x(), followOffset.y(), followOffset.z());
            double[] angles = followOffset.toEulerAnglesXYZ();
            pose.mulPose(Axis.XP.rotation((float) angles[0]));
            pose.mulPose(Axis.YP.rotation((float) angles[1]));
            pose.mulPose(Axis.ZP.rotation((float) angles[2]));
        }

        private RenderObject copy() {
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
            copy.worldSpace = worldSpace;
            copy.manualTarget = manualTarget;
            copy.entityModelTarget = entityModelTarget;
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
            copy.followEntityScope = followEntityScope;
            copy.mesh = mesh;
            copy.bbmodel = bbmodel;
            copy.modelPartTransforms.putAll(modelPartTransforms);
            copy.resolvedTextures = resolvedTextures;
            copy.modelScale = modelScale;
            copy.renderStage = renderStage;
            copy.animationPlayer.copyStateFrom(animationPlayer);
            return copy;
        }
    }

}
