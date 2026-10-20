package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;

public final class MLuaAnimationTrack {
    /** Event callbacks are retained by their track until cleared or the track is released. */
    public static final int MAX_EVENTS_PER_TRACK = MLuaAnimationEventState.MAX_EVENTS_PER_TRACK;
    /** Bounds retained Lua closures across every live animation track. */
    public static final int MAX_ACTIVE_EVENT_CALLBACKS = MLuaAnimationEventState.MAX_ACTIVE_EVENT_CALLBACKS;
    /** Prevents one addon runtime from reserving marker capacity needed by other addons. */
    public static final int MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER = MLuaAnimationEventState.MAX_ACTIVE_EVENT_CALLBACKS_PER_OWNER;
    public static final int MAX_EVENT_CALLBACKS_PER_ENTITY_TICK = MLuaAnimationEventState.MAX_EVENT_CALLBACKS_PER_ENTITY_TICK;
    public static final int MAX_EVENT_CALLBACKS_PER_ADDON_TICK = MLuaAnimationEventState.MAX_EVENT_CALLBACKS_PER_ADDON_TICK;
    /** Caps marker work for one batched animation update across all supplied entities. */
    public static final int MAX_EVENT_CALLBACKS_PER_BATCH_TICK = MLuaAnimationEventState.MAX_EVENT_CALLBACKS_PER_BATCH_TICK;
    public static final int MAX_EVENT_CALLBACKS_PER_ADDON_BATCH_TICK = MLuaAnimationEventState.MAX_EVENT_CALLBACKS_PER_ADDON_BATCH_TICK;
    private final int trackId;
    private final int entityId;
    private final MLuaAnimation animation;
    private final AnimationPriority priority;
    private final Object runtimeOwner;
    private final String addonOwner;

    private boolean playing;
    private boolean paused;
    private boolean looped;
    private boolean releaseWhenFaded;
    private volatile boolean released;
    private boolean emitStartEvents;
    private boolean holdOnEnd = true;
    private float speed;
    private float time;
    private float weight;
    private float targetWeight;
    private float fadeSpeed;
    private float defaultFadeSpeed;
    private boolean timedFade;

    private final MLuaAnimationEventState eventState;
    private Map<String, MLuaCFrame> computedPose;
    private Map<String, MLuaCFrame> sampledPose;

    private static final AtomicInteger NEXT_TRACK_ID = new AtomicInteger(1);

    public MLuaAnimationTrack(int entityId, MLuaAnimation animation, AnimationPriority priority) {
        this(entityId, animation, priority, null, "");
    }

    public MLuaAnimationTrack(int entityId, MLuaAnimation animation, AnimationPriority priority,
            Object runtimeOwner, String addonOwner) {
        this.trackId = NEXT_TRACK_ID.getAndIncrement();
        this.entityId = entityId;
        this.animation = Objects.requireNonNull(animation, "animation");
        this.priority = Objects.requireNonNull(priority, "priority");
        this.runtimeOwner = runtimeOwner;
        this.addonOwner = addonOwner == null ? "" : addonOwner.trim();
        this.speed = 1.0f;
        // Loading prepares a reusable track; it must not apply a pose before Play().
        this.weight = 0.0f;
        this.targetWeight = 0.0f;
        this.fadeSpeed = 2.0f;
        this.defaultFadeSpeed = 2.0f;
        this.timedFade = false;
        this.playing = false;
        this.paused = false;
        this.looped = false;
        this.releaseWhenFaded = false;
        this.emitStartEvents = false;
        this.time = 0.0f;
        this.computedPose = new HashMap<>();
        this.sampledPose = new HashMap<>();
        this.eventState = new MLuaAnimationEventState(this.trackId, this.runtimeOwner, this.addonOwner);
    }

    public int trackId() { return trackId; }
    public int entityId() { return entityId; }
    public boolean isOwnedBy(Object runtimeOwner, String addonOwner) {
        return this.runtimeOwner == runtimeOwner
            && this.addonOwner.equals(addonOwner == null ? "" : addonOwner.trim());
    }
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
    public boolean holdOnEnd() { return holdOnEnd; }
    public boolean isAlive() { return !released && (!releaseWhenFaded || playing || weight > 0.001f); }
    public boolean isReleased() { return released; }
    public boolean isFading() { return Math.abs(weight - targetWeight) > 0.001f; }
    public Map<String, MLuaCFrame> getComputedPose() { return Map.copyOf(computedPose); }
    Map<String, MLuaCFrame> sampledPoseView() { return sampledPose; }
    public MLuaCFrame getBonePose(String bone) { return computedPose.get(bone); }

    public MLuaAnimationTrack setLooped(boolean looped) { requireActive(); this.looped = looped; return this; }
    public MLuaAnimationTrack setHoldOnEnd(boolean holdOnEnd) {
        requireActive();
        this.holdOnEnd = holdOnEnd;
        return this;
    }
    public MLuaAnimationTrack setSpeed(float speed) {
        requireActive();
        this.speed = finiteNonNegative(speed, "Animation speed");
        return this;
    }
    public MLuaAnimationTrack setFadeSpeed(float speed) {
        requireActive();
        this.defaultFadeSpeed = finiteNonNegative(speed, "Animation fade speed");
        if (!timedFade) this.fadeSpeed = defaultFadeSpeed;
        return this;
    }

    public void play() {
        requireActive();
        releaseWhenFaded = false;
        playing = true;
        paused = false;
        time = 0.0f;
        emitStartEvents = true;
        fadeTo(1.0f);
    }

    public void play(float fadeTime) {
        requireActive();
        validateFadeTime(fadeTime);
        releaseWhenFaded = false;
        fadeTo(0.0f, 0.0f);
        playing = true;
        paused = false;
        time = 0.0f;
        emitStartEvents = true;
        fadeTo(1.0f, fadeTime);
    }

    public void stop() {
        requireActive();
        releaseWhenFaded = true;
        fadeTo(0.0f);
    }

    public void stop(float fadeTime) {
        requireActive();
        validateFadeTime(fadeTime);
        releaseWhenFaded = true;
        fadeTo(0.0f, fadeTime);
    }

    public void release() {
        if (this.released) return;
        this.released = true;
        releaseWhenFaded = true;
        playing = false;
        weight = 0.0f;
        targetWeight = 0.0f;
        timedFade = false;
        fadeSpeed = defaultFadeSpeed;
        computedPose.clear();
        sampledPose.clear();
        this.eventState.clear();
    }

    public void pause() {
        requireActive();
        paused = true;
    }

    public void resume() {
        requireActive();
        if (paused) {
            paused = false;
            playing = true;
        }
    }

    public void resumePlaying() {
        requireActive();
        releaseWhenFaded = false;
        playing = true;
        paused = false;
        fadeTo(1.0f);
    }

    public void fadeTo(float target) {
        requireActive();
        if (!Float.isFinite(target)) throw new IllegalArgumentException("Animation weight must be finite");
        if (target > 0.001f) releaseWhenFaded = false;
        timedFade = false;
        fadeSpeed = defaultFadeSpeed;
        targetWeight = Math.max(0.0f, Math.min(1.0f, target));
        if (targetWeight <= 0.001f) {
            playing = false;
        }
    }

    public void fadeTo(float target, float fadeTime) {
        requireActive();
        if (!Float.isFinite(target)) throw new IllegalArgumentException("Animation weight must be finite");
        validateFadeTime(fadeTime);
        if (target > 0.001f) releaseWhenFaded = false;
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
        requireActive();
        setSpeed(newSpeed);
    }

    public void setTimePosition(float t) {
        requireActive();
        if (!Float.isFinite(t)) throw new IllegalArgumentException("Animation time position must be finite");
        time = Math.max(0.0f, Math.min(t, (float) animation.getLength()));
    }

    public void addEvent(float triggerTime, LuaValue callback) {
        requireActive();
        this.eventState.add(triggerTime, (float) this.animation.getLength(), callback, this::requireActive);
    }

    public void clearEvents() { this.eventState.clear(); }

    static int activeEventCallbackCount() { return MLuaAnimationEventState.activeCallbackCount(); }
    static int activeEventCallbackCount(Object runtimeOwner, String addonOwner) {
        return MLuaAnimationEventState.activeCallbackCount(runtimeOwner, addonOwner);
    }

    private void requireActive() {
        if (this.released) {
            throw new LuaError("[MLua] AnimationTrack " + this.trackId
                + " was released after fading out or because its entity/runtime was unloaded.");
        }
    }

    public void tick(float delta) {
        this.tick(delta, new MLuaAnimationEventState.EventBudget(MAX_EVENT_CALLBACKS_PER_ENTITY_TICK), "");
    }

    void tick(float delta, MLuaAnimationEventState.EventBudget eventBudget, String addonOwner) {
        if (this.released) return;
        if (!Float.isFinite(delta) || delta < 0.0f) return;
        if (animation.getLength() <= 0.0) {
            // A one-keyframe animation at t=0 is a useful static pose. It has
            // no playback interval, so keep it at that pose until Stop() or
            // an explicit weight change releases it.
            handleFade(delta);
            updateComputedPose();
            if (playing && !paused && emitStartEvents) {
                this.eventState.fireBetween(0.0, 0.0, true, 0.0, this.looped, eventBudget, addonOwner);
                emitStartEvents = false;
            }
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
            if (!holdOnEnd) {
                releaseWhenFaded = true;
                fadeTo(0.0f);
            }
        }

        updateComputedPose();

        this.eventState.fireBetween(previousTime, eventEndTime, emitStartEvents,
            this.animation.getLength(), this.looped, eventBudget, addonOwner);
        emitStartEvents = false;
    }

    private void updateComputedPose() {
        if (weight <= 0.001f) {
            computedPose.clear();
            sampledPose.clear();
            return;
        }

        animation.sampleInto(time, sampledPose);
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
        if (fadeSpeed <= 0.0f) {
            // A zero-speed fade cannot make progress. Resolve it immediately so
            // Stop() cannot leave an inert track retained by the controller.
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

    private static float finiteNonNegative(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
        return value;
    }

    private static void validateFadeTime(float fadeTime) {
        if (!Float.isFinite(fadeTime) || fadeTime < 0.0f) {
            throw new IllegalArgumentException("Animation fade time must be finite and non-negative");
        }
    }

    /** @deprecated Animation marker ownership is managed internally by the track. */
    @Deprecated
    public static final class AnimationEvent {
        public final float triggerTime;
        public final LuaValue callback;

        public AnimationEvent(float triggerTime, LuaValue callback) {
            this.triggerTime = triggerTime;
            this.callback = callback;
        }
    }

}
