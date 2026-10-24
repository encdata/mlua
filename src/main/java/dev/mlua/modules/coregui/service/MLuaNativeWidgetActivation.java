package dev.mlua.modules.coregui.service;

import dev.mlua.minecraft.ui.element.GuiButton;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.luaj.vm2.LuaError;

/** Resolves and invokes the native action behind a script replacement button. */
final class MLuaNativeWidgetActivation {
    private static final ClassValue<Activation> ACTIVATIONS = new ClassValue<>() {
        @Override protected Activation computeValue(Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                Method press = declaredMethod(current, "onPress");
                if (press != null) return activation(press, false);
                Method click = declaredMethod(current, "onClick", double.class, double.class);
                if (click != null) return activation(click, true);
            }
            return Activation.NONE;
        }
    };

    private MLuaNativeWidgetActivation() { }

    static void activate(GuiButton button, double mouseX, double mouseY) {
        Object widget;
        synchronized (MLuaGuiState.REPLACEMENT_ACTIONS) {
            widget = MLuaGuiState.REPLACEMENT_ACTIONS.get(button);
        }
        if (widget == null) return;

        Activation activation = ACTIVATIONS.get(widget.getClass());
        if (activation.method() == null) return;
        if (activation.failure() != null) throw invocationError(widget, activation, activation.failure());
        try {
            if (activation.withMousePosition()) activation.method().invoke(widget, mouseX, mouseY);
            else activation.method().invoke(widget);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Throwable cause = failure instanceof InvocationTargetException invocation
                && invocation.getCause() != null ? invocation.getCause() : failure;
            throw invocationError(widget, activation, cause);
        }
    }

    private static Method declaredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Activation activation(Method method, boolean withMousePosition) {
        try {
            method.setAccessible(true);
            return new Activation(method, withMousePosition, null);
        } catch (RuntimeException failure) {
            return new Activation(method, withMousePosition, failure);
        }
    }

    private static LuaError invocationError(Object widget, Activation activation, Throwable cause) {
        return new LuaError(new RuntimeException("[MLua] Could not forward activation to native widget '"
            + widget.getClass().getName() + "' through " + activation.method().getName() + "()", cause));
    }

    private record Activation(Method method, boolean withMousePosition, RuntimeException failure) {
        private static final Activation NONE = new Activation(null, false, null);
    }
}
