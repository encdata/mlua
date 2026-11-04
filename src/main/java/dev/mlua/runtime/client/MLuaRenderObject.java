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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
//--version 26.1
import net.minecraft.resources.Identifier;
//--endversion
//--version 1.20.1,1.21.1
import net.minecraft.resources.ResourceLocation;
//--endversion
import com.mojang.blaze3d.vertex.PoseStack;
import org.luaj.vm2.LuaValue;

class MLuaRenderObject {
    record ModelPartState(Map<String, MLuaCFrame> transforms, Set<String> hiddenParts) {
        private static final ModelPartState EMPTY = new ModelPartState(Map.of(), Set.of());
    }

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
    volatile MLuaCFrame previousFollowOffset = MLuaCFrame.IDENTITY;
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
    //--version 26.1
    volatile List<Identifier> resolvedTextures = List.of();
    volatile List<Identifier> modelTextures = List.of();
    //--endversion
    //--version 1.20.1,1.21.1
    volatile List<ResourceLocation> resolvedTextures = List.of();
    volatile List<ResourceLocation> modelTextures = List.of();
    //--endversion
    volatile MLuaBBModelRenderPlan bbModelRenderPlan = MLuaBBModelRenderPlan.empty();
    volatile float modelScale = 1.0F;
    volatile MLuaCFrame modelTransform = MLuaCFrame.IDENTITY;
    volatile String renderStage;
    volatile String heldItemId;
    final MLuaAnimationPlayer animationPlayer;
    private final Map<String, MLuaCFrame> modelPartTransforms = new HashMap<>();
    private final Set<String> hiddenModelParts = new HashSet<>();
    private volatile ModelPartState modelPartState = ModelPartState.EMPTY;

    MLuaRenderObject(int id, MLuaClientRenderManager.RenderType type, String resource) {
        this(id, type, resource, true);
    }

    MLuaRenderObject(int id, MLuaClientRenderManager.RenderType type, String resource,
                     boolean trackAnimationActivity) {
        this(id, type, resource, trackAnimationActivity,
            dev.mlua.loaderapi.LoaderApi.client().defaultWorldModelRenderStage());
    }

    MLuaRenderObject(int id, MLuaClientRenderManager.RenderType type, String resource,
                     boolean trackAnimationActivity, String defaultRenderStage) {
        this.id = id;
        this.type = type;
        this.resource = resource;
        this.renderStage = Objects.requireNonNull(defaultRenderStage, "defaultRenderStage");
        this.animationPlayer = trackAnimationActivity
                ? new MLuaAnimationPlayer(() -> MLuaClientRenderManager.updateTickRegistration(this))
                : new MLuaAnimationPlayer();
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
        return modelPartState.transforms().getOrDefault(boneId, MLuaCFrame.IDENTITY);
    }
    public boolean isModelPartVisible(List<MLuaBBModelAsset.Bone> boneChain) {
        if (boneChain == null) return true;
        Set<String> hiddenParts = modelPartState.hiddenParts();
        for (MLuaBBModelAsset.Bone bone : boneChain) {
            if (hiddenParts.contains(bone.uuid())) return false;
        }
        return true;
    }
    public boolean modelPartVisible(String boneId) {
        requireModelBone(boneId);
        MLuaBBModelAsset.Bone bone = bbmodel.boneById(boneId);
        Set<String> hiddenParts = modelPartState.hiddenParts();
        while (bone != null) {
            if (hiddenParts.contains(bone.uuid())) return false;
            String parentId = bone.parent();
            bone = parentId == null ? null : bbmodel.boneById(parentId);
        }
        return true;
    }
    public synchronized void setModelPartVisible(String boneId, boolean visible) {
        requireModelBone(boneId);
        boolean changed = visible ? hiddenModelParts.remove(boneId) : hiddenModelParts.add(boneId);
        if (!changed) return;
        refreshModelPartSnapshots();
    }
    public synchronized void resetModelPartVisibility(String boneId) {
        requireModelBone(boneId);
        if (!hiddenModelParts.remove(boneId)) return;
        refreshModelPartSnapshots();
    }
    public synchronized void setModelPartTransform(String boneId, MLuaCFrame transform) {
        requireModelBone(boneId);
        if (transform == null) throw new IllegalArgumentException("Model part transform cannot be null");
        if (transform.equals(modelPartTransforms.get(boneId))) return;
        modelPartTransforms.put(boneId, transform);
        refreshModelPartSnapshots();
    }
    public synchronized void resetModelPartTransform(String boneId) {
        requireModelBone(boneId);
        if (modelPartTransforms.remove(boneId) == null) return;
        refreshModelPartSnapshots();
    }
    ModelPartState modelPartStateSnapshot() {
        return modelPartState;
    }
    private void refreshModelPartSnapshots() {
        modelPartState = new ModelPartState(
            Map.copyOf(modelPartTransforms),
            Set.copyOf(hiddenModelParts)
        );
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
        MLuaClientRenderManager.updateHeldItemModelIndex(this, renderTarget, heldItemId);
        visible = false;
        clearEntityAttachment();
        followOffsetBinding = FollowOffsetBinding.NONE;
        animationPlayer.stop();
        modelPartTransforms.clear();
        hiddenModelParts.clear();
        refreshModelPartSnapshots();
    }
    public synchronized void setZIndex(int zIndex) {
        if (this.zIndex == zIndex) return;
        this.zIndex = zIndex;
        MLuaClientRenderManager.invalidateRenderOrder();
    }
    public synchronized void setWorldSpace(boolean worldSpace) {
        if (worldSpace && renderTarget == MLuaClientRenderManager.RenderTarget.ENTITY_MODEL) return;
        if (worldSpace && (type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL)) {
            dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
        }
        if (!worldSpace) clearEntityAttachment();
        MLuaClientRenderManager.RenderTarget previousTarget = renderTarget;
        String previousItemId = heldItemId;
        renderTarget = !worldSpace ? MLuaClientRenderManager.RenderTarget.HUD
            : (type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL
                ? MLuaClientRenderManager.RenderTarget.WORLD_MODEL : MLuaClientRenderManager.RenderTarget.WORLD_OVERLAY);
        MLuaClientRenderManager.updateHeldItemModelIndex(this, previousTarget, previousItemId);
    }
    public synchronized void setTarget(MLuaClientRenderManager.RenderTarget target) {
        if (target == null) throw new IllegalArgumentException("Render target cannot be null");
        boolean model = type == MLuaClientRenderManager.RenderType.MESH || type == MLuaClientRenderManager.RenderType.BBMODEL;
        if (target == MLuaClientRenderManager.RenderTarget.MANUAL) {
            if (!model) throw new IllegalArgumentException("Manual target requires a Mesh or BBModel object");
            clearEntityAttachment();
            followBoneName = null;
            MLuaClientRenderManager.RenderTarget previousTarget = renderTarget;
            String previousItemId = heldItemId;
            renderTarget = MLuaClientRenderManager.RenderTarget.MANUAL;
            MLuaClientRenderManager.updateHeldItemModelIndex(this, previousTarget, previousItemId);
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
        MLuaClientRenderManager.RenderTarget previousTarget = renderTarget;
        String previousItemId = heldItemId;
        renderTarget = target;
        MLuaClientRenderManager.updateHeldItemModelIndex(this, previousTarget, previousItemId);
    }
    public synchronized void setHeldItemId(String itemId) {
        String previousItemId = heldItemId;
        if (itemId == null || itemId.isBlank()) {
            this.heldItemId = null;
            MLuaClientRenderManager.updateHeldItemModelIndex(this, renderTarget, previousItemId);
            return;
        }
        String oldItemId = this.heldItemId;
        try {
            this.heldItemId = dev.mlua.minecraft.MLuaResourceLocations.parse(itemId).toString();
        } catch (IllegalArgumentException invalid) {
            this.heldItemId = oldItemId;
            throw new IllegalArgumentException("Invalid held item id: " + itemId, invalid);
        }
        MLuaClientRenderManager.updateHeldItemModelIndex(this, renderTarget, previousItemId);
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
        this.previousFollowOffset = offset;
        this.followOffsetBinding = FollowOffsetBinding.NONE;
        MLuaClientRenderManager.updateTickRegistration(this);
    }
    public synchronized void setFollowOffsetCallback(LuaValue callback, MLuaContext context) {
        if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("FollowOffset callback must be a function");
        if (context == null) throw new IllegalArgumentException("FollowOffset callback needs its owning Lua context");
        this.previousFollowOffset = this.followOffset;
        this.followOffsetBinding = new FollowOffsetBinding(callback, context);
        MLuaClientRenderManager.updateTickRegistration(this);
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
            if (resolved != null && followOffsetBinding == binding) {
                previousFollowOffset = followOffset;
                followOffset = resolved;
            }
        } catch (Error fatal) {
            throw fatal;
        } catch (RuntimeException failure) {
            if (followOffsetBinding != binding) return;
            followOffsetBinding = FollowOffsetBinding.NONE;
            MLuaClientRenderManager.updateTickRegistration(this);
            MLuaLogger.LOGGER.error("RenderObject FollowOffset callback failed; disabled it and kept the last valid CFrame", failure);
        }
    }

    boolean requiresClientTick() {
        if (destroyed) return false;
        FollowOffsetBinding binding = followOffsetBinding;
        return (bbmodel != null && animationPlayer.needsTick()) || binding.callback().isfunction();
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

    MLuaCFrame interpolatedFollowOffset(float partialTick) {
        float alpha = Float.isFinite(partialTick) ? Math.max(0.0F, Math.min(1.0F, partialTick)) : 1.0F;
        MLuaCFrame previous = previousFollowOffset;
        MLuaCFrame current = followOffset;
        return previous == current || alpha >= 1.0F ? current : previous.lerp(current, alpha);
    }

    void applyFollowOffset(PoseStack pose, float partialTick) {
        MLuaCFrame offset = interpolatedFollowOffset(partialTick);
        pose.translate(offset.x(), offset.y(), offset.z());
        MLuaAnimationScratch scratch = MLuaAnimationScratch.current();
        offset.toEulerAnglesXYZ(scratch.eulerAngles);
        double[] angles = scratch.eulerAngles;
        pose.mulPose(com.mojang.math.Axis.XP.rotation((float) angles[0]));
        pose.mulPose(com.mojang.math.Axis.YP.rotation((float) angles[1]));
        pose.mulPose(com.mojang.math.Axis.ZP.rotation((float) angles[2]));
    }

    MLuaClientRenderManager.RenderObject copy() {
        // Preview/projected objects are short-lived render snapshots. They must not register
        // themselves in the live animation tick index when their state is copied.
        MLuaClientRenderManager.RenderObject result =
                new MLuaClientRenderManager.RenderObject(id, type, resource, false);
        MLuaRenderObject copy = result;
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
        copy.previousFollowOffset = previousFollowOffset;
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
        ModelPartState partState = modelPartState;
        copy.modelPartTransforms.putAll(partState.transforms());
        copy.hiddenModelParts.addAll(partState.hiddenParts());
        copy.modelPartState = partState;
        copy.resolvedTextures = resolvedTextures;
        copy.modelTextures = modelTextures;
        copy.bbModelRenderPlan = bbModelRenderPlan;
        copy.modelScale = modelScale;
        copy.modelTransform = modelTransform;
        copy.renderStage = renderStage;
        copy.animationPlayer.copyStateFrom(animationPlayer);
        return result;
    }
}
