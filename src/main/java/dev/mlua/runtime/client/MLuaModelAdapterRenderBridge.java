package dev.mlua.runtime.client;

import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.core.MLuaLogger;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Connects loader render hooks to entity-scoped model adapter state. */
public final class MLuaModelAdapterRenderBridge {
    private static final int MAX_RENDERER_WARNING_KEYS = 4096;
    private static final Set<String> REPORTED_RENDER_FAILURES =
        Collections.synchronizedSet(new LinkedHashSet<>());

    private MLuaModelAdapterRenderBridge() { }

    public static void beginEntityRender(Entity entity, Object renderer) {
        if (entity == null) return;
        if (entity instanceof LivingEntity) {
            // A nested living renderer must hide any non-living adapter frame beneath it.
            if (MLuaEntityModelRenderState.hasActiveFrame()) MLuaEntityModelRenderState.pushMask(entity);
            return;
        }
        if (!MLuaClientRenderThread.require("non-living model adapter")) return;
        Object model = null;
        IdentityHashMap<ModelPart, String> bones = new IdentityHashMap<>();
        MLuaEntityScope scope = MLuaEntityScope.of(entity);
        MLuaAppliedModelRegistry.remove(scope);
        try {
            model = MLuaModelAdapterRegistry.findAdaptedModel(renderer);
            Map<String, String> mappings = model == null ? null
                : MLuaModelAdapterRegistry.findMappingForModel(model);
            if (model != null && MLuaModelAdapterLookup.isAdaptedModel(model)) {
                Object adaptedModel = model;
                Map<String, String> safeMappings = mappings == null ? Map.of() : mappings;
                MLuaModelAdapterRegistry.resolvedParts(model, safeMappings)
                    .forEach((bone, part) -> bones.put(part, bone));
                java.util.LinkedHashSet<String> customBones = new java.util.LinkedHashSet<>();
                MLuaModelAdapterLookup.customAdaptersForModel(adaptedModel.getClass()).adapters()
                    .forEach((adapter, names) -> {
                        if (MLuaCustomModelAdapterInvoker.apply(adapter, adaptedModel, scope, names)) {
                            customBones.addAll(names);
                        }
                    });
                java.util.LinkedHashSet<String> resolved = new java.util.LinkedHashSet<>(bones.values());
                resolved.addAll(customBones);
                MLuaAppliedModelRegistry.record(model.getClass(), safeMappings,
                    Collections.unmodifiableSet(resolved), Collections.unmodifiableSet(customBones), scope);
            } else {
                model = null;
            }
        } catch (Error fatal) {
            throw fatal;
        } catch (Throwable failure) {
            String key = renderer == null ? entity.getType().toString() : renderer.getClass().getName();
            if (recordRenderFailureOnce(key)) {
                MLuaLogger.LOGGER.error(
                    "[MLua] Could not prepare adapted model rendering for {} (first affected entity: {})",
                    key, entity.getUUID(), failure);
            }
            model = null;
            bones.clear();
        }
        if (model != null) {
            MLuaEntityModelRenderState.push(entity, model, scope, MLuaBonePose.getRenderPose(scope), bones);
        } else if (MLuaEntityModelRenderState.hasActiveFrame()) {
            // Keep nesting balanced when this renderer has no adapter of its own.
            MLuaEntityModelRenderState.pushMask(entity);
        }
    }

    public static void endEntityRender(Entity entity) {
        MLuaEntityModelRenderState.endEntityRender(entity);
    }

    public static void abortEntityRender(Entity entity) {
        MLuaEntityModelRenderState.abortEntityRender(entity);
    }

    public static void abortEntityRender(Entity entity, Object renderer) {
        if (entity == null) return;
        if (entity instanceof LivingEntity) {
            Object model = MLuaModelAdapterRegistry.findAdaptedModel(renderer);
            if (model != null) MLuaClientModelManager.restoreOverrides(model);
        }
        abortEntityRender(entity);
    }

    public static void beginModelPartRender(ModelPart part) {
        MLuaEntityModelRenderState.beginModelPartRender(part);
    }

    public static void endModelPartRender(ModelPart part) {
        MLuaEntityModelRenderState.endModelPartRender(part);
    }

    static void clearFailureCache() {
        REPORTED_RENDER_FAILURES.clear();
    }

    private static boolean recordRenderFailureOnce(String key) {
        synchronized (REPORTED_RENDER_FAILURES) {
            if (!REPORTED_RENDER_FAILURES.add(key)) return false;
            if (REPORTED_RENDER_FAILURES.size() > MAX_RENDERER_WARNING_KEYS) {
                var oldest = REPORTED_RENDER_FAILURES.iterator();
                if (oldest.hasNext()) {
                    oldest.next();
                    oldest.remove();
                }
            }
            return true;
        }
    }
}
