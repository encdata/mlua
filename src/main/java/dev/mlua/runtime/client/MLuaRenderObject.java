package dev.mlua.runtime.client;

import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.luaj.vm2.LuaValue;

class MLuaRenderObject {
    final int id;
    final MLuaClientRenderManager.RenderType type;
    volatile String luaClassName = "RenderObject";
    volatile String resource;
    volatile double x = 0.0;
    volatile double y = 0.0;
    volatile double width = 16.0;
    volatile double height = 16.0;
    volatile int color = 0xFFFFFF;
    volatile double transparency = 0.0;
    volatile double rotation = 0.0;
    volatile boolean visible = true;
    volatile boolean initialized = true;
    volatile boolean destroyed;
    final AtomicBoolean renderFailureReported = new AtomicBoolean();
    volatile int zIndex = 0;
    volatile MLuaClientRenderManager.RenderTarget renderTarget = MLuaClientRenderManager.RenderTarget.HUD;
    volatile String followBoneName;
    volatile MLuaCFrame followOffset = MLuaCFrame.IDENTITY;
    volatile FollowOffsetBinding followOffsetBinding = FollowOffsetBinding.NONE;
    volatile double worldX = 0.0;
    volatile double worldY = 0.0;
    volatile double worldZ = 0.0;
    volatile double endX = 0.0;
    volatile double endY = 0.0;
    volatile double endWorldX = 0.0;
    volatile double endWorldY = 0.0;
    volatile double endWorldZ = 0.0;
    volatile double depth = 16.0;
    volatile double thickness = 1.0;
    volatile boolean scaleWithDistance = true;
    volatile double worldRenderDistance = MLuaClientRenderManager.DEFAULT_WORLD_RENDER_DISTANCE;
    volatile MLuaEntityScope followEntityScope;
    volatile MLuaMeshDefinition mesh;
    volatile MLuaBBModelAsset bbmodel;
    volatile List<ResourceLocation> resolvedTextures = List.of();
    volatile List<ResourceLocation> modelTextures = List.of();
    volatile MLuaBBModelRenderPlan bbModelRenderPlan = MLuaBBModelRenderPlan.empty();
    volatile float modelScale = 1.0F;
    volatile MLuaCFrame modelTransform = MLuaCFrame.IDENTITY;
    volatile String renderStage;
    volatile String heldItemId;
    final MLuaAnimationPlayer animationPlayer = new MLuaAnimationPlayer();
    final ConcurrentHashMap<String, MLuaCFrame> modelPartTransforms = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Boolean> modelPartVisibility = new ConcurrentHashMap<>();

    MLuaRenderObject(int id, MLuaClientRenderManager.RenderType type, String resource) {
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
    public MLuaClientRenderManager.RenderType type() { return type; }
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
        return renderTarget == MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY || renderTarget == MLuaClientRenderManager.RenderTarget.WORLD_MODEL
            || renderTarget == MLuaClientRenderManager.RenderTarget.ENTITY_MODEL;
    }
    public MLuaClientRenderManager.RenderTarget target() { return renderTarget; }
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
    synchronized void markDestroyed() {
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
        MLuaClientRenderManager.invalidateDrawOrder();
        MLuaClientRenderManager.invalidateRenderTargetOrder();
    }
    public synchronized void setWorldSpace(boolean worldSpace) {
        if (worldSpace && renderTarget == MLuaClientRenderManager.RenderTarget.ENTITY_MODEL) return;
        if (worldSpace && (type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL)) {
            dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
        }
        if (!worldSpace) clearEntityAttachment();
        renderTarget = !worldSpace ? MLuaClientRenderManager.RenderTarget.HUD
            : (type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL
                ? MLuaClientRenderManager.RenderTarget.WORLD_MODEL : MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY);
        MLuaClientRenderManager.updateRenderTargetIndex(this);
    }
    public synchronized void setTarget(MLuaClientRenderManager.RenderTarget target) {
        if (target == null) throw new IllegalArgumentException("Render target cannot be null");
        boolean model = type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL;
        if (target == MLuaClientRenderManager.RenderTarget.MANUAL) {
            if (!model) throw new IllegalArgumentException("Manual target requires a Mesh or BBModel object");
            clearEntityAttachment();
            followBoneName = null;
            renderTarget = MLuaClientRenderManager.RenderTarget.MANUAL;
            MLuaClientRenderManager.updateRenderTargetIndex(this);
            return;
        }
        if ((target == MLuaClientRenderManager.RenderTarget.WORLD_MODEL || target == MLuaClientRenderManager.RenderTarget.ENTITY_MODEL
                || target == MLuaClientRenderManager.RenderTarget.FIRST_PERSON_ITEM || target == MLuaClientRenderManager.RenderTarget.THIRD_PERSON_ITEM) && !model) {
            throw new IllegalArgumentException("WorldModel, EntityModel, FirstPersonItem, and ThirdPersonItem targets require a Mesh or BBModel object");
        }
        if (target == MLuaClientRenderManager.RenderTarget.WORLD_MODEL) {
            dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
        }
        if (target == MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY && (type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL)) {
            throw new IllegalArgumentException("Mesh and BBModel objects use the WorldModel or EntityModel target");
        }
        if (target != MLuaClientRenderManager.RenderTarget.ENTITY_MODEL) {
            clearEntityAttachment();
            followBoneName = null;
        }
        renderTarget = target;
        MLuaClientRenderManager.updateRenderTargetIndex(this);
    }
    public synchronized void setHeldItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            this.heldItemId = null;
            MLuaClientRenderManager.updateRenderTargetIndex(this);
            return;
        }
        String oldItemId = this.heldItemId;
        try {
            this.heldItemId = dev.mlua.minecraft.MLuaResourceLocations.parse(itemId).toString();
        } catch (IllegalArgumentException invalid) {
            this.heldItemId = oldItemId;
            throw new IllegalArgumentException("Invalid held item id: " + itemId, invalid);
        }
        MLuaClientRenderManager.updateRenderTargetIndex(this);
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
        if (type != MLuaClientRenderManager.RenderType.MESH && type != MLuaClientRenderManager.RenderType.BBMODEL) {
            throw new IllegalArgumentException("is available only on Mesh and BBModel objects");
        }
        if (modelTransform == null) throw new IllegalArgumentException("must be a CFrame");
        requireFiniteCFrame(modelTransform);
        this.modelTransform = modelTransform;
    }
    public synchronized void setWorldRenderDistance(double distance) {
        if (!Double.isFinite(distance) || distance < 1.0D || distance > MLuaClientRenderManager.MAX_WORLD_RENDER_DISTANCE) {
            throw new IllegalArgumentException("World render distance must be between 1 and "
                + (int)MLuaClientRenderManager.MAX_WORLD_RENDER_DISTANCE + " blocks");
        }
        this.worldRenderDistance = distance;
    }
    public synchronized void setRenderStage(String renderStage) {
        String normalized;
        try {
            normalized = dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
        } catch (IllegalArgumentException notAModelStage) {
            if (target() == MLuaClientRenderManager.RenderTarget.WORLD_MODEL) throw notAModelStage;
            normalized = dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(renderStage);
        }
        this.renderStage = normalized;
    }
    public synchronized void setFollowEntityScope(MLuaEntityScope followEntityScope) {
        if (followEntityScope == null) {
            clearEntityAttachment();
            if (renderTarget == MLuaClientRenderManager.RenderTarget.ENTITY_MODEL) {
                renderTarget = type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL
                    ? MLuaClientRenderManager.RenderTarget.WORLD_MODEL : MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY;
                if (renderTarget == MLuaClientRenderManager.RenderTarget.WORLD_MODEL) {
                    normalizeWorldModelStageAfterDetach();
                }
            }
            return;
        }
        if (this.followEntityScope != null && !this.followEntityScope.equals(followEntityScope)) {
            MLuaClientRenderManager.unregisterEntityModel(this.followEntityScope, this.id);
        }
        this.followEntityScope = followEntityScope;
        renderTarget = type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL
            ? MLuaClientRenderManager.RenderTarget.ENTITY_MODEL : MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY;
        MLuaClientRenderManager.registerEntityModel(followEntityScope, this.id);
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
        if (followEntityScope != null) MLuaClientRenderManager.unregisterEntityModel(followEntityScope, id);
        followEntityScope = null;
        followBoneName = null;
    }

    public synchronized void setFollowBoneName(String boneName) {
        if (type != MLuaClientRenderManager.RenderType.MESH && type != MLuaClientRenderManager.RenderType.BBMODEL) {
            throw new IllegalArgumentException("FollowBone requires a Mesh or BBModel object");
        }
        if (boneName == null || boneName.isBlank()) {
            this.followBoneName = null;
            return;
        }
        this.followBoneName = boneName.trim();
        this.renderTarget = MLuaClientRenderManager.RenderTarget.ENTITY_MODEL;
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
    void sampleFollowOffset() {
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
        static final FollowOffsetBinding NONE = new FollowOffsetBinding(LuaValue.NIL, null);
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

    void applyFollowOffset(PoseStack pose) {
        pose.translate(followOffset.x(), followOffset.y(), followOffset.z());
        double[] angles = followOffset.toEulerAnglesXYZ();
        pose.mulPose(com.mojang.math.Axis.XP.rotation((float) angles[0]));
        pose.mulPose(com.mojang.math.Axis.YP.rotation((float) angles[1]));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation((float) angles[2]));
    }

    MLuaClientRenderManager.RenderObject copy() {
        MLuaClientRenderManager.RenderObject copy = new MLuaClientRenderManager.RenderObject(id, type, resource);
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
