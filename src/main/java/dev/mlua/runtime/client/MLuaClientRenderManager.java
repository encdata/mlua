package dev.mlua.runtime.client;

import dev.mlua.minecraft.MLuaResourceLocations;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.runtime.client.model.MLuaBBModelAsset;
import dev.mlua.runtime.client.model.MLuaAnimationPlayer;
import dev.mlua.runtime.client.model.MLuaMeshDefinition;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.luaj.vm2.LuaValue;

public final class MLuaClientRenderManager {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final ConcurrentHashMap<Integer, RenderObject> OBJECTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<MLuaEntityScope, java.util.Set<Integer>> ENTITY_MODELS = new ConcurrentHashMap<>();
    private static final MLuaEmbeddedTextureRegistry EMBEDDED_TEXTURES = new MLuaEmbeddedTextureRegistry();
    private static final MLuaRenderOwnershipRegistry OWNERS = new MLuaRenderOwnershipRegistry();
    private static final AtomicBoolean WORLD_RENDER_FAILURE_REPORTED = new AtomicBoolean();
    private static volatile RenderObject[] drawOrderSnapshot = new RenderObject[0];
    private static volatile boolean drawOrderDirty = true;
    private static boolean animationClockRegistered;

    private MLuaClientRenderManager() {
    }

    public static void registerOverlay() {
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
        for (String stage : dev.mlua.loaderapi.ClientBridge.WORLD_RENDER_STAGES) {
            dev.mlua.loaderapi.LoaderApi.client().registerWorldRenderer(stage,
                context -> renderWorldContext(stage, context));
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

    private static void renderWorldContext(String stage, Object context) {
        try {
            Object rawPose = invokeOptional(context, "matrixStack", "getPoseStack", "poseStack");
            Object rawBuffers = invokeOptional(context, "consumers", "getRenderTypeBuffer", "bufferSource");
            Object rawCamera = invokeOptional(context, "camera", "getCamera");
            Float renderPartialTick = MLuaRenderCallbacks.readPartialTick(context);
            if (renderPartialTick == null) renderPartialTick = MLuaRenderCallbacks.currentClientPartialTick();
            MLuaRenderCallbacks.renderWorld(stage, context, rawPose, rawBuffers, rawCamera);
            if (!(rawPose instanceof PoseStack pose) || !(rawBuffers instanceof MultiBufferSource buffers)) return;
            Vec3 camera = rawCamera == null ? Minecraft.getInstance().gameRenderer.getMainCamera().getPosition() :
                (Vec3) invokeOptional(rawCamera, "getPosition", "position");
            if (camera == null) camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 look = cameraLook(rawCamera == null ? Minecraft.getInstance().gameRenderer.getMainCamera() : rawCamera);
            for (RenderObject object : OBJECTS.values()) {
                synchronized (object.executionLock()) {
                    if (object.destroyed || !object.visible() || object.target() != RenderTarget.WORLD_MODEL
                            || !object.renderStage().equals(stage)) continue;
                    try {
                        renderWorldModel(pose, buffers, object, camera, look,
                            renderPartialTick == null ? 1.0F : renderPartialTick);
                    } catch (RuntimeException failure) {
                        reportWorldRenderFailure(failure);
                    }
                }
            }
        } catch (RuntimeException failure) {
            reportWorldRenderFailure(failure);
        }
    }

    private static void reportWorldRenderFailure(Throwable failure) {
        if (WORLD_RENDER_FAILURE_REPORTED.compareAndSet(false, true)) {
            MLuaLogger.LOGGER.warn("[MLua] World render hook failed; HUD rendering remains available.", failure);
        }
    }

    private static Object invoke(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            try { return target.getClass().getMethod(name).invoke(target); }
            catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    private static Object invokeOptional(Object target, String... names) {
        try {
            return target == null ? null : invoke(target, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Vec3 cameraLook(Object camera) {
        Object value = invokeOptional(camera, "getLookVector", "getLookDirection", "getForwardVector", "getLookAngle", "lookVector", "lookDirection");
        Object x = invokeOptional(value, "x");
        Object y = invokeOptional(value, "y");
        Object z = invokeOptional(value, "z");
        return x instanceof Number && y instanceof Number && z instanceof Number
            ? new Vec3(((Number)x).doubleValue(), ((Number)y).doubleValue(), ((Number)z).doubleValue())
            : new Vec3(0, 0, 1);
    }

    private static Entity resolveFollowEntity(MLuaEntityScope scope) {
        Minecraft minecraft = Minecraft.getInstance();
        if (scope.side() != dev.mlua.common.enums.Side.CLIENT || minecraft.level == null
            || !minecraft.level.dimension().location().toString().equals(scope.dimension())) return null;
        Entity entity = minecraft.level.getEntity(scope.entityId());
        return entity != null && entity.getUUID().equals(scope.uuid()) ? entity : null;
    }

    private static void renderWorldModel(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                         Vec3 camera, Vec3 look, float partialTick) {
        double x = object.worldX;
        double y = object.worldY;
        double z = object.worldZ;
        if (object.followEntityScope != null) {
            Entity entity = resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) return;
            x += entity.getX(); y += entity.getY(); z += entity.getZ();
        }
        Vec3 relative = new Vec3(x - camera.x, y - camera.y, z - camera.z);
        double distanceSquared = relative.lengthSqr();
        if (distanceSquared > 256.0 * 256.0) return;
        double radius = object.type == RenderType.BBMODEL && object.bbmodel != null ? 4.0 : 2.0;
        if (distanceSquared > 0.01 && relative.dot(look) < -radius) return;
        int packedLight = MLuaWorldLight.packedAt(x, y, z, 15728880);
        pose.pushPose();
        try {
            pose.translate(x - camera.x, y - camera.y, z - camera.z);
            if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
            if (object.type == RenderType.MESH && object.mesh != null) {
                ResourceLocation texture = resolveTexture(object.mesh.texture());
                MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                    texture == null ? MLuaRenderHelper.fallbackTexture() : texture, packedLight, 0);
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

    private static void renderBBModelWorld(PoseStack pose, MultiBufferSource buffers, RenderObject object,
                                          int light, int overlay, float partialTick) {
        List<ResourceLocation> textures = object.modelTextures;
        var animation = object.animationPlayer.active() == null ? null : object.bbmodel.animations().get(object.animationPlayer.active());
        float[] animatedPosition = new float[3];
        float[] animatedRotation = new float[3];
        float[] animatedScale = new float[3];
        for (var cube : object.bbmodel.cubes()) {
            pose.pushPose();
            try {
                for (var bone : object.bbmodel.boneChainForCube(cube.uuid())) {
                    object.animationPlayer.applyBoneTransform(pose, animation, bone, partialTick,
                        animatedPosition, animatedRotation, animatedScale);
                }
                object.animationPlayer.applyCubeTransform(pose, animation, cube, partialTick,
                    animatedPosition, animatedRotation, animatedScale);
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
        if (model == null || scope == null || pose == null || buffers == null) return;
        java.util.Set<Integer> attachedRenderIds = ENTITY_MODELS.get(scope);
        if (attachedRenderIds == null || attachedRenderIds.isEmpty()) return;
        for (Integer renderId : attachedRenderIds) {
            RenderObject object = OBJECTS.get(renderId);
            if (object == null) continue;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                        || !scope.equals(object.followEntityScope) || object.followBoneName == null) continue;
                pose.pushPose();
                try {
                    if (!MLuaModelAdapterRegistry.applyPartTransform(model, object.followBoneName, pose)) continue;
                    object.applyFollowOffset(pose);
                    pose.translate(object.worldX, object.worldY, object.worldZ);
                    if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.type == RenderType.MESH && object.mesh != null) {
                        ResourceLocation texture = resolveTexture(object.mesh.texture());
                        MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                            texture == null ? MLuaRenderHelper.fallbackTexture() : texture, light, overlay);
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
            object.resolvedTextures = registerEmbeddedTextures(asset);
            object.modelTextures = resolveModelTextures(asset, object.resolvedTextures);
            return id;
        } catch (RuntimeException failure) {
            remove(id, owner);
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
                        EMBEDDED_TEXTURES.release(location);
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
        EMBEDDED_TEXTURES.drain().forEach(location -> Minecraft.getInstance().getTextureManager().release(location));
    }

    private static void releaseEmbeddedTextures(RenderObject object) {
        for (ResourceLocation location : object.resolvedTextures) {
            if (location != null && EMBEDDED_TEXTURES.release(location)) {
                Minecraft.getInstance().getTextureManager().release(location);
            }
        }
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

    public static void renderAll(GuiGraphics graphics) {
        MLuaRenderCallbacks.renderHud(graphics, graphics.guiWidth(), graphics.guiHeight());
        for (RenderObject object : orderedRenderObjects()) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible) continue;
                if (object.target() == RenderTarget.WORLD_MODEL || object.target() == RenderTarget.ENTITY_MODEL) continue;
                render(graphics, object);
            }
        }
    }

    private static void render(GuiGraphics graphics, RenderObject object) {
        if (object.target() == RenderTarget.ENTITY_MODEL) return;
        if (object.target() == RenderTarget.WORLD_OVERLAY) {
            renderWorld(graphics, object);
            return;
        }
        switch (object.type) {
            case RECT -> renderRect(graphics, object);
            case IMAGE -> renderImage(graphics, object);
            case ITEM -> renderItem(graphics, object, false);
            case BLOCK -> renderItem(graphics, object, true);
            case TEXT -> renderText(graphics, object);
            case LINE, BEAM -> renderLine2d(graphics, object.x, object.y, object.endX, object.endY, object);
            case BOX -> renderRect(graphics, object);
            case MESH, BBMODEL -> renderModelPreview(graphics, object);
        }
    }

    private static void renderWorld(GuiGraphics graphics, RenderObject object) {
        if (object.type == RenderType.LINE || object.type == RenderType.BEAM) {
            renderWorldLine(graphics, object);
            return;
        }
        if (object.type == RenderType.BOX) {
            renderWorldBox(graphics, object);
            return;
        }
        Projection projection = project(object.worldX, object.worldY, object.worldZ, graphics.guiWidth(), graphics.guiHeight());
        if (object.followEntityScope != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) {
                return;
            }
            projection = project(
                entity.getX() + object.worldX,
                entity.getY() + entity.getBbHeight() * 0.5 + object.worldY,
                entity.getZ() + object.worldZ,
                graphics.guiWidth(),
                graphics.guiHeight()
            );
        }
        if (projection == null) {
            return;
        }

        double drawWidth = object.width;
        double drawHeight = object.height;
        if (object.scaleWithDistance) {
            drawWidth *= projection.scale;
            drawHeight *= projection.scale;
        }

        RenderObject projected = object.copy();
        projected.worldSpace = false;
        projected.x = projection.x - drawWidth / 2.0;
        projected.y = projection.y - drawHeight / 2.0;
        projected.width = Math.max(1.0, drawWidth);
        projected.height = Math.max(1.0, drawHeight);
        render(graphics, projected);
    }

    private static void renderModelPreview(GuiGraphics graphics, RenderObject object) {
        if (object.bbmodel != null && renderBBModel(graphics, object)) {
            return;
        }
        // The mesh vertex backend is installed incrementally per loader. Until a
        // world vertex callback is active, render a visible bounds preview in UI
        // space instead of silently dropping a loaded asset.
        double width = object.width;
        double height = object.height;
        if (object.bbmodel != null && !object.bbmodel.cubes().isEmpty()) {
            var first = object.bbmodel.cubes().get(0);
            float minX = first.from(0), minY = first.from(1), minZ = first.from(2);
            float maxX = first.to(0), maxY = first.to(1), maxZ = first.to(2);
            for (var cube : object.bbmodel.cubes()) {
                minX = Math.min(minX, cube.from(0)); minY = Math.min(minY, cube.from(1)); minZ = Math.min(minZ, cube.from(2));
                maxX = Math.max(maxX, cube.to(0)); maxY = Math.max(maxY, cube.to(1)); maxZ = Math.max(maxZ, cube.to(2));
            }
            width = Math.max(1.0, (maxX - minX) * object.width / 16.0);
            height = Math.max(1.0, (maxY - minY) * object.height / 16.0);
        }
        RenderObject preview = object.copy();
        preview.worldSpace = false;
        preview.x = object.x;
        preview.y = object.y;
        preview.width = width;
        preview.height = height;
        renderRect(graphics, preview);
    }

    private static boolean renderBBModel(GuiGraphics graphics, RenderObject object) {
        try {
            Object rawBuffer = graphics.getClass().getMethod("bufferSource").invoke(graphics);
            if (!(rawBuffer instanceof MultiBufferSource buffer)) return false;
            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(object.x, object.y, object.zIndex * 0.01F);
                float scale = (float)(Math.min(object.width, object.height) / 16.0);
                pose.scale(scale, scale, scale);
                Float renderPartialTick = MLuaRenderCallbacks.currentClientPartialTick();
                float animationPartialTick = renderPartialTick == null ? 1.0F : renderPartialTick;
                renderBBModelWorld(pose, buffer, object, 15728880, 0, animationPartialTick);
            } finally {
                pose.popPose();
            }
            try { graphics.getClass().getMethod("flush").invoke(graphics); } catch (ReflectiveOperationException ignored) { }
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static void renderWorldLine(GuiGraphics graphics, RenderObject object) {
        double startX = object.worldX;
        double startY = object.worldY;
        double startZ = object.worldZ;
        if (object.followEntityScope != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) {
                return;
            }
            startX = entity.getX() + object.worldX;
            startY = entity.getY() + entity.getBbHeight() * 0.5 + object.worldY;
            startZ = entity.getZ() + object.worldZ;
        }
        Projection start = project(startX, startY, startZ, graphics.guiWidth(), graphics.guiHeight());
        Projection end = project(object.endWorldX, object.endWorldY, object.endWorldZ, graphics.guiWidth(), graphics.guiHeight());
        if (start == null || end == null) {
            return;
        }
        renderLine2d(graphics, start.x, start.y, end.x, end.y, object);
    }

    private static void renderWorldBox(GuiGraphics graphics, RenderObject object) {
        double centerX = object.worldX;
        double centerY = object.worldY;
        double centerZ = object.worldZ;
        if (object.followEntityScope != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return;
            }
            Entity entity = resolveFollowEntity(object.followEntityScope);
            if (entity == null || !entity.isAlive()) {
                return;
            }
            centerX = entity.getX() + object.worldX;
            centerY = entity.getY() + entity.getBbHeight() * 0.5 + object.worldY;
            centerZ = entity.getZ() + object.worldZ;
        }
        double halfWidth = object.width / 2.0;
        double halfHeight = object.height / 2.0;
        double halfDepth = object.depth / 2.0;
        Projection[] corners = new Projection[] {
            project(centerX - halfWidth, centerY - halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY - halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY + halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY + halfHeight, centerZ - halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY - halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY - halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX + halfWidth, centerY + halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight()),
            project(centerX - halfWidth, centerY + halfHeight, centerZ + halfDepth, graphics.guiWidth(), graphics.guiHeight())
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            Projection a = corners[edge[0]];
            Projection b = corners[edge[1]];
            if (a != null && b != null) {
                renderLine2d(graphics, a.x, a.y, b.x, b.y, object);
            }
        }
    }

    private static Projection project(double worldX, double worldY, double worldZ, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) {
            return null;
        }

        Vec3 cameraPos = camera.getPosition();
        Vec3 look = cameraLook(camera);
        var upVector = camera.getUpVector();
        Vec3 up = new Vec3(upVector.x(), upVector.y(), upVector.z());
        Vec3 right = up.cross(look);
        Vec3 rel = new Vec3(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);

        double camX = rel.dot(right);
        double camY = rel.dot(up);
        double camZ = rel.dot(look);
        if (camZ <= 0.05) {
            return null;
        }

        double fov = Math.toRadians(Math.max(1.0, mc.options.fov().get()));
        double focal = (screenHeight / 2.0) / Math.tan(fov / 2.0);
        double x = (screenWidth / 2.0) + (camX * focal / camZ);
        double y = (screenHeight / 2.0) - (camY * focal / camZ);
        double scale = focal / camZ / 32.0;
        if (x < -256 || x > screenWidth + 256 || y < -256 || y > screenHeight + 256) {
            return null;
        }
        return new Projection(x, y, scale);
    }

    private static void renderRect(GuiGraphics graphics, RenderObject object) {
        int color = ((int) Math.round((1.0 - object.transparency) * 255.0) << 24) | (object.color & 0x00FFFFFF);
        graphics.fill((int) object.x, (int) object.y, (int) (object.x + object.width), (int) (object.y + object.height), color);
    }

    private static void renderImage(GuiGraphics graphics, RenderObject object) {
        ResourceLocation texture = resolveTexture(object.resource);
        if (texture == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(object.x, object.y, 0.0F);
        if (object.rotation != 0.0) {
            graphics.pose().translate(object.width / 2.0, object.height / 2.0, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) object.rotation));
            graphics.pose().translate(-object.width / 2.0, -object.height / 2.0, 0.0F);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, (float) (1.0 - object.transparency));
        graphics.blit(texture, 0, 0, 0, 0, (int) object.width, (int) object.height, (int) object.width, (int) object.height);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void renderItem(GuiGraphics graphics, RenderObject object, boolean block) {
        ItemStack stack = resolveStack(object.resource, block);
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(object.x, object.y, 0.0F);
        float scale = (float) (Math.min(object.width, object.height) / 16.0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static void renderText(GuiGraphics graphics, RenderObject object) {
        String text = object.resource != null ? object.resource : "";
        int color = ((int) Math.round((1.0 - object.transparency) * 255.0) << 24) | (object.color & 0x00FFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(object.x, object.y, 0.0F);
        if (object.rotation != 0.0) {
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) object.rotation));
        }
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private static void renderLine2d(GuiGraphics graphics, double x1, double y1, double x2, double y2, RenderObject object) {
        int color = ((int) Math.round((1.0 - object.transparency) * 255.0) << 24) | (object.color & 0x00FFFFFF);
        double dx = x2 - x1;
        double dy = y2 - y1;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))));
        double thickness = Math.max(1.0, object.thickness);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / (double) steps;
            int px = (int) Math.round(x1 + dx * t);
            int py = (int) Math.round(y1 + dy * t);
            graphics.fill(px, py, px + (int) Math.round(thickness), py + (int) Math.round(thickness), color);
        }
    }

    private static ResourceLocation resolveTexture(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        ResourceLocation location = path.contains(":") ? MLuaResourceLocations.parse(path) : MLuaResourceLocations.build("mlua", path);
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent() ? location : null;
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

    private static ItemStack resolveStack(String id, boolean block) {
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
        private boolean entityModelTarget;
        private String followBoneName;
        private volatile MLuaCFrame followOffset = MLuaCFrame.IDENTITY;
        private volatile FollowOffsetBinding followOffsetBinding = FollowOffsetBinding.NONE;
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
        private List<ResourceLocation> resolvedTextures = List.of();
        private List<ResourceLocation> modelTextures = List.of();
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
        public boolean destroyed() { return destroyed; }
        public boolean markRenderFailureReported() { return renderFailureReported.compareAndSet(false, true); }
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
        private void markDestroyed() {
            if (destroyed) return;
            destroyed = true;
            visible = false;
            setFollowEntityScope(null);
            followOffsetBinding = FollowOffsetBinding.NONE;
            animationPlayer.stop();
        }
        public void setZIndex(int zIndex) {
            if (this.zIndex == zIndex) return;
            this.zIndex = zIndex;
            invalidateDrawOrder();
        }
        public void setWorldSpace(boolean worldSpace) {
            if (worldSpace && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            }
            if (!worldSpace) {
                setFollowEntityScope(null);
                this.followBoneName = null;
                this.entityModelTarget = false;
            }
            this.worldSpace = worldSpace;
        }
        public void setTarget(RenderTarget target) {
            if (target == null) throw new IllegalArgumentException("Render target cannot be null");
            boolean model = type == RenderType.MESH || type == RenderType.BBMODEL;
            if ((target == RenderTarget.WORLD_MODEL || target == RenderTarget.ENTITY_MODEL) && !model) {
                throw new IllegalArgumentException("WorldModel and EntityModel targets require a Mesh or BBModel object");
            }
            if (target == RenderTarget.WORLD_MODEL) {
                dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage);
            }
            if (target == RenderTarget.WORLD_OVERLAY && (type == RenderType.MESH || type == RenderType.BBMODEL)) {
                throw new IllegalArgumentException("Mesh and BBModel objects use the WorldModel or EntityModel target");
            }
            if (target == RenderTarget.HUD) {
                setFollowEntityScope(null);
                this.followBoneName = null;
            } else if (target != RenderTarget.ENTITY_MODEL) {
                this.followBoneName = null;
            }
            this.entityModelTarget = target == RenderTarget.ENTITY_MODEL;
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
                ? dev.mlua.loaderapi.LoaderApi.client().normalizeSupportedWorldModelStage(renderStage)
                : dev.mlua.loaderapi.ClientBridge.normalizeWorldRenderStage(renderStage);
            this.renderStage = normalized;
        }
        public void setFollowEntityScope(MLuaEntityScope followEntityScope) {
            if (this.followEntityScope != null && !this.followEntityScope.equals(followEntityScope)) {
                unregisterEntityModel(this.followEntityScope, this.id);
            }
            this.followEntityScope = followEntityScope;
            if (followEntityScope == null) {
                this.followBoneName = null;
                this.entityModelTarget = false;
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
                this.followBoneName = null;
                this.entityModelTarget = false;
                return;
            }
            this.followBoneName = boneName.trim();
            this.entityModelTarget = true;
            this.worldSpace = true;
        }
        public void setFollowOffset(MLuaCFrame offset) {
            if (offset == null) throw new IllegalArgumentException("FollowOffset must be a CFrame");
            this.followOffset = offset;
            this.followOffsetBinding = FollowOffsetBinding.NONE;
        }
        public void setFollowOffsetCallback(LuaValue callback, MLuaContext context) {
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
                synchronized (context.executionLock()) {
                    if (followOffsetBinding != binding) return;
                    MLuaLuaBindings.bindContext(context.globals(), context);
                    LuaValue result = callback.invoke(LuaValue.NONE).arg1();
                    MLuaCFrame resolved = MLuaCFrame.fromLua(result);
                    // A script may replace the callback while it is executing from another thread.
                    // Do not let the older result overwrite the newer value.
                    if (followOffsetBinding == binding) followOffset = resolved;
                }
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
        private void applyFollowOffset(PoseStack pose) {
            pose.translate(followOffset.x(), followOffset.y(), followOffset.z());
            double[] angles = followOffset.toEulerAnglesXYZ();
            pose.mulPose(com.mojang.math.Axis.XP.rotation((float) angles[0]));
            pose.mulPose(com.mojang.math.Axis.YP.rotation((float) angles[1]));
            pose.mulPose(com.mojang.math.Axis.ZP.rotation((float) angles[2]));
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
            copy.executionLock = executionLock;
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
            copy.modelTextures = modelTextures;
            copy.modelScale = modelScale;
            copy.renderStage = renderStage;
            copy.animationPlayer.copyStateFrom(animationPlayer);
            return copy;
        }
    }

    private record Projection(double x, double y, double scale) {}
}
