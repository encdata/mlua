package dev.mlua.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/** Owns the version-tolerant access to Minecraft key mappings and options. */
final class MLuaKeyMappingAccess {
    private static final Method TYPE_GET_OR_CREATE = findMethod(InputConstants.Type.class,
        InputConstants.Key.class, false, new Class<?>[]{int.class}, "getOrCreate");
    private static final Method INPUT_GET_KEY = findMethod(InputConstants.class,
        InputConstants.Key.class, true, new Class<?>[]{String.class}, "getKey");
    private static final Method KEY_GET_TYPE = findMethod(InputConstants.Key.class,
        InputConstants.Type.class, false, new Class<?>[0], "getType");
    private static final Method KEY_GET_VALUE = findMethod(InputConstants.Key.class,
        Integer.TYPE, false, new Class<?>[0], "getValue");
    private static final Method KEY_GET_KEY = findMethod(KeyMapping.class,
        InputConstants.Key.class, false, new Class<?>[0], "getKey", "getDefaultKey");
    private static final Method KEY_SET_KEY = findMethod(KeyMapping.class,
        Void.TYPE, false, new Class<?>[]{InputConstants.Key.class}, "setKey");
    private static final Method KEY_IS_DOWN = findMethod(KeyMapping.class,
        Boolean.TYPE, false, new Class<?>[0], "isDown");
    private static final Method KEY_SET_DOWN = findSetDown();
    private static final Method KEY_CLICK = findClick();
    private static final Method KEY_CONSUME_CLICK = findMethod(KeyMapping.class,
        Boolean.TYPE, false, new Class<?>[0], "consumeClick");
    private static final Method KEY_GET_NAME = findMethod(KeyMapping.class,
        String.class, false, new Class<?>[0], "getName", "saveString");
    private static final Method KEY_GET_CATEGORY = findCategory();
    private static final Method KEY_RESET_MAPPING = findMethod(KeyMapping.class,
        Void.TYPE, true, new Class<?>[0], "resetMapping");
    private static final Field MINECRAFT_OPTIONS = findMinecraftOptionsField();
    private static final Field OPTIONS_KEY_MAPPINGS = findOptionsKeyMappingsField();

    private MLuaKeyMappingAccess() { }

    static InputConstants.Key parseKeyName(String name) {
        if (INPUT_GET_KEY != null) {
            try {
                Object parsed = INPUT_GET_KEY.invoke(null, name);
                if (parsed instanceof InputConstants.Key key && !key.equals(InputConstants.UNKNOWN)) return key;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return null;
    }

    static InputConstants.Key createKey(InputConstants.Type type, int code) {
        if (TYPE_GET_OR_CREATE != null) {
            try {
                return (InputConstants.Key) TYPE_GET_OR_CREATE.invoke(type, code);
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
        }
        return InputConstants.UNKNOWN;
    }

    static InputConstants.Type keyType(InputConstants.Key key) {
        Object value = invokeRequired(KEY_GET_TYPE, key, "read key type");
        if (value instanceof InputConstants.Type type) return type;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key type.");
    }

    static int keyValue(InputConstants.Key key) {
        Object value = invokeRequired(KEY_GET_VALUE, key, "read key code");
        if (value instanceof Number number) return number.intValue();
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key code.");
    }

    static InputConstants.Key key(KeyMapping mapping) {
        Object value = invokeRequired(KEY_GET_KEY, mapping, "read key mapping value");
        if (value instanceof InputConstants.Key key) return key;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping value.");
    }

    static void setKey(KeyMapping mapping, InputConstants.Key key) {
        if (KEY_SET_KEY == null) throw new IllegalStateException("[MLua] Failed to set key binding.");
        invokeRequired(KEY_SET_KEY, mapping, "set key binding", key);
    }

    static boolean isDown(KeyMapping mapping) {
        Object value = invokeRequired(KEY_IS_DOWN, mapping, "read key mapping state");
        if (value instanceof Boolean down) return down;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping state.");
    }

    static void setDown(KeyMapping mapping, boolean down) {
        if (KEY_SET_DOWN == null) {
            throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping press control.");
        }
        if (Modifier.isStatic(KEY_SET_DOWN.getModifiers())) invokeRequired(KEY_SET_DOWN, null, "change key mapping state", mapping, down);
        else invokeRequired(KEY_SET_DOWN, mapping, "change key mapping state", down);
    }

    static void click(KeyMapping mapping) {
        if (KEY_CLICK == null) throw new IllegalStateException("[MLua] This Minecraft version does not expose key mapping clicks.");
        if (Modifier.isStatic(KEY_CLICK.getModifiers())) invokeRequired(KEY_CLICK, null, "queue key mapping click", key(mapping));
        else invokeRequired(KEY_CLICK, mapping, "queue key mapping click");
    }

    static boolean consumeClick(KeyMapping mapping) {
        Object value = invokeRequired(KEY_CONSUME_CLICK, mapping, "consume key mapping click");
        if (value instanceof Boolean consumed) return consumed;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key click result.");
    }

    static String name(KeyMapping mapping) {
        Object value = invokeRequired(KEY_GET_NAME, mapping, "read key mapping identifier");
        if (value instanceof String name) return name;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping identifier.");
    }

    static String category(KeyMapping mapping) {
        //--version 1.20.1,1.21.1
        Object value = invokeRequired(KEY_GET_CATEGORY, mapping, "read key mapping category");
        if (value instanceof String category) return category;
        throw new IllegalStateException("[MLua] Minecraft returned an invalid key mapping category.");
        //--endversion
        //--version 26.1
        return mapping.getCategory().id().toString();
        //--endversion
    }

    static KeyMapping[] keyMappings(Minecraft minecraft) {
        Options options = requireOptions(minecraft);
        if (OPTIONS_KEY_MAPPINGS == null) {
            throw new IllegalStateException("[MLua] This Minecraft version does not expose its key mapping array.");
        }
        try {
            KeyMapping[] mappings = (KeyMapping[]) OPTIONS_KEY_MAPPINGS.get(options);
            if (mappings == null) throw new IllegalStateException("[MLua] Minecraft returned a null key mapping array.");
            return mappings;
        } catch (IllegalAccessException | ClassCastException failure) {
            throw new IllegalStateException("[MLua] Failed to read Minecraft's key mappings.", failure);
        }
    }

    static void setKeyMappings(Minecraft minecraft, KeyMapping[] mappings) {
        Options options = requireOptions(minecraft);
        if (OPTIONS_KEY_MAPPINGS == null) {
            throw new IllegalStateException("[MLua] This Minecraft version does not expose its key mapping array.");
        }
        try {
            OPTIONS_KEY_MAPPINGS.set(options, mappings);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("[MLua] Failed to update Minecraft's key mappings.", failure);
        }
    }

    static void persist(KeyMapping mapping) {
        Minecraft minecraft = MLuaMinecraftClient.instance();
        Options options = requireOptions(minecraft);
        //--version 1.20.1,1.21.1
        options.setKey(mapping, key(mapping));
        //--endversion
        //--version 26.1
        mapping.setKey(key(mapping));
        //--endversion
        options.save();
    }

    static void resetMappings() {
        if (KEY_RESET_MAPPING == null) throw new IllegalStateException("[MLua] Failed to reset key mappings.");
        invokeRequired(KEY_RESET_MAPPING, null, "reset key mappings");
    }

    static Options requireOptions(Minecraft minecraft) {
        Options options = null;
        if (MINECRAFT_OPTIONS != null) {
            try {
                options = (Options) MINECRAFT_OPTIONS.get(minecraft);
            } catch (IllegalAccessException | RuntimeException ignored) { }
        }
        if (options == null) {
            throw new IllegalStateException("[MLua] Minecraft options are not available; key mappings cannot be read or changed yet.");
        }
        return options;
    }

    private static Method findSetDown() {
        Method method = findMethod(KeyMapping.class, Void.TYPE, false, new Class<?>[]{Boolean.TYPE}, "setDown");
        if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
            new Class<?>[]{KeyMapping.class, Boolean.TYPE}, "setDown");
        if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, false, new Class<?>[]{Boolean.TYPE}, "set");
        if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
            new Class<?>[]{KeyMapping.class, Boolean.TYPE}, "set");
        return method;
    }

    private static Method findClick() {
        Method method = findMethod(KeyMapping.class, Void.TYPE, false, new Class<?>[0], "click");
        if (method == null) method = findMethod(KeyMapping.class, Void.TYPE, true,
            new Class<?>[]{InputConstants.Key.class}, "click");
        return method;
    }

    private static Method findCategory() {
        //--version 1.20.1,1.21.1
        return findMethod(KeyMapping.class, String.class, false, new Class<?>[0], "getCategory");
        //--endversion
        //--version 26.1
        return findMethod(KeyMapping.class, KeyMapping.Category.class, false, new Class<?>[0], "getCategory");
        //--endversion
    }

    private static Field findMinecraftOptionsField() {
        return findField(Minecraft.class, Options.class, "options");
    }

    private static Field findOptionsKeyMappingsField() {
        return findField(Options.class, KeyMapping[].class, "keyMappings");
    }

    private static Field findField(Class<?> owner, Class<?> fieldType, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                if (field.getType() == fieldType && makeAccessible(field)) return field;
            } catch (NoSuchFieldException ignored) { }
        }
        for (Field field : owner.getDeclaredFields()) {
            if (field.getType() == fieldType && makeAccessible(field)) return field;
        }
        return null;
    }

    private static boolean makeAccessible(Field field) {
        try {
            return field.trySetAccessible() || Modifier.isPublic(field.getModifiers());
        } catch (RuntimeException inaccessible) {
            return Modifier.isPublic(field.getModifiers());
        }
    }

    private static Method findMethod(Class<?> owner, Class<?> returnType, boolean isStatic,
            Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                Method method = owner.getMethod(name, parameterTypes);
                if (matches(method, returnType, isStatic, parameterTypes)) {
                    Method accessible = makeAccessible(method);
                    if (accessible != null) return accessible;
                }
            } catch (NoSuchMethodException ignored) { }
        }
        Method candidate = null;
        for (Method method : owner.getMethods()) {
            if (!matches(method, returnType, isStatic, parameterTypes)) continue;
            if (candidate != null && !candidate.equals(method)) return null;
            candidate = method;
        }
        return candidate == null ? null : makeAccessible(candidate);
    }

    private static boolean matches(Method method, Class<?> returnType, boolean isStatic, Class<?>[] parameterTypes) {
        return Modifier.isStatic(method.getModifiers()) == isStatic
            && method.getReturnType() == returnType
            && Arrays.equals(method.getParameterTypes(), parameterTypes)
            && !method.isBridge() && !method.isSynthetic();
    }

    private static Method makeAccessible(Method method) {
        try {
            if (!method.trySetAccessible() && !Modifier.isPublic(method.getModifiers())) return null;
            return method;
        } catch (RuntimeException inaccessible) {
            return Modifier.isPublic(method.getModifiers()) ? method : null;
        }
    }

    private static Object invokeRequired(Method method, Object receiver, String operation, Object... arguments) {
        if (method == null) {
            throw new IllegalStateException("[MLua] This Minecraft version does not expose the method needed to "
                + operation + ".");
        }
        try {
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("[MLua] Failed to " + operation + ".", failure);
        }
    }
}
