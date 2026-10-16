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
    private static final int MAX_EVENT_CALLBACKS_PER_TICK = 1024;
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
    private float defaultFadeSpeed;
    private boolean timedFade;

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
        this.defaultFadeSpeed = 2.0f;
        this.timedFade = false;
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
    public float getTargetWeight() { return targetWeight; }
    public float getLength() { return (float) animation.getLength(); }
    public boolean isPlaying() { return playing; }
    public boolean isPaused() { return paused; }
    public boolean isLooped() { return looped; }
    public boolean isAlive() { return playing || weight > 0.001f; }
    public boolean isFading() { return Math.abs(weight - targetWeight) > 0.001f; }
    public Map<String, MLuaCFrame> getComputedPose() { return Map.copyOf(computedPose); }
    Map<String, MLuaCFrame> sampledPoseView() { return sampledPose; }
    public MLuaCFrame getBonePose(String bone) { return computedPose.get(bone); }

    public MLuaAnimationTrack setLooped(boolean looped) { this.looped = looped; return this; }
    public MLuaAnimationTrack setSpeed(float speed) {
        this.speed = finiteNonNegative(speed, "Animation speed");
        return this;
    }
    public MLuaAnimationTrack setFadeSpeed(float speed) {
        this.defaultFadeSpeed = finiteNonNegative(speed, "Animation fade speed");
        if (!timedFade) this.fadeSpeed = defaultFadeSpeed;
        return this;
    }

    public void play() {
        playing = true;
        paused = false;
        time = 0.0f;
        emitStartEvents = true;
        fadeTo(1.0f);
    }

    public void play(float fadeTime) {
        fadeTo(0.0f, 0.0f);
        playing = true;
        paused = false;
        time = 0.0f;
        emitStartEvents = true;
        fadeTo(1.0f, fadeTime);
    }

    public void stop() {
        fadeTo(0.0f);
    }

    public void stop(float fadeTime) {
        fadeTo(0.0f, fadeTime);
    }

    public void stopImmediate() {
        playing = false;
        weight = 0.0f;
        targetWeight = 0.0f;
        timedFade = false;
        fadeSpeed = defaultFadeSpeed;
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
        timedFade = false;
        fadeSpeed = defaultFadeSpeed;
        targetWeight = Math.max(0.0f, Math.min(1.0f, target));
        if (targetWeight <= 0.001f) {
            playing = false;
        }
    }

    public void fadeTo(float target, float fadeTime) {
        if (!Float.isFinite(target)) throw new IllegalArgumentException("Animation weight must be finite");
        if (!Float.isFinite(fadeTime) || fadeTime < 0.0f) {
            throw new IllegalArgumentException("Animation fade time must be finite and non-negative");
        }
        float clampedTarget = Math.max(0.0f, Math.min(1.0f, target));
        if (fadeTime == 0.0f) {
            fadeTo(clampedTarget);
            weight = clampedTarget;
            return;
        }
        float distance = Math.abs(clampedTarget - weight);
        if (distance > 0.0f) {
            float speedForDuration = distance / fadeTime;
            if (!Float.isFinite(speedForDuration)) {
                fadeTo(clampedTarget, 0.0f);
                return;
            }
            fadeSpeed = speedForDuration;
            timedFade = true;
        } else {
            fadeSpeed = defaultFadeSpeed;
            timedFade = false;
        }
        targetWeight = clampedTarget;
        if (targetWeight <= 0.001f) playing = false;
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
        double eventEndTime;

        if (looped && nextTime >= animation.getLength()) {
            time = (float) (nextTime % animation.getLength());
            eventEndTime = nextTime;
        } else {
            time = (float) Math.min(nextTime, animation.getLength());
            eventEndTime = time;
        }

        if (!looped && time >= animation.getLength()) {
            time = (float) animation.getLength();
            playing = false;
        }

        updateComputedPose();

        checkEvents(previousTime, eventEndTime, emitStartEvents);
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
            if (timedFade) {
                timedFade = false;
                fadeSpeed = defaultFadeSpeed;
            }
            return;
        }
        float diff = targetWeight - weight;
        float maxDelta = fadeSpeed * delta;
        if (Math.abs(diff) <= maxDelta) {
            weight = targetWeight;
            if (timedFade) {
                timedFade = false;
                fadeSpeed = defaultFadeSpeed;
            }
        } else {
            weight += Math.signum(diff) * maxDelta;
        }
        weight = Math.max(0.0f, Math.min(1.0f, weight));
    }

    private void checkEvents(double fromTime, double toTime, boolean includeStart) {
        List<AnimationEvent> snapshot;
        synchronized (events) {
            snapshot = List.copyOf(events);
        }
        if (snapshot.isEmpty()) return;

        double length = animation.getLength();
        if (!looped || length <= 0.0) {
            int dispatched = 0;
            for (AnimationEvent event : snapshot) {
                boolean startsNow = includeStart && fromTime == 0.0 && event.triggerTime == 0.0f;
                if (!startsNow && !(event.triggerTime > fromTime && event.triggerTime <= toTime)) continue;
                if (dispatched >= MAX_EVENT_CALLBACKS_PER_TICK) {
                    MLuaLogger.LOGGER.warn("[MLua] Animation event callback limit reached for track {}", trackId);
                    return;
                }
                if (fireEvent(event)) dispatched++;
            }
            return;
        }

        long firstCycle = (long) Math.floor(fromTime / length);
        long lastCycle = (long) Math.floor(toTime / length);
        int dispatched = 0;
        for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
            double cycleOffset = cycle * length;
            for (AnimationEvent event : snapshot) {
                double eventTime = cycleOffset + event.triggerTime;
                boolean startsNow = includeStart && fromTime == 0.0 && cycle == 0 && event.triggerTime == 0.0f;
                if (!startsNow && !(eventTime > fromTime && eventTime <= toTime)) continue;
                if (dispatched >= MAX_EVENT_CALLBACKS_PER_TICK) {
                    MLuaLogger.LOGGER.warn("[MLua] Animation event callback limit reached for track {}", trackId);
                    return;
                }
                if (fireEvent(event)) dispatched++;
            }
            if (cycle == Long.MAX_VALUE) break;
        }
    }

    private boolean fireEvent(AnimationEvent event) {
        synchronized (events) {
            if (!events.contains(event)) return false;
        }
        if (event.callback == null || event.callback.isnil()) return false;
        try {
            event.callback.invoke(LuaValue.valueOf(trackId), LuaValue.valueOf(event.triggerTime));
        } catch (Exception error) {
            MLuaLogger.LOGGER.warn("[MLua] Animation event callback failed at t={}: {}", event.triggerTime, error.getMessage());
        }
        return true;
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
