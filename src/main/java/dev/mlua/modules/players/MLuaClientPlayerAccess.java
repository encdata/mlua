package dev.mlua.modules.players;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Version-tolerant access to client player state, with reflection resolved once per runtime class. */
public final class MLuaClientPlayerAccess {
    private static final ClassValue<Member> PLAYER_ACCESSORS = new ClassValue<>() {
        @Override
        protected Member computeValue(Class<?> type) {
            return member(type, "player", "getPlayer");
        }
    };
    private static final ClassValue<Member> LEVEL_ACCESSORS = new ClassValue<>() {
        @Override
        protected Member computeValue(Class<?> type) {
            return member(type, "level", "getLevel");
        }
    };
    private static final ClassValue<Method> PLAYERS_METHODS = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                return type.getMethod("players");
            } catch (NoSuchMethodException error) {
                throw new IllegalStateException("Client level does not expose its player list", error);
            }
        }
    };

    private MLuaClientPlayerAccess() {}

    public static Object localPlayer() throws ReflectiveOperationException {
        Object minecraft = minecraft();
        return PLAYER_ACCESSORS.get(minecraft.getClass()).read(minecraft);
    }

    public static Object level() throws ReflectiveOperationException {
        Object minecraft = minecraft();
        return LEVEL_ACCESSORS.get(minecraft.getClass()).read(minecraft);
    }

    public static List<?> players() throws ReflectiveOperationException {
        Object level = level();
        if (level == null) return List.of();
        Object players = PLAYERS_METHODS.get(level.getClass()).invoke(level);
        if (players instanceof List<?> list) return list;
        throw new IllegalStateException("Client level returned a non-list player collection");
    }

    private static Object minecraft() throws ReflectiveOperationException {
        return MinecraftInstance.GET_INSTANCE.invoke(null);
    }

    private static Member member(Class<?> type, String fieldName, String methodName) {
        try {
            return new FieldMember(type.getField(fieldName));
        } catch (NoSuchFieldException ignored) {
            // Some supported mappings expose these fields as package-private or private.
        }
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                if (field.trySetAccessible()) return new FieldMember(field);
            } catch (NoSuchFieldException ignored) {
                // Check the next superclass; Minecraft moves these fields between versions.
            }
        }
        for (String candidate : methodCandidates(methodName)) {
            try {
                return new MethodMember(type.getMethod(candidate));
            } catch (NoSuchMethodException ignored) {
                // Try the next mapped name.
            }
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(candidate);
                    if (method.trySetAccessible()) return new MethodMember(method);
                } catch (NoSuchMethodException ignored) {
                    // Check the next superclass.
                }
            }
        }
        throw new IllegalStateException("Minecraft client does not expose '" + fieldName + "' on " + type.getName());
    }

    private static String[] methodCandidates(String methodName) {
        return methodName.equals("getPlayer") ? new String[]{"getPlayer", "player"}
            : new String[]{"getLevel", "level"};
    }

    private sealed interface Member permits FieldMember, MethodMember {
        Object read(Object target) throws ReflectiveOperationException;
    }

    private record FieldMember(Field field) implements Member {
        @Override public Object read(Object target) throws IllegalAccessException { return field.get(target); }
    }

    private record MethodMember(Method method) implements Member {
        @Override public Object read(Object target) throws ReflectiveOperationException { return method.invoke(target); }
    }
    private static final class MinecraftInstance {
        private static final Method GET_INSTANCE = findGetInstance();

        private static Method findGetInstance() {
            try {
                return Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance");
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Minecraft client singleton is unavailable", error);
            }
        }
    }
}
