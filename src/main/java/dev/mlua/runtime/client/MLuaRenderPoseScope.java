package dev.mlua.runtime.client;

import dev.mlua.core.MLuaLogger;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Restores the pose stack boundary around one addon render callback. */
final class MLuaRenderPoseScope implements AutoCloseable {
    private record DepthAccess(Field field, boolean indexBased) {
        private static final DepthAccess UNAVAILABLE = new DepthAccess(null, false);
    }

    private static final ClassValue<DepthAccess> DEPTH_ACCESS = new ClassValue<>() {
        @Override protected DepthAccess computeValue(Class<?> type) {
            for (String name : List.of("lastIndex", "poses", "poseStack")) {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    try {
                        Field field = current.getDeclaredField(name);
                        boolean index = field.getType() == int.class;
                        if (!index && !Collection.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        return new DepthAccess(field, index);
                    } catch (ReflectiveOperationException | RuntimeException ignored) { }
                }
            }
            return DepthAccess.UNAVAILABLE;
        }
    };
    private static final Set<Class<?>> WARNED_STACK_TYPES = ConcurrentHashMap.newKeySet();

    private final PoseStack stack;
    private final int depthBefore;
    private boolean closed;

    private MLuaRenderPoseScope(PoseStack stack) {
        this.stack = stack;
        this.depthBefore = depth(stack);
        stack.pushPose();
    }

    static MLuaRenderPoseScope open(PoseStack stack) {
        return stack == null ? null : new MLuaRenderPoseScope(stack);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        try {
            if (depthBefore < 0) {
                stack.popPose();
                return;
            }
            int depth = depth(stack);
            if (depth < depthBefore) {
                warn(stack.getClass());
                return;
            }
            while (depth > depthBefore) {
                stack.popPose();
                int nextDepth = depth(stack);
                if (nextDepth < 0 || nextDepth >= depth) {
                    warn(stack.getClass());
                    return;
                }
                depth = nextDepth;
            }
        } catch (RuntimeException failure) {
            warn(stack.getClass());
        }
    }

    private static int depth(PoseStack stack) {
        DepthAccess access = DEPTH_ACCESS.get(stack.getClass());
        if (access.field() == null) return -1;
        try {
            if (access.indexBased()) return access.field().getInt(stack) + 1;
            Object poses = access.field().get(stack);
            return poses instanceof Collection<?> collection ? collection.size() : -1;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return -1;
        }
    }

    private static void warn(Class<?> stackClass) {
        if (WARNED_STACK_TYPES.add(stackClass)) {
            MLuaLogger.LOGGER.warn("[MLua] Could not fully restore PoseStack depth after a render callback on {}; "
                + "avoid unmatched pushPose()/popPose() calls", stackClass.getName());
        }
    }
}
