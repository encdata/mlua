package dev.mlua.minecraft.animation;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.core.MLuaLogger;
import dev.mlua.math.MLuaCFrame;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.luaj.vm2.LuaValue;

public final class MLuaAnimationTrack {
    private final int trackId;
    private final int entityId;
    private final MLuaAnimation animation;
    private final AnimationPriority priority;

    private boolean playing;
    private boolean paused;
    private boolean looped;
    private float speed;
    private float time;
    private float weight;
    private float targetWeight;
    private float fadeSpeed;

    private final List<AnimationEvent> events;
    private float lastEventCheckTime;

    private Map<String, MLuaCFrame> computedPose;

    private static int NEXT_TRACK_ID = 1;

    public MLuaAnimationTrack(int entityId, MLuaAnimation animation, AnimationPriority priority) {
        this.trackId = NEXT_TRACK_ID++;
        this.entityId = entityId;
        this.animation = animation;
        this.priority = priority;
        this.speed = 1.0f;
        this.weight = 1.0f;
        this.targetWeight = 1.0f;
        this.fadeSpeed = 2.0f;
        this.playing = false;
        this.paused = false;
        this.looped = false;
        this.time = 0.0f;
        this.computedPose = new HashMap<>();
        this.events = new ArrayList<>();
        this.lastEventCheckTime = 0.0f;
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
    public Map<String, MLuaCFrame> getComputedPose() { return computedPose; }
    public MLuaCFrame getBonePose(String bone) { return computedPose.get(bone); }

    public MLuaAnimationTrack setLooped(boolean looped) { this.looped = looped; return this; }
    public MLuaAnimationTrack setSpeed(float speed) { this.speed = Math.max(0.0f, speed); return this; }
    public MLuaAnimationTrack setFadeSpeed(float speed) { this.fadeSpeed = Math.max(0.1f, speed); return this; }

    public void play() {
        playing = true;
        paused = false;
        time = 0.0f;
        lastEventCheckTime = 0.0f;
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
        targetWeight = Math.max(0.0f, Math.min(1.0f, target));
        if (targetWeight <= 0.001f) {
            playing = false;
        }
    }

    public void adjustSpeed(float newSpeed) {
        speed = Math.max(0.0f, newSpeed);
    }

    public void setTimePosition(float t) {
        time = Math.max(0.0f, Math.min(t, (float) animation.getLength()));
        lastEventCheckTime = time;
    }

    public void addEvent(float triggerTime, LuaValue callback) {
        events.add(new AnimationEvent(triggerTime, callback));
        events.sort((a, b) -> Float.compare(a.triggerTime, b.triggerTime));
    }

    public void tick(float delta) {
        if (!playing || paused) {
            handleFade(delta);
            updateComputedPose();
            return;
        }

        float previousTime = time;

        handleFade(delta);

        if (weight <= 0.001f) {
            computedPose.clear();
            return;
        }

        time += delta * speed;

        if (looped && time > animation.getLength()) {
            time = (float) (time % animation.getLength());
            if (time < 0.01f && previousTime < animation.getLength()) {
                lastEventCheckTime = 0.0f;
            }
        }

        if (!looped && time >= animation.getLength()) {
            time = (float) animation.getLength();
            playing = false;
        }

        updateComputedPose();

        checkEvents(previousTime, time);
    }

    private void updateComputedPose() {
        if (weight <= 0.001f || animation.getLength() <= 0) {
            computedPose.clear();
            return;
        }

        Map<String, MLuaCFrame> rawPose = animation.sample(time);

        computedPose.clear();
        if (weight >= 0.999f) {
            computedPose.putAll(rawPose);
        } else {
            MLuaCFrame identity = MLuaCFrame.angles(0, 0, 0);
            for (Map.Entry<String, MLuaCFrame> entry : rawPose.entrySet()) {
                computedPose.put(entry.getKey(), identity.lerp(entry.getValue(), weight));
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

    private void checkEvents(float fromTime, float toTime) {
        for (AnimationEvent event : events) {
            if (event.triggerTime > fromTime && event.triggerTime <= toTime ||
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

    public static final class AnimationEvent {
        public final float triggerTime;
        public final LuaValue callback;

        public AnimationEvent(float triggerTime, LuaValue callback) {
            this.triggerTime = triggerTime;
            this.callback = callback;
        }
    }
}
