//--version 1.20.1,1.21.1
package dev.mlua.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.runtime.client.MLuaClientRenderManager.RenderObject;
import dev.mlua.runtime.client.MLuaClientRenderManager.RenderTarget;
import dev.mlua.runtime.client.MLuaClientRenderManager.RenderType;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/** Owns rendering for entity attachments and first/third-person held-item targets. */
final class MLuaRenderTargetDispatcher {
    private MLuaRenderTargetDispatcher() { }

    static void renderEntityAttachments(Object model, MLuaEntityScope scope, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay, float partialTick) {
        if (scope == null || pose == null || buffers == null) return;
        List<RenderObject> attachments = MLuaClientRenderManager.orderedEntityModels(scope);
        if (attachments.isEmpty()) return;
        MLuaModelAdapterRegistry.recordResolvedModel(model, scope);
        for (RenderObject object : attachments) {
            int renderId = object.id;
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.visible || object.target() != RenderTarget.ENTITY_MODEL
                        || !scope.equals(object.followEntityScope)) continue;
                pose.pushPose();
                try {
                    if (object.followBoneName != null && (model == null
                            || !MLuaModelAdapterRegistry.applyPartTransform(model, object.followBoneName, pose, scope))) continue;
                    object.applyFollowOffset(pose, partialTick);
                    pose.translate(object.worldX, object.worldY, object.worldZ);
                    MLuaClientRenderManager.applyTransform(pose, object.modelTransform);
                    if (object.modelScale != 1.0F) pose.scale(object.modelScale, object.modelScale, object.modelScale);
                    if (object.type == RenderType.MESH && object.mesh != null) {
                        MLuaRenderHelper.renderMesh(pose, buffers, object.mesh,
                            MLuaClientRenderManager.resolveMeshTexture(object), light, overlay);
                    } else if (object.bbmodel != null) {
                        MLuaClientRenderManager.renderBBModelWorld(pose, buffers, object, light, overlay, partialTick);
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

    static void renderHeldItemModels(RenderTarget target, String itemId, Object rawPose,
            Object renderSource, int packedLight, float partialTick) {
        if ((target != RenderTarget.FIRST_PERSON_ITEM && target != RenderTarget.THIRD_PERSON_ITEM)
                || itemId == null || !(rawPose instanceof PoseStack pose)) return;
        boolean immediate = renderSource instanceof MultiBufferSource;
        boolean deferred = !immediate && dev.mlua.loaderapi.LoaderApi.client().supportsCustomGeometry(renderSource);
        if (!immediate && !deferred) return;
        for (RenderObject object : MLuaClientRenderManager.orderedHeldItemModels(target, itemId)) {
            synchronized (object.executionLock()) {
                if (object.destroyed || !object.initialized() || !object.visible || object.target() != target
                        || !itemId.equals(object.heldItemId)
                        || (object.type != RenderType.MESH && object.type != RenderType.BBMODEL)) continue;
                pose.pushPose();
                try {
                    if (immediate) {
                        object.applyFollowOffset(pose, partialTick);
                        MLuaClientRenderManager.applyTransform(pose, object.modelTransform);
                        if (object.modelScale != 1.0F) {
                            pose.scale(object.modelScale, object.modelScale, object.modelScale);
                        }
                        if (object.type == RenderType.MESH && object.mesh != null) {
                            MLuaRenderHelper.renderMesh(pose, (MultiBufferSource) renderSource, object.mesh,
                                MLuaClientRenderManager.resolveMeshTexture(object), packedLight, 0);
                        } else if (object.bbmodel != null) {
                            MLuaClientRenderManager.renderBBModelWorld(pose, (MultiBufferSource) renderSource, object,
                                packedLight, 0, partialTick);
                        }
                    } else {
                        MLuaCFrame offset = object.interpolatedFollowOffset(partialTick);
                        MLuaCFrame modelTransform = object.modelTransform;
                        float modelScale = object.modelScale;
                        java.util.function.Consumer<Matrix4f> transform = matrix -> {
                            MLuaClientRenderManager.applyTransform(matrix, offset);
                            MLuaClientRenderManager.applyTransform(matrix, modelTransform);
                            matrix.scale(modelScale);
                        };
                        boolean submitted;
                        if (object.type == RenderType.MESH && object.mesh != null) {
                            submitted = MLuaWorldRenderPipeline.submitMeshGeometry(renderSource, pose, object.mesh,
                                MLuaClientRenderManager.resolveMeshTexture(object), packedLight, 0, object, transform);
                        } else {
                            submitted = MLuaWorldRenderPipeline.submitBBModelGeometry(renderSource, pose, object,
                                packedLight, 0, partialTick, transform);
                        }
                        if (!submitted) throw new IllegalStateException(
                            "The loader rejected held-item model geometry submission");
                    }
                } catch (RuntimeException failure) {
                    if (object.markRenderFailureReported()) {
                        MLuaLogger.LOGGER.error("[MLua] Failed to draw held-item RenderObject {}", object.id, failure);
                    }
                } finally {
                    pose.popPose();
                }
            }
        }
    }

    static boolean hasHeldItemModels(RenderTarget target, String itemId) {
        if ((target != RenderTarget.FIRST_PERSON_ITEM && target != RenderTarget.THIRD_PERSON_ITEM)
                || itemId == null) return false;
        for (RenderObject object : MLuaClientRenderManager.orderedHeldItemModels(target, itemId)) {
            if (!object.destroyed() && object.visible() && object.target() == target
                    && itemId.equals(object.heldItemId())) return true;
        }
        return false;
    }
}
//--endversion
