package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import net.minecraft.client.model.Model;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

/** Selects the addon-adapted model instance owned by a renderer. */
final class MLuaRendererModelResolver {
    private static final int MAX_WARNING_KEYS = 4096;
    private static final Set<String> WARNED_PATHS = Collections.synchronizedSet(new LinkedHashSet<>());

    private MLuaRendererModelResolver() { }

    static Object findModel(Object renderer, String configuredPath, Predicate<Object> adaptedModel) {
        return findModel(renderer, configuredPath, adaptedModel, "adapted model");
    }

    /** Finds the renderer's primary native model before an addon has registered an adapter for it. */
    static Object findModelCandidate(Object renderer, String configuredPath) {
        return findModel(renderer, configuredPath, Model.class::isInstance, "Minecraft model");
    }

    private static Object findModel(Object renderer, String configuredPath, Predicate<Object> selectedModel,
                                    String selectedKind) {
        if (renderer == null) return null;
        if (configuredPath != null) {
            Object candidate = MLuaModelFieldAccess.resolveFieldPath(renderer, configuredPath);
            if (candidate == null) {
                reportMissingPath(renderer.getClass(), configuredPath, "the configured field path did not resolve");
                return null;
            }
            if (!selectedModel.test(candidate)) {
                reportMissingPath(renderer.getClass(), configuredPath,
                    "the field does not contain a " + selectedKind);
                return null;
            }
            return candidate;
        }

        Object selected = null;
        String selectedField = null;
        ArrayList<String> ambiguousFields = null;
        for (Field field : MLuaModelFieldAccess.rendererFields(renderer.getClass())) {
            try {
                Object candidate = field.get(renderer);
                if (candidate == null || !selectedModel.test(candidate)) continue;
                if (selected == null) {
                    selected = candidate;
                    selectedField = field.getName();
                } else if (candidate != selected) {
                    if (ambiguousFields == null) {
                        ambiguousFields = new ArrayList<>();
                        ambiguousFields.add(selectedField);
                    }
                    ambiguousFields.add(field.getName());
                }
            } catch (IllegalAccessException | RuntimeException inaccessible) {
                // Other renderer fields may still expose the adapted model.
            }
        }
        if (ambiguousFields != null) {
            String key = renderer.getClass().getName() + "\u0000ambiguous-adapted-model-fields";
            if (markWarning(key)) {
                MLuaLogger.LOGGER.warn("[MLua] Renderer {} contains multiple adapted models in fields {}; "
                        + "register ModelService:RegisterRendererAdapter with the intended field path",
                    renderer.getClass().getName(), String.join(", ", ambiguousFields));
            }
            return null;
        }
        return selected;
    }

    private static void reportMissingPath(Class<?> rendererClass, String path, String reason) {
        String key = rendererClass.getName() + '\u0000' + path;
        if (markWarning(key)) {
            MLuaLogger.LOGGER.warn("[MLua] Renderer adapter path '{}' did not resolve an adapted model on {}: {}",
                path, rendererClass.getName(), reason);
        }
    }

    private static boolean markWarning(String key) {
        synchronized (WARNED_PATHS) {
            if (!WARNED_PATHS.add(key)) return false;
            if (WARNED_PATHS.size() > MAX_WARNING_KEYS) {
                var oldest = WARNED_PATHS.iterator();
                oldest.next();
                oldest.remove();
            }
            return true;
        }
    }
}
