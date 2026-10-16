package dev.mlua.runtime.client;

import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.core.MLuaLogger;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.registry.MLuaAssetResolver;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import dev.mlua.loaderapi.LoaderApi;
import org.luaj.vm2.LuaValue;

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

    private MLuaClientRenderManager() {
    }

    public static void registerOverlay() {
        if (!animationClockRegistered) {
            animationClockRegistered = true;
            LoaderApi.lifecycle().onClientTickEnd(MLuaClientRenderManager::tickAnimations);
        }
        LoaderApi.client().registerOverlay("mlua_render_service", graphics -> {
            if (graphics instanceof GuiGraphicsExtractor extractor) {
                MLuaRenderCallbacks.renderHud(extractor, extractor.guiWidth(), extractor.guiHeight());
                renderOverlayObjects(extractor);
            }
        });
        for (String stage : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
            LoaderApi.client().registerWorldRenderer(stage, context -> MLuaRenderCallbacks.renderWorld(stage,
                context, invokeOptional(context, "getPoseStack", "poseStack", "matrixStack"), null,
                invokeOptional(context, "getCamera", "camera")));
        }
    }

    private static void tickAnimations() {
        for (RenderObject object : OBJECTS.values()) {
            if (object.bbmodel != null) object.animationPlayer.tick(1.0F / 20.0F, object.bbmodel);
            object.sampleFollowOffset();
        }
    }

    private static void invalidateDrawOrder() {
        synchronized (MLuaClientRenderManager.class) {
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

    /** Submits 3D render objects after Minecraft has posed a living entity model. */
    public static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
                                               SubmitNodeCollector collector, int light) {
        if (model == null || scope == null || pose == null || collector == null) return;
        java.util.Set<Integer> attachedRenderIds = ENTITY_MODELS.get(scope);
        if (attachedRenderIds == null || attachedRenderIds.isEmpty()) return;
        for (Integer renderId : attachedRenderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                    || object.followBoneName == null) continue;
            pose.pushPose();
            if (!MLuaModelAdapterRegistry.applyPartTransform(model, object.followBoneName, pose)) {
                pose.popPose();
                continue;
            }
            object.applyFollowOffset(pose);
            pose.translate(object.worldX, object.worldY, object.worldZ);
            if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
            if (object.bbmodel != null) submitBBModelAttachment(pose, collector, object, light);
            else if (object.mesh != null) submitMeshAttachment(pose, collector, object, light);
            pose.popPose();
        }
    }

    private static void submitMeshAttachment(PoseStack pose, SubmitNodeCollector collector,
                                             RenderObject object, int light) {
        var mesh = object.mesh;
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(ATTACHMENT_TEXTURE), (matrix, vertices) -> {
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

    private static void submitBBModelAttachment(PoseStack pose, SubmitNodeCollector collector,
                                                 RenderObject object, int light) {
        var asset = object.bbmodel;
        var animationName = object.animationPlayer.active();
        var animation = animationName == null ? null : asset.animations().get(animationName);
        for (var cube : asset.cubes()) {
            pose.pushPose();
            for (var bone : asset.boneChainForCube(cube.uuid())) {
                float[] pivot = bone.pivot();
                float[] rotation = bone.rotation();
                if (animation != null) {
                    float[] animatedPosition = object.animationPlayer.sample(animation, bone.uuid(), "position");
                    float[] animatedRotation = object.animationPlayer.sample(animation, bone.uuid(), "rotation");
                    pose.translate(animatedPosition[0] / 16.0F, animatedPosition[1] / 16.0F, animatedPosition[2] / 16.0F);
                    rotation = new float[]{rotation[0] + animatedRotation[0], rotation[1] + animatedRotation[1], rotation[2] + animatedRotation[2]};
                }
                pose.translate(pivot[0] / 16.0F, pivot[1] / 16.0F, pivot[2] / 16.0F);
                pose.mulPose(Axis.XP.rotationDegrees(rotation[0]));
                pose.mulPose(Axis.YP.rotationDegrees(rotation[1]));
                pose.mulPose(Axis.ZP.rotationDegrees(rotation[2]));
                pose.translate(-pivot[0] / 16.0F, -pivot[1] / 16.0F, -pivot[2] / 16.0F);
            }
            for (var entry : cube.faces().entrySet()) {
                var face = entry.getValue();
                collector.submitCustomGeometry(pose, RenderTypes.entityCutout(attachmentTexture(object, face)),
                    (matrix, vertices) -> submitBBFace(vertices, matrix, entry.getKey(), cube.from(), cube.to(),
                        face, asset.textureWidth(), asset.textureHeight(), light));
            }
            pose.popPose();
        }
    }

    private static Identifier attachmentTexture(RenderObject object, MLuaBBModelAsset.Face face) {
        if (object.bbmodel == null) return ATTACHMENT_TEXTURE;
        return face.texture() >= 0 && face.texture() < object.resolvedTextures.size()
            ? object.resolvedTextures.get(face.texture()) : ATTACHMENT_TEXTURE;
    }

    private static void submitBBFace(VertexConsumer out, PoseStack.Pose pose, String direction,
                                     float[] from, float[] to, MLuaBBModelAsset.Face face,
                                     int textureWidth, int textureHeight, int light) {
        float x1 = from[0] / 16.0F, y1 = from[1] / 16.0F, z1 = from[2] / 16.0F;
        float x2 = to[0] / 16.0F, y2 = to[1] / 16.0F, z2 = to[2] / 16.0F;
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
        float[] uv = face.uv();
        if (uv == null || uv.length < 4) uv = new float[]{0, 0, textureWidth, textureHeight};
        float u1 = uv[0] / Math.max(1, textureWidth), v1 = uv[1] / Math.max(1, textureHeight);
        float u2 = uv[2] / Math.max(1, textureWidth), v2 = uv[3] / Math.max(1, textureHeight);
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
            if (!object.visible() || object.worldSpace()) continue;
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

    public static int create(RenderType type, String resource) {
        return create(type, resource, new MLuaRenderOwnershipRegistry.Owner(null, ""));
    }

    public static int create(RenderType type, String resource, MLuaRenderOwnershipRegistry.Owner owner) {
        int id = NEXT_ID.getAndIncrement();
        OBJECTS.put(id, new RenderObject(id, type, resource));
        invalidateDrawOrder();
        OWNERS.register(id, owner);
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
        if (removed != null) invalidateDrawOrder();
        if (removed != null)
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
        OBJECTS.clear();
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

    private static void resetEntityModel(MLuaEntityScope scope) {
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
                .anyMatch(object -> object != null && object.visible());
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
        private int zIndex = 0;
        private boolean worldSpace = false;
        private boolean entityModelTarget;
        private String followBoneName;
        private MLuaCFrame followOffset = MLuaCFrame.IDENTITY;
        private LuaValue followOffsetCallback = LuaValue.NIL;
        private boolean followOffsetCallbackFailed;
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
        public int zIndex() { return zIndex; }
        public boolean worldSpace() { return worldSpace; }
        public RenderTarget target() {
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
        public void setZIndex(int zIndex) {
            if (this.zIndex == zIndex) return;
            this.zIndex = zIndex;
            invalidateDrawOrder();
        }
        public void setWorldSpace(boolean worldSpace) {
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
            if ((target == RenderTarget.WORLD_MODEL || target == RenderTarget.ENTITY_MODEL) && !model) {
                throw new IllegalArgumentException("WorldModel and EntityModel targets require a Mesh or BBModel object");
            }
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
            this.renderStage = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(renderStage);
        }
        public void setFollowEntityScope(MLuaEntityScope followEntityScope) {
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
                entityModelTarget = false;
                return;
            }
            followBoneName = boneName.trim();
            entityModelTarget = true;
            worldSpace = true;
        }
        public void setFollowOffset(MLuaCFrame offset) {
            if (offset == null) throw new IllegalArgumentException("FollowOffset must be a CFrame");
            followOffset = offset;
            followOffsetCallback = LuaValue.NIL;
            followOffsetCallbackFailed = false;
        }
        public void setFollowOffsetCallback(LuaValue callback) {
            if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("FollowOffset callback must be a function");
            followOffsetCallback = callback;
            followOffsetCallbackFailed = false;
        }
        private void sampleFollowOffset() {
            if (!followOffsetCallback.isfunction()) return;
            try {
                followOffset = MLuaCFrame.fromLua(followOffsetCallback.invoke(LuaValue.NONE).arg1());
                followOffsetCallbackFailed = false;
            } catch (RuntimeException failure) {
                if (!followOffsetCallbackFailed) {
                    MLuaLogger.LOGGER.error("RenderObject FollowOffset callback failed; keeping its last valid CFrame", failure);
                    followOffsetCallbackFailed = true;
                }
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
            copy.entityModelTarget = entityModelTarget;
            copy.followBoneName = followBoneName;
            copy.followOffset = followOffset;
            copy.followOffsetCallback = followOffsetCallback;
            copy.followOffsetCallbackFailed = followOffsetCallbackFailed;
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
            copy.resolvedTextures = resolvedTextures;
            copy.modelScale = modelScale;
            copy.renderStage = renderStage;
            copy.animationPlayer.copyStateFrom(animationPlayer);
            return copy;
        }
    }

}
