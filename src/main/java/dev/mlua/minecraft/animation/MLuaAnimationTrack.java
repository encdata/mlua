package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.luaj.vm2.LuaValue;

public final class MLuaAnimationTrack {
    private final int trackId;
    private final int entityId;
    private final MLuaAnimation animation;
    private final AnimationPriority priority;

    private boolean playing;
    private boolean paused;
    private boolean looped;
    private boolean emitStartEvents;
    private float speed;
    private float time;
    private float weight;
    private float targetWeight;
    private float fadeSpeed;

    private final List<AnimationEvent> events;
    private Map<String, MLuaCFrame> computedPose;
    private Map<String, MLuaCFrame> sampledPose;

    private static final AtomicInteger NEXT_TRACK_ID = new AtomicInteger(1);

    public MLuaAnimationTrack(int entityId, MLuaAnimation animation, AnimationPriority priority) {
        this.trackId = NEXT_TRACK_ID.getAndIncrement();
        this.entityId = entityId;
        this.animation = Objects.requireNonNull(animation, "animation");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.speed = 1.0f;
        this.weight = 1.0f;
        this.targetWeight = 1.0f;
        this.fadeSpeed = 2.0f;
        this.playing = false;
        this.paused = false;
        this.looped = false;
        this.emitStartEvents = false;
        this.time = 0.0f;
        this.computedPose = new HashMap<>();
        this.sampledPose = new HashMap<>();
        this.events = new ArrayList<>();
    }

    public int trackId() { return trackId; }
    public int entityId() { return entityId; }
    public MLuaAnimation animation() { return animation; }
    public AnimationPriority getPriority() { return priority; }
    public float getSpeed() { return speed; }
    public float getTime() { return time; }
    public float getWeight() { return weight; }
    public float getLength() { return (float) animation.getLength(); }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isLooped() { return looped; }
    public boolean isAlive() { return playing || weight > 0.001f; }
    public Map<String, MLuaCFrame> getComputedPose() { return Map.copyOf(computedPose); }
    Map<String, MLuaCFrame> sampledPoseView() { return sampledPose; }
    public MLuaCFrame getBonePose(String bone) { return computedPose.get(bone); }

    public MLuaAnimationTrack setLooped(boolean looped) { this.looped = looped; return this; }
    public MLuaAnimationTrack setSpeed(float speed) {
        this.speed = finiteNonNegative(speed, "Animation speed");
        return this;
    }
    public MLuaAnimationTrack setFadeSpeed(float speed) {
        this.fadeSpeed = Math.max(0.1f, finiteNonNegative(speed, "Animation fade speed"));
        return this;
    }

    public void play() {
        playing = true;
        paused = false;
        time = 0.0f;
        emitStartEvents = true;
        fadeTo(1.0f);
    }

    public void stop() {
        fadeTo(0.0f);
    }

    public void stopImmediate() {
        playing = false;
        weight = 0.0f;
        targetWeight = 0.0f;
        computedPose.clear();
        sampledPose.clear();
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        if (paused) {
            paused = false;
            playing = true;
        }
    }

    public void resumePlaying() {
        playing = true;
        paused = false;
        fadeTo(1.0f);
    }

    public void fadeTo(float target) {
        if (!Float.isFinite(target)) throw new IllegalArgumentException("Animation weight must be finite");
        targetWeight = Math.max(0.0f, Math.min(1.0f, target));
        if (targetWeight <= 0.001f) {
            playing = false;
        }
    }

    public void adjustSpeed(float newSpeed) {
        setSpeed(newSpeed);
    }

    public void setTimePosition(float t) {
        if (!Float.isFinite(t)) throw new IllegalArgumentException("Animation time position must be finite");
        time = Math.max(0.0f, Math.min(t, (float) animation.getLength()));
    }

    public void addEvent(float triggerTime, LuaValue callback) {
        if (!Float.isFinite(triggerTime) || triggerTime < 0.0f || triggerTime > animation.getLength()) {
            throw new IllegalArgumentException("Animation event time must be finite and within the animation length");
        }
        if (callback == null || !callback.isfunction()) {
            throw new IllegalArgumentException("Animation event callback must be a function");
        }
        synchronized (events) {
            events.add(new AnimationEvent(triggerTime, callback));
            events.sort((a, b) -> Float.compare(a.triggerTime, b.triggerTime));
        }
    }

    public void clearEvents() {
        synchronized (events) {
            events.clear();
        }
    }

    public void tick(float delta) {
        if (!Float.isFinite(delta) || delta < 0.0f) return;
        if (animation.getLength() <= 0.0) {
            playing = false;
            weight = 0.0f;
            targetWeight = 0.0f;
            computedPose.clear();
            sampledPose.clear();
            return;
        }
        if (!playing || paused) {
            handleFade(delta);
            updateComputedPose();
            return;
        }

        float previousTime = time;

        handleFade(delta);

        if (weight <= 0.001f) {
            computedPose.clear();
            sampledPose.clear();
            return;
        }

        double nextTime = time + (double) delta * speed;

        if (looped && nextTime >= animation.getLength()) {
            time = (float) (nextTime % animation.getLength());
        } else {
            time = (float) Math.min(nextTime, animation.getLength());
        }

        if (!looped && time >= animation.getLength()) {
            time = (float) animation.getLength();
            playing = false;
        }

        updateComputedPose();

        checkEvents(previousTime, time, emitStartEvents);
        emitStartEvents = false;
    }

    private void updateComputedPose() {
        if (weight <= 0.001f || animation.getLength() <= 0) {
            computedPose.clear();
            sampledPose.clear();
            return;
        }

        sampledPose = animation.sample(time);
        computedPose.clear();
        if (weight >= 0.999f) {
            computedPose.putAll(sampledPose);
        } else {
            for (Map.Entry<String, MLuaCFrame> entry : sampledPose.entrySet()) {
                computedPose.put(entry.getKey(), MLuaCFrame.IDENTITY.lerp(entry.getValue(), weight));
            }
        }
    }

    private void handleFade(float delta) {
        if (Math.abs(weight - targetWeight) < 0.001f) {
            weight = targetWeight;
            return;
        }
        float diff = targetWeight - weight;
        float maxDelta = fadeSpeed * delta;
        if (Math.abs(diff) <= maxDelta) {
            weight = targetWeight;
        } else {
            weight += Math.signum(diff) * maxDelta;
        }
        weight = Math.max(0.0f, Math.min(1.0f, weight));
    }

    private void checkEvents(float fromTime, float toTime, boolean includeStart) {
        List<AnimationEvent> snapshot;
        synchronized (events) {
            snapshot = List.copyOf(events);
        }
        for (AnimationEvent event : snapshot) {
            synchronized (events) {
                if (!events.contains(event)) continue;
            }
            if ((includeStart && event.triggerTime == 0.0f)
                || event.triggerTime > fromTime && event.triggerTime <= toTime ||
                (looped && toTime < fromTime && (event.triggerTime > fromTime || event.triggerTime <= toTime))) {
                if (event.callback != null && !event.callback.isnil()) {
                try {
                    event.callback.invoke(LuaValue.valueOf(trackId), LuaValue.valueOf(event.triggerTime));
                } catch (Exception e) {
                    MLuaLogger.LOGGER.warn("[MLua] Animation event callback failed at t={}: {}", event.triggerTime, e.getMessage());
                }
                }
            }
        }
    }

    private static float finiteNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    public static final class AnimationEvent {
        public final float triggerTime;
        public final LuaValue callback;

        public AnimationEvent(float triggerTime, LuaValue callback) {
            this.triggerTime = triggerTime;
            this.callback = callback;
        }
    }
}
