package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.core.MLuaCallbackBudget;
import dev.mlua.core.MLuaContext;
import dev.mlua.core.MLuaLogger;
import dev.mlua.core.engine.MLuaLuaBindings;
import dev.mlua.runtime.client.MLuaRenderHookRegistry.Hook;
import java.util.function.Supplier;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Runs render hooks inside bounded Lua, pose-stack, and frame-context scopes. */
final class MLuaRenderCallbackInvoker {
    private MLuaRenderCallbackInvoker() { }

    /** Includes Lua wrappers and frame-context creation in the same failure boundary as callback execution. */
    static void invokeSafely(Hook hook, Supplier<Varargs> argumentFactory) {
        if (hook == null || !hook.active || hook.failed) return;
        if (!MLuaClientRenderThread.require("Lua render callback setup")) return;
        try {
            Varargs args = argumentFactory.get();
            if (args == null) throw new IllegalStateException("Render callback argument factory returned null");
            invoke(hook, args);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableHook(hook, failure);
        }
    }

    private static void invoke(Hook hook, Varargs invocationArgs) {
        MLuaRenderFrameContexts.Binding frameBinding = MLuaRenderFrameContexts.find(invocationArgs);
        if (!hook.active || hook.failed) {
            closeFrameScope(frameBinding);
            return;
        }
        if (!MLuaClientRenderThread.require("Lua render callback")) {
            closeFrameScope(frameBinding);
            return;
        }
        MLuaContext context = hook.context;
        MLuaRenderFrameScope.ScopeBinding activeFrame = null;
        MLuaRenderPoseScope callbackPoseBoundary = null;
        try {
            if (frameBinding != null) {
                activeFrame = frameBinding.scope().view().enter();
                callbackPoseBoundary = MLuaRenderPoseScope.open(framePoseStack(frameBinding));
            }
            MLuaLuaBindings.withContext(context, () -> {
                if (!hook.active || hook.failed) return null;
                return MLuaCallbackBudget.run(context, () -> {
                    hook.callback.invoke(invocationArgs);
                    return null;
                });
            });
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            disableHook(hook, failure);
        } finally {
            try {
                if (callbackPoseBoundary != null) callbackPoseBoundary.close();
            } finally {
                try {
                    if (activeFrame != null) activeFrame.close();
                } finally {
                    closeFrameScope(frameBinding);
                }
            }
        }
    }

    static LuaValue renderContext(Hook hook, String target, Object poseStack, Object bufferSource,
            Object model, float partialTick, boolean hasPartialTick, int packedLight, int packedOverlay,
            Object nativeContext, Object... additionalFields) {
        return MLuaRenderFrameContextFactory.create(hook.context, hook.id,
            failure -> disableHook(hook, failure), target, poseStack, bufferSource, model, partialTick,
            hasPartialTick, packedLight, packedOverlay, nativeContext, additionalFields);
    }

    static LuaValue userdata(Object value) {
        return value == null ? LuaValue.NIL : LuaValue.userdataOf(value);
    }

    private static PoseStack framePoseStack(MLuaRenderFrameContexts.Binding binding) {
        if (binding == null) return null;
        LuaValue value = binding.context().get("PoseStack");
        if (value == null || !value.isuserdata(MLuaRenderFrameScope.MLuaScopedRenderObject.class)) return null;
        Object nativePose = ((MLuaRenderFrameScope.MLuaScopedRenderObject) value.touserdata(
            MLuaRenderFrameScope.MLuaScopedRenderObject.class)).unwrap();
        return nativePose instanceof PoseStack pose ? pose : null;
    }

    private static void closeFrameScope(MLuaRenderFrameContexts.Binding binding) {
        if (binding == null) return;
        MLuaRenderFrameContexts.remove(binding);
        binding.scope().close();
    }

    private static void disableHook(Hook hook, Throwable failure) {
        hook.failed = true;
        MLuaRenderHookRegistry.removeFailed(hook);
        MLuaLogger.LOGGER.error("[MLua] Disabled render callback '{}' for addon '{}' at {} after an error",
            hook.id, hook.key.owner().modId(), hook.target, failure);
    }
}
