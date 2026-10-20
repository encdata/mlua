package dev.mlua.ci;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/** Connects the isolated client smoke run using Minecraft's native multiplayer screen. */
final class PlayerTestServerConnector {
    private static boolean attempted;

    private PlayerTestServerConnector() { }

    static synchronized void connectIfRequested(Object minecraft) {
        if (attempted || !Boolean.parseBoolean(System.getenv("MLUA_CI_PLAYER_TEST"))) return;
        attempted = true;
        try {
            ClassLoader loader = minecraft.getClass().getClassLoader();
            Class<?> addressClass = loadFirst(loader,
                "net.minecraft.client.multiplayer.resolver.ServerAddress",
                "net.minecraft.client.multiplayer.ServerAddress");
            Class<?> dataClass = Class.forName("net.minecraft.client.multiplayer.ServerData", true, loader);
            Class<?> screenClass = Class.forName("net.minecraft.client.gui.screens.ConnectScreen", true, loader);
            Object address = parseAddress(addressClass, "127.0.0.1:25565");
            Object serverData = createServerData(dataClass);
            Object screen = readField(minecraft, "screen");
            invokeConnect(screenClass, screen, minecraft, address, serverData);
            System.out.println("MLUA_CI_PLAYER_CONNECT_REQUESTED:127.0.0.1:25565");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Could not connect the player smoke client to 127.0.0.1:25565", failure);
        }
    }

    private static Class<?> loadFirst(ClassLoader loader, String... classNames) throws ClassNotFoundException {
        ClassNotFoundException lastFailure = null;
        for (String className : classNames) {
            try {
                return Class.forName(className, true, loader);
            } catch (ClassNotFoundException failure) {
                lastFailure = failure;
            }
        }
        throw lastFailure == null ? new ClassNotFoundException("No candidate Minecraft address classes were provided")
            : lastFailure;
    }

    private static Object parseAddress(Class<?> addressClass, String value) throws ReflectiveOperationException {
        for (String methodName : new String[]{"parseString", "parse"}) {
            try {
                Method method = addressClass.getMethod(methodName, String.class);
                if (Modifier.isStatic(method.getModifiers())) return method.invoke(null, value);
            } catch (NoSuchMethodException ignored) {
                // Try the next mapped spelling used by the supported Minecraft versions.
            }
        }
        throw new NoSuchMethodException(addressClass.getName() + ".parseString(String)");
    }

    private static Object createServerData(Class<?> dataClass) throws ReflectiveOperationException {
        List<String> candidates = new ArrayList<>();
        for (Constructor<?> constructor : dataClass.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            candidates.add(constructor.toGenericString());
            if (parameters.length != 3 || parameters[0] != String.class || parameters[1] != String.class) continue;
            if (parameters[2].isEnum()) {
                Object type = enumConstant(parameters[2], "OTHER");
                return constructor.newInstance("MLua CI player test", "127.0.0.1:25565", type);
            }
            if (parameters[2] == boolean.class || parameters[2] == Boolean.class) {
                return constructor.newInstance("MLua CI player test", "127.0.0.1:25565", false);
            }
        }
        throw new NoSuchMethodException("No compatible " + dataClass.getName()
            + " constructor. Candidates: " + candidates);
    }

    private static Object enumConstant(Class<?> enumClass, String preferred) {
        Object[] constants = enumClass.getEnumConstants();
        for (Object constant : constants) {
            if (((Enum<?>) constant).name().equals(preferred)) return constant;
        }
        if (constants.length == 0) throw new IllegalStateException(enumClass.getName() + " has no values");
        return constants[0];
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Search superclass fields as versioned Minecraft classes move declarations.
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + name);
    }

    private static void invokeConnect(Class<?> connectScreen, Object screen, Object minecraft,
                                      Object address, Object serverData) throws ReflectiveOperationException {
        ReflectiveOperationException lastFailure = null;
        List<String> candidates = new ArrayList<>();
        for (Method method : connectScreen.getDeclaredMethods()) {
            if (!method.getName().equals("startConnecting") || !Modifier.isStatic(method.getModifiers())) continue;
            candidates.add(method.toGenericString());
            Object[] arguments = new Object[method.getParameterCount()];
            Class<?>[] parameters = method.getParameterTypes();
            boolean supported = true;
            for (int index = 0; index < parameters.length; index++) {
                Class<?> parameter = parameters[index];
                if (minecraft != null && parameter != Object.class && parameter.isInstance(minecraft)) arguments[index] = minecraft;
                else if (screen != null && parameter != Object.class && parameter.isInstance(screen)) arguments[index] = screen;
                else if (parameter.isInstance(address)) arguments[index] = address;
                else if (parameter.isInstance(serverData)) arguments[index] = serverData;
                else if (parameter == boolean.class || parameter == Boolean.class) arguments[index] = false;
                else if (parameter.getSimpleName().equals("TransferState")) arguments[index] = null;
                else {
                    supported = false;
                    break;
                }
            }
            if (!supported) continue;
            try {
                method.setAccessible(true);
                method.invoke(null, arguments);
                return;
            } catch (ReflectiveOperationException failure) {
                lastFailure = failure;
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new NoSuchMethodException("No compatible " + connectScreen.getName()
            + ".startConnecting method. Candidates: " + candidates);
    }
}
