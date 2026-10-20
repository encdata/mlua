package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaBonePose;
import dev.mlua.minecraft.animation.MLuaEntityScope;
import dev.mlua.core.MLuaLogger;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;

/** Owns temporary model and bone state while Minecraft traverses nested entity model renders. */
final class MLuaEntityModelRenderState {
    private static final ThreadLocal<Deque<Frame>> FRAMES = new ThreadLocal<>();

    private static final class Frame {
        private final Entity entity;
        private final Object model;
        private final MLuaEntityScope scope;
        private final Map<String, MLuaCFrame> animationPose;
        private final IdentityHashMap<ModelPart, String> bones;
        private final IdentityHashMap<ModelPart, Integer> activePartDepth = new IdentityHashMap<>();
        private final Set<ModelPart> changedParts = Collections.newSetFromMap(new IdentityHashMap<>());

        private Frame(Entity entity, Object model, MLuaEntityScope scope,
                      Map<String, MLuaCFrame> animationPose, IdentityHashMap<ModelPart, String> bones) {
            this.entity = entity;
            this.model = model;
            this.scope = scope;
            this.animationPose = animationPose;
            this.bones = bones;
        }
    }

    private MLuaEntityModelRenderState() { }

    static boolean hasActiveFrame() {
        Deque<Frame> frames = FRAMES.get();
        return frames != null && !frames.isEmpty();
    }

    static void push(Entity entity, Object model, MLuaEntityScope scope,
                     Map<String, MLuaCFrame> animationPose,
                     IdentityHashMap<ModelPart, String> bones) {
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        frames.push(new Frame(entity, model, scope, animationPose, bones));
    }

    static void pushMask(Entity entity) {
        push(entity, null, MLuaEntityScope.of(entity), Map.of(), new IdentityHashMap<>());
    }

    static void endEntityRender(Entity entity) {
        if (entity == null) return;
        Deque<Frame> frames = FRAMES.get();
        if (frames == null || frames.isEmpty()) return;
        Frame frame = frames.pop();
        if (frame.entity != entity) {
            MLuaLogger.LOGGER.warn("[MLua] Discarded an unbalanced custom entity model render scope");
            restore(frame);
            while (!frames.isEmpty()) restore(frames.pop());
            FRAMES.remove();
            return;
        }
        restore(frame);
        if (frames.isEmpty()) FRAMES.remove();
    }

    static void abortEntityRender(Entity entity) {
        if (entity == null) return;
        Deque<Frame> frames = FRAMES.get();
        if (frames == null) return;
        Frame frame = frames.peek();
        if (frame == null) {
            FRAMES.remove();
            return;
        }
        if (frame.entity != entity) return;
        frames.pop();
        restore(frame);
        if (frames.isEmpty()) FRAMES.remove();
    }

    static void beginModelPartRender(ModelPart part) {
        if (part == null) return;
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames == null ? null : frames.peek();
        if (frame == null || frame.model == null) return;
        int depth = frame.activePartDepth.getOrDefault(part, 0);
        frame.activePartDepth.put(part, depth + 1);
        if (depth > 0) return;
        String bone = frame.bones.get(part);
        if (bone == null) return;
        MLuaCFrame pose = frame.animationPose.get(bone);
        MLuaClientModelManager.PartState state = MLuaClientModelManager.getPartState(frame.scope, bone);
        if (pose == null && state == null) return;
        MLuaClientModelManager.rememberOverridePose(frame.model, part);
        if (pose != null) MLuaModelAdapterRegistry.applyPose(part, pose);
        MLuaClientModelManager.applyState(frame.model, part, state);
        frame.changedParts.add(part);
    }

    static void endModelPartRender(ModelPart part) {
        if (part == null) return;
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames == null ? null : frames.peek();
        if (frame == null) return;
        Integer depth = frame.activePartDepth.get(part);
        if (depth == null) return;
        if (depth > 1) {
            frame.activePartDepth.put(part, depth - 1);
            return;
        }
        frame.activePartDepth.remove(part);
        if (frame.changedParts.remove(part)) {
            MLuaClientModelManager.restoreOverridePose(frame.model, part);
        }
    }

    static Map<String, MLuaCFrame> renderPose(MLuaEntityScope scope) {
        Deque<Frame> frames = FRAMES.get();
        Frame frame = frames == null ? null : frames.peek();
        if (frame != null && frame.scope.equals(scope)) return frame.animationPose;
        if (frames != null && frames.isEmpty()) FRAMES.remove();
        return MLuaBonePose.getRenderPose(scope);
    }

    private static void restore(Frame frame) {
        if (frame.model != null) {
            for (ModelPart part : List.copyOf(frame.changedParts)) {
                MLuaClientModelManager.restoreOverridePose(frame.model, part);
            }
        }
        frame.activePartDepth.clear();
        frame.changedParts.clear();
    }
}
