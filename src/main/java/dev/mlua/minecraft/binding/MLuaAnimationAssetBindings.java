package dev.mlua.minecraft.binding;

import dev.mlua.module.MLuaRegistration;
import dev.mlua.core.classes.MLuaClassMethods;
import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.modules.enums.MLuaEnumValue;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

/** Lua bindings for immutable animation assets and their cached Lua wrappers. */
final class MLuaAnimationAssetBindings {
    private static final Map<MLuaRegistration, Map<MLuaAnimation, WeakReference<LuaTable>>> ANIMATION_WRAPPERS = new WeakHashMap<>();

    private MLuaAnimationAssetBindings() { }

    static LuaTable createAnimationTable(MLuaRegistration registration) {
        java.util.Objects.requireNonNull(registration, "registration");
        LuaTable animTable = new LuaTable();
        LuaTable animationClass = registration.classTable("Animation");
        LuaTable trackClass = registration.classTable("AnimationTrack");
        LuaTable animatorClass = registration.classTable("Animator");
        if (animationClass == null || trackClass == null || animatorClass == null) {
            throw new IllegalStateException("Animation classes must be registered before creating the Animation library table");
        }
        animTable.set("Animation", animationClass);
        animTable.set("AnimationTrack", trackClass);
        animTable.set("Animator", animatorClass);
        animTable.set("new", animationClass.get("new"));

        animTable.set("AnimationPriority", MLuaEnumValue.enumConstants(AnimationPriority.class, null));
        animTable.set("EasingStyle", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingStyle.class, null));
        animTable.set("EasingDirection", MLuaEnumValue.enumConstants(dev.mlua.common.enums.EasingDirection.class, null));

        return animTable;
    }

    static LuaTable wrapAnimation(MLuaAnimation animation, MLuaRegistration registration) {
        synchronized (ANIMATION_WRAPPERS) {
            Map<MLuaAnimation, WeakReference<LuaTable>> wrappers = ANIMATION_WRAPPERS.computeIfAbsent(
                registration, ignored -> new WeakHashMap<>());
            wrappers.entrySet().removeIf(entry -> entry.getValue().get() == null);
            WeakReference<LuaTable> existingReference = wrappers.get(animation);
            LuaTable existing = existingReference == null ? null : existingReference.get();
            if (existing != null && registration.isInstanceOf(existing, "Animation")) return existing;
            LuaTable created = createAnimationInstance(animation, registration);
            wrappers.put(animation, new WeakReference<>(created));
            return created;
        }
    }

    private static LuaTable createAnimationInstance(MLuaAnimation animation, MLuaRegistration registration) {
        LuaTable table = createClassInstance(registration, "Animation", new LuaTable());
        table.rawset("__mlua_animation", LuaValue.userdataOf(animation));
        MLuaClassMethods.setInstanceMethod(table, "GetLength", new ZeroArgFunction() {
            public LuaValue call() {
                return LuaValue.valueOf(animation.getLength());
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetKeyframes", new ZeroArgFunction() {
            public LuaValue call() {
                LuaTable kfTable = new LuaTable();
                int i = 1;
                for (MLuaAnimation.Keyframe kf : animation.getKeyframes()) {
                    LuaTable kfEntry = new LuaTable();
                    kfEntry.set("Time", LuaValue.valueOf(kf.time));
                    kfEntry.set("EasingStyle", MLuaEnumValue.forJava(kf.easingStyle));
                    kfEntry.set("EasingDirection", MLuaEnumValue.forJava(kf.easingDirection));
                    LuaTable dataTable = new LuaTable();
                    for (Map.Entry<String, MLuaCFrame> entry : kf.data.entrySet()) {
                        dataTable.set(entry.getKey(), entry.getValue().toLua());
                    }
                    kfEntry.set("Data", dataTable);
                    kfTable.set(i++, kfEntry);
                }
                return kfTable;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetKeyframeCount", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(animation.getKeyframeCount()); }
        });
        MLuaClassMethods.setInstanceMethod(table, "GetBoneNames", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable names = new LuaTable();
                int index = 1;
                for (String name : animation.getBoneNames()) names.set(index++, LuaValue.valueOf(name));
                return names;
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "HasBone", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(animation.hasBone(args.checkjstring(2)));
            }
        });
        MLuaClassMethods.setInstanceMethod(table, "Sample", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                float time = MLuaLuaArguments.finiteNonNegativeFloat(args.arg(2), "Animation:Sample", "time");
                LuaTable pose = new LuaTable();
                animation.sample(time).forEach((bone, transform) -> pose.set(bone, transform.toLua()));
                return pose;
            }
        });
        return table;
    }

    private static LuaTable createClassInstance(MLuaRegistration registration, String className, LuaTable fields) {
        if (registration == null) throw new IllegalStateException("Lua registration is required for " + className + " instances");
        return registration.createClassInstance(className, fields);
    }
}
