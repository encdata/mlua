package dev.mlua.minecraft.binding;

import dev.mlua.common.enums.AnimationPriority;
import dev.mlua.core.classes.MLuaClassRegistry;
import dev.mlua.math.MLuaCFrame;
import dev.mlua.minecraft.animation.MLuaAnimation;
import dev.mlua.minecraft.animation.MLuaAnimationTrack;
import dev.mlua.module.MLuaRegistration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MLuaAnimationBindingsRegistrationTest {
    @Test
    void animationAndTrackInstancesAttachToTheirRegisteringRuntime() {
        MLuaRegistration first = registration();
        MLuaRegistration second = registration();
        MLuaAnimationBindings.ensureRegisteredClasses(first);
        MLuaAnimationBindings.ensureRegisteredClasses(second);

        LuaTable firstLibrary = MLuaAnimationBindings.createAnimationTable(first);
        LuaTable secondLibrary = MLuaAnimationBindings.createAnimationTable(second);
        LuaTable firstAnimationClass = first.classTable("Animation");
        LuaTable secondAnimationClass = second.classTable("Animation");
        assertNotSame(firstAnimationClass, secondAnimationClass);
        assertSame(firstAnimationClass, firstLibrary.get("Animation"));
        assertSame(secondAnimationClass, secondLibrary.get("Animation"));

        MLuaAnimation animation = new MLuaAnimation(List.of(new MLuaAnimation.Keyframe(0, Map.<String, MLuaCFrame>of())));
        LuaTable firstAnimation = MLuaAnimationBindings.wrapAnimation(animation, first);
        LuaTable secondAnimation = MLuaAnimationBindings.wrapAnimation(animation, second);
        assertTrue(firstAnimation.get("__type").tojstring().equals("Animation"));
        assertTrue(secondAnimation.get("__type").tojstring().equals("Animation"));

        MLuaAnimationTrack track = new MLuaAnimationTrack(1, animation, AnimationPriority.Idle);
        LuaTable firstTrack = MLuaAnimationBindings.wrapTrack(track, first);
        LuaTable secondTrack = MLuaAnimationBindings.wrapTrack(track, second);
        assertTrue(firstTrack.get("__type").tojstring().equals("AnimationTrack"));
        assertTrue(secondTrack.get("__type").tojstring().equals("AnimationTrack"));
        assertTrue(firstAnimation.get("IsA").call(firstAnimation, LuaValue.valueOf("Instance")).toboolean());
    }

    private static MLuaRegistration registration() {
        MLuaClassRegistry classes = new MLuaClassRegistry();
        MLuaRegistration registration = new MLuaRegistration() {
            @Override public LuaTable registerClass(String name, String parentName) {
                return classes.register(name, parentName);
            }
            @Override public LuaTable registerClass(String name, String parentName, String description) {
                return classes.register(name, parentName, description);
            }
            @Override public LuaTable registerClassTable(String name, LuaTable table, String parentName) {
                return classes.register(name, table, parentName);
            }
            @Override public LuaTable registerClassTable(String name, LuaTable table, String parentName, String description) {
                return classes.register(name, table, parentName, description);
            }
            @Override public LuaTable classTable(String name) { return classes.get(name); }
            @Override public java.util.Set<String> classNames() { return classes.names(); }
            @Override public LuaTable attachClassInstance(LuaTable instance, String name, String parentName) {
                return classes.attachInstance(instance, name, parentName);
            }
        };
        registration.registerInstanceBaseClass("Instance", null,
            "Base class for animation binding test instances.");
        return registration;
    }
}
