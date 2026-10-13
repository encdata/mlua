package dev.mlua.runtime.client;

import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.mlua.loaderapi.LoaderApi;

public final class MLuaClientRenderManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, RenderObject> OBJECTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MLuaEntityScope, java.util.Set<Integer>> ENTITY_MODELS = new ConcurrentHashMap<>();
    private static final MLuaRenderOwnershipRegistry OWNERS = new MLuaRenderOwnershipRegistry();

    private MLuaClientRenderManager() {
    }

    public static void registerOverlay() {
        LoaderApi.client().registerOverlay("mlua_render_service", graphics -> {
            if (graphics instanceof GuiGraphicsExtractor extractor) renderOverlayObjects(extractor);
        });
    }

    private static void renderOverlayObjects(GuiGraphicsExtractor graphics) {
        List<RenderObject> objects = new ArrayList<>(OBJECTS.values());
        objects.sort(Comparator.comparingInt(RenderObject::zIndex));
        for (RenderObject object : objects) {
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

    public static final class RenderObject {
        private final int id;
        private final RenderType type;
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
        private float modelScale = 1.0F;
        private final MLuaAnimationPlayer animationPlayer = new MLuaAnimationPlayer();

        private RenderObject(int id, RenderType type, String resource) {
            this.id = id;
            this.type = type;
            this.resource = resource;
        }

        public int id() { return id; }
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
        public MLuaMeshDefinition mesh() { return mesh; }
        public MLuaBBModelAsset bbmodel() { return bbmodel; }
        public MLuaAnimationPlayer animationPlayer() { return animationPlayer; }
        public float modelScale() { return modelScale; }

        public void setResource(String resource) { this.resource = resource; }
        public void setPosition(double x, double y) { this.x = x; this.y = y; }
        public void setSize(double width, double height) { this.width = width; this.height = height; }
        public void setColor(int color) { this.color = color; }
        public void setTransparency(double transparency) { this.transparency = Math.max(0.0, Math.min(1.0, transparency)); }
        public void setRotation(double rotation) { this.rotation = rotation; }
        public void setVisible(boolean visible) { this.visible = visible; }
        public void setZIndex(int zIndex) { this.zIndex = zIndex; }
        public void setWorldSpace(boolean worldSpace) { this.worldSpace = worldSpace; }
        public void setWorldPosition(double x, double y, double z) { this.worldX = x; this.worldY = y; this.worldZ = z; }
        public void setEndPosition(double x, double y) { this.endX = x; this.endY = y; }
        public void setEndWorldPosition(double x, double y, double z) { this.endWorldX = x; this.endWorldY = y; this.endWorldZ = z; }
        public void setDepth(double depth) { this.depth = Math.max(0.0, depth); }
        public void setThickness(double thickness) { this.thickness = Math.max(1.0, thickness); }
        public void setScaleWithDistance(boolean scaleWithDistance) { this.scaleWithDistance = scaleWithDistance; }
        public void setModelScale(float modelScale) { this.modelScale = Math.max(0.001F, modelScale); }
        public void setFollowEntityScope(MLuaEntityScope followEntityScope) {
            if (this.followEntityScope != null && !this.followEntityScope.equals(followEntityScope)) {
                unregisterEntityModel(this.followEntityScope, this.id);
            }
            this.followEntityScope = followEntityScope;
            if (followEntityScope != null) {
                registerEntityModel(followEntityScope, this.id);
                this.worldSpace = true;
            }
        }

        private RenderObject copy() {
            RenderObject copy = new RenderObject(id, type, resource);
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
            copy.modelScale = modelScale;
            copy.animationPlayer.copyStateFrom(animationPlayer);
            return copy;
        }
    }

}
