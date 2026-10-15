package dev.mlua.runtime.client;

import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.luaj.vm2.LuaValue;

/** Owns script hand-pose layers and composes them before first-person item rendering. */
public final class MLuaHandPoseRegistry {
    public record Owner(Object runtimeSession, String modId) {
        public Owner {
            modId = modId == null ? "" : modId;
        }
    }

    @FunctionalInterface
    public interface Sampler {
        MLuaCFrame sample(AbstractClientPlayer player, InteractionHand hand, ItemStack stack, HumanoidArm arm);
    }

    private static final Owner LEGACY_OWNER = new Owner(null, "");
    private static final class Pose {
        private volatile MLuaCFrame previous;
        private volatile MLuaCFrame current;
        private volatile LuaValue callback = LuaValue.NIL;
        private volatile Sampler sampler;
        private volatile Object executionLock = MLuaHandPoseRegistry.class;
    }

    private final Map<Owner, EnumMap<HumanoidArm, Pose>> poses = new LinkedHashMap<>();

    public synchronized void set(Owner owner, HumanoidArm arm, MLuaCFrame offset) {
        Owner resolved = resolveOwner(owner);
        if (offset == null) {
            remove(resolved, arm);
            return;
        }
        Pose pose = pose(resolved, arm);
        pose.callback = LuaValue.NIL;
        pose.sampler = null;
        pose.previous = offset;
        pose.current = offset;
    }

    public synchronized void setCallback(Owner owner, HumanoidArm arm, LuaValue callback,
                                         Sampler sampler, Object executionLock) {
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Hand pose callback must be a function");
        }
        if (sampler == null) throw new IllegalArgumentException("Hand pose sampler is required");
        Pose pose = pose(resolveOwner(owner), arm);
        pose.executionLock = executionLock == null ? MLuaHandPoseRegistry.class : executionLock;
        pose.callback = callback;
        pose.sampler = sampler;
        if (pose.current == null) pose.current = MLuaCFrame.IDENTITY;
        pose.previous = pose.current;
    }

    public synchronized LuaValue getValue(Owner owner, HumanoidArm arm) {
        EnumMap<HumanoidArm, Pose> byArm = poses.get(resolveOwner(owner));
        Pose pose = byArm == null ? null : byArm.get(arm);
        if (pose == null) return LuaValue.NIL;
        return pose.callback.isfunction() ? pose.callback
            : pose.current == null ? LuaValue.NIL : pose.current.toLua();
    }

    /** Samples Lua callbacks on the client tick; render hooks only read immutable CFrames. */
    public void sample(AbstractClientPlayer player, HumanoidArm arm) {
        List<Map.Entry<Owner, Pose>> snapshot = new ArrayList<>();
        synchronized (this) {
            poses.forEach((owner, byArm) -> {
                Pose pose = byArm.get(arm);
                if (pose != null && pose.callback.isfunction()) snapshot.add(Map.entry(owner, pose));
            });
        }
        for (Map.Entry<Owner, Pose> entry : snapshot) {
            Owner owner = entry.getKey();
            Pose pose = entry.getValue();
            try {
                HumanoidArm callbackArm = arm;
                InteractionHand hand = player == null || callbackArm == player.getMainArm()
                    ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                ItemStack stack = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
                MLuaCFrame sampled;
                synchronized (pose.executionLock) {
                    sampled = pose.sampler.sample(player, hand, stack, callbackArm);
                }
                if (sampled == null) throw new IllegalArgumentException("callback must return a CFrame");
                pose.previous = pose.current;
                pose.current = sampled;
            } catch (RuntimeException failure) {
                synchronized (this) {
                    pose.callback = LuaValue.NIL;
                    pose.sampler = null;
                }
                System.getLogger("MLuaCamera").log(System.Logger.Level.ERROR,
                    "[MLua] " + owner.modId() + " " + arm.name().toLowerCase(java.util.Locale.ROOT)
                        + " hand pose callback failed and was disabled; keeping its last valid pose: "
                        + failure.getMessage());
            }
        }
    }

    public synchronized MLuaCFrame composed(HumanoidArm arm, float partialTick) {
        float alpha = Float.isFinite(partialTick) ? Math.max(0.0f, Math.min(1.0f, partialTick)) : 1.0f;
        MLuaCFrame result = MLuaCFrame.IDENTITY;
        boolean found = false;
        for (EnumMap<HumanoidArm, Pose> byArm : poses.values()) {
            Pose pose = byArm.get(arm);
            if (pose == null || pose.current == null) continue;
            MLuaCFrame current = pose.current;
            MLuaCFrame previous = pose.previous == null ? current : pose.previous;
            result = result.mul(previous.lerp(current, alpha));
            found = true;
        }
        return found ? result : null;
    }

    public synchronized void clearOwned(Owner owner) {
        poses.remove(resolveOwner(owner));
    }

    public synchronized void clearRuntime(Object runtimeSession) {
        if (runtimeSession == null) return;
        poses.keySet().removeIf(owner -> owner.runtimeSession() == runtimeSession);
    }

    public synchronized void clearRuntime(Object runtimeSession, String modId) {
        if (runtimeSession == null) return;
        clearOwned(new Owner(runtimeSession, modId));
    }

    public synchronized void clear() {
        poses.clear();
    }

    private Pose pose(Owner owner, HumanoidArm arm) {
        if (arm == null) throw new IllegalArgumentException("Hand arm is required");
        return poses.computeIfAbsent(owner, ignored -> new EnumMap<>(HumanoidArm.class))
            .computeIfAbsent(arm, ignored -> new Pose());
    }

    private void remove(Owner owner, HumanoidArm arm) {
        EnumMap<HumanoidArm, Pose> byArm = poses.get(owner);
        if (byArm == null) return;
        byArm.remove(arm);
        if (byArm.isEmpty()) poses.remove(owner);
    }

    private static Owner resolveOwner(Owner owner) {
        return owner == null ? LEGACY_OWNER : owner;
    }
}
