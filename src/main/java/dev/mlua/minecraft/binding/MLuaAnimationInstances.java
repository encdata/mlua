package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.core.MLuaContext;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Compatibility facade for creating animation, animator, and track Lua wrappers. */
final class MLuaAnimationInstances {

    private MLuaAnimationInstances() { }

    static LuaTable createAnimationTable(MLuaRegistration registration) {
        return MLuaAnimationAssetBindings.createAnimationTable(registration);
    }

    static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
        return MLuaAnimationAssetBindings.wrapAnimation(animation, registration);
    }

    /** Creates the Java-style per-entity animation controller exposed by Entity:GetAnimator(). */
    static LuaTable wrapAnimator(LuaTable entity, MLuaRegistration registration) {
        return MLuaAnimatorBindings.wrapAnimator(entity, registration);
    }

    static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration) {
        return MLuaAnimationTrackBindings.wrapTrack(track, registration);
    }

    static LuaTable wrapTrack(MLuaAnimationTrack track, MLuaRegistration registration, LuaTable entity) {
        return MLuaAnimationTrackBindings.wrapTrack(track, registration, entity);
    }

    static MLuaContext requireAnimationOwner(MLuaRegistration registration, String method) {
        return MLuaAnimationTrackBindings.requireAnimationOwner(registration, method);
    }

}
