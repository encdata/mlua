package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.core.MLuaContext;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.module.MLuaRegistration;
import java.lang.ref.WeakReference;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Creates short-lived Lua views of Minecraft render state and submits draw requests within their lifetime. */
final class MLuaRenderFrameContextFactory {
    private static final Class<?> RENDER_COLLECTOR_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.SubmitNodeCollector");
    private static final Class<?> MULTI_BUFFER_SOURCE_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.MultiBufferSource");
    private static final Class<?> ENTITY_RENDER_STATE_CLASS = findOptionalClass(
        "net.minecraft.client.renderer.entity.state.EntityRenderState");

    private MLuaRenderFrameContextFactory() { }

    static LuaValue create(MLuaContext callbackContext, String passId, java.util.function.Consumer<Throwable> failureHandler,
            String target, Object poseStack, Object bufferSource, Object model, float partialTick,
            boolean hasPartialTick, int packedLight, int packedOverlay, Object nativeContext,
            Object... additionalFields) {
        var context = new LuaTable();
        LuaTable contextValues = new LuaTable();
        context.rawset("__context_values", contextValues);
        MLuaRenderFrameScope frameScope = new MLuaRenderFrameScope();
        try {
            boolean collectorAvailable = isRenderCollector(bufferSource);
            boolean bufferSourceAvailable = isMultiBufferSource(bufferSource);
            contextValues.set("Target", LuaValue.valueOf(target));
            contextValues.set("PoseStack", scopedUserdata(frameScope, poseStack));
            contextValues.set("BufferSource", scopedUserdata(frameScope, bufferSourceAvailable ? bufferSource : null));
            contextValues.set("RenderCollector", scopedUserdata(frameScope, collectorAvailable ? bufferSource : null));
            contextValues.set("HasPoseStack", LuaValue.valueOf(poseStack != null));
            contextValues.set("HasBufferSource", LuaValue.valueOf(bufferSourceAvailable));
            contextValues.set("HasRenderSource", LuaValue.valueOf(collectorAvailable || bufferSourceAvailable));
            LuaTable drawObjectTypes = drawObjectTypes(bufferSource, poseStack != null);
            contextValues.set("HasDrawObjectSource", LuaValue.valueOf(!drawObjectTypes.get(1).isnil()));
            contextValues.set("DrawObjectTypes", drawObjectTypes);
            contextValues.set("Model", scopedUserdata(frameScope, model));
            contextValues.set("PartialTick", LuaValue.valueOf(partialTick));
            contextValues.set("HasPartialTick", LuaValue.valueOf(hasPartialTick));
            MLuaRenderFrameClock.Snapshot currentFrame = MLuaRenderFrameClock.current();
            contextValues.set("DeltaTime", LuaValue.valueOf(currentFrame.deltaTime()));
            contextValues.set("HasDeltaTime", LuaValue.valueOf(currentFrame.available()));
            contextValues.set("PackedLight", LuaValue.valueOf(packedLight));
            contextValues.set("PackedOverlay", LuaValue.valueOf(packedOverlay));
            contextValues.set("NativeContext", scopedUserdata(frameScope, nativeContext));
            Object renderState = nativeContext != null && ENTITY_RENDER_STATE_CLASS != null
                && ENTITY_RENDER_STATE_CLASS.isInstance(nativeContext) ? nativeContext : null;
            contextValues.set("RenderState", scopedUserdata(frameScope, renderState));
            contextValues.set("PassId", LuaValue.valueOf(passId));
            for (int index = 0; index + 1 < additionalFields.length; index += 2) {
                String name = (String) additionalFields[index];
                LuaValue value = (LuaValue) additionalFields[index + 1];
                if (value.isuserdata()) {
                    Object nativeValue = value.touserdata();
                    contextValues.set(name, scopedUserdata(frameScope, nativeValue));
                } else {
                    contextValues.set(name, value);
                }
            }
            LuaValue instance = callbackContext.registration().attachClassInstance(context, "RenderContext", null);
            if (!instance.istable()) {
                throw new IllegalStateException("RenderContext class attachment did not produce a Lua table");
            }
            instance.rawset("__mluaRenderDrawRequest", LuaValue.userdataOf(new RenderDrawRequest(
                frameScope, instance.get("PoseStack"),
                collectorAvailable ? instance.get("RenderCollector")
                    : bufferSourceAvailable ? instance.get("BufferSource") : LuaValue.NIL,
                new WeakReference<>(callbackContext.session()), callbackContext.ownerModId(),
                target, packedLight, packedOverlay, partialTick)));
            MLuaRenderFrameContexts.register(instance.checktable(), frameScope);
            return instance;
        } catch (VirtualMachineError | ThreadDeath fatal) {
            frameScope.close();
            throw fatal;
        } catch (Throwable failure) {
            frameScope.close();
            failureHandler.accept(failure);
            return LuaValue.NIL;
        }
    }

    /** Called by the registered RenderContext:DrawObject class method. */
    static LuaValue drawObject(MLuaRegistration registration, LuaValue context,
                                     LuaValue handle, LuaValue transformValue) {
        LuaValue requestValue = context.istable() ? context.checktable().rawget("__mluaRenderDrawRequest") : LuaValue.NIL;
        if (!requestValue.isuserdata(RenderDrawRequest.class)) {
            throw new LuaError("[MLua] RenderContext:DrawObject requires a live render context.");
        }
        RenderDrawRequest request = (RenderDrawRequest)requestValue.touserdata(RenderDrawRequest.class);
        if (!MLuaRenderFrameContexts.owns(context.checktable(), request.scope())) {
            throw new LuaError("[MLua] RenderContext:DrawObject requires the original active RenderContext table.");
        }
        if (!request.scope().view().isActive()) {
            throw new LuaError("[MLua] RenderContext:DrawObject can only run during its render callback.");
        }
        String target = request.target();
        if ("Hud".equals(target)) {
            throw new LuaError("[MLua] RenderContext:DrawObject cannot submit a 3D RenderObject from a Hud callback; use the HUD render APIs for screen-space drawing.");
        }
        if (!handle.istable() || !registration.isInstanceOf(handle, "RenderObject")) {
            throw new LuaError("[MLua] RenderContext:DrawObject expects a live RenderObject instance.");
        }
        LuaValue idValue = handle.checktable().rawget(dev.mlua.modules.render.service.MLuaRenderService.OBJECT_ID);
        if (!idValue.isint()) {
            throw new LuaError("[MLua] RenderContext:DrawObject received an invalid RenderObject handle.");
        }
        MLuaCFrame transform = MLuaCFrame.IDENTITY;
        if (!transformValue.isnil()) {
            if (!MLuaCFrame.isCFrame(transformValue)) {
                throw new LuaError("[MLua] RenderContext:DrawObject transform must be a CFrame.");
            }
            transform = MLuaCFrame.fromLua(transformValue);
            double[] components = {transform.x(), transform.y(), transform.z(), transform.r00(), transform.r01(),
                transform.r02(), transform.r10(), transform.r11(), transform.r12(), transform.r20(),
                transform.r21(), transform.r22()};
            for (double component : components) {
                if (!Double.isFinite(component)) {
                    throw new LuaError("[MLua] RenderContext:DrawObject transform must be finite.");
                }
            }
        }
        Object livePose = unwrapFrameValue(request.poseStack(), "PoseStack", target);
        Object liveSource = unwrapFrameValue(request.bufferSource(), "render source", target);
        try {
            boolean drawn = MLuaClientRenderManager.drawObjectInRenderContext(idValue.toint(), request.owner(),
                livePose, liveSource, transform, request.light(), request.overlay(), request.partialTick());
            return LuaValue.valueOf(drawn);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new LuaError("[MLua] RenderContext:DrawObject " + failure.getMessage());
        }
    }

    /** Runs a Lua draw operation inside a native pose-stack scope restored to its entry depth. */
    static Varargs withPose(LuaValue contextValue, LuaValue callback) {
        if (callback == null || !callback.isfunction()) {
            throw new LuaError("[MLua] RenderContext:WithPose expects a callback function.");
        }
        if (contextValue == null || !contextValue.istable()) {
            throw new LuaError("[MLua] RenderContext:WithPose requires a live render context.");
        }
        MLuaRenderFrameScope scope = MLuaRenderFrameContexts.get(contextValue.checktable());
        if (scope == null || !scope.view().isActive()) {
            throw new LuaError("[MLua] RenderContext:WithPose can only run during its render callback.");
        }
        LuaValue poseValue = contextValue.get("PoseStack");
        if (!poseValue.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) {
            throw new LuaError("[MLua] RenderContext:WithPose is unavailable because this callback has no PoseStack.");
        }
        Object nativePose = ((MLuaRenderFrameScope.MLuaScopedRenderObject)poseValue.touserdata(
            MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        if (!(nativePose instanceof PoseStack poseStack)) {
            throw new LuaError("[MLua] RenderContext:WithPose is unavailable because this callback has no PoseStack.");
        }
        MLuaRenderPoseScope poseBoundary = MLuaRenderPoseScope.open(poseStack);
        try {
            return callback.invoke();
        } finally {
            poseBoundary.close();
        }
    }

    private static Object unwrapFrameValue(LuaValue reference, String name, String target) {
        if (reference == null || !reference.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) {
            throw new LuaError("[MLua] RenderContext:DrawObject is unavailable for " + target
                + " callbacks because Minecraft did not provide a " + name + ".");
        }
        try {
            return ((MLuaRenderFrameScope.MLuaScopedRenderObject)reference.touserdata(
                MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        } catch (IllegalStateException expired) {
            throw new LuaError("[MLua] RenderContext:DrawObject cannot use an expired " + name + ".");
        }
    }

    private record RenderDrawRequest(MLuaRenderFrameScope scope, LuaValue poseStack, LuaValue bufferSource,
                                     WeakReference<Object> runtimeOwner, String ownerModId, String target,
                                     int light, int overlay, float partialTick) {
        private MLuaRenderOwnershipRegistry.Owner owner() {
            return new MLuaRenderOwnershipRegistry.Owner(runtimeOwner.get(), ownerModId);
        }
    }

    private static LuaValue scopedUserdata(MLuaRenderFrameScope scope, Object value) {
        Object reference = scope.reference(value);
        return reference == null ? LuaValue.NIL : LuaValue.userdataOf(reference);
    }

    private static LuaTable drawObjectTypes(Object renderSource, boolean hasPoseStack) {
        LuaTable types = new LuaTable();
        if (!hasPoseStack) return types;
        if (isMultiBufferSource(renderSource)) {
            types.set(1, LuaValue.valueOf("Mesh"));
            types.set(2, LuaValue.valueOf("BBModel"));
        } else if (isRenderCollector(renderSource)
                && MLuaClientRenderManager.supportsDrawObjectSource(renderSource)) {
            types.set(1, LuaValue.valueOf("Mesh"));
            types.set(2, LuaValue.valueOf("BBModel"));
        }
        return types;
    }

    private static Class<?> findOptionalClass(String className) {
        try {
            return Class.forName(className, false, MLuaRenderFrameContextFactory.class.getClassLoader());
        } catch (LinkageError | ClassNotFoundException unavailable) {
            return null;
        }
    }

    private static boolean isRenderCollector(Object value) {
        return value != null && RENDER_COLLECTOR_CLASS != null && RENDER_COLLECTOR_CLASS.isInstance(value);
    }

    private static boolean isMultiBufferSource(Object value) {
        return value != null && MULTI_BUFFER_SOURCE_CLASS != null && MULTI_BUFFER_SOURCE_CLASS.isInstance(value);
    }
}
