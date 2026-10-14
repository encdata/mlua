package dev.mlua.common.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MLuaMixinCallbackRegistry {
   private static final Logger LOGGER = LoggerFactory.getLogger("MLuaTransformer");
   private static final Map<String, LuaValue> CALLBACKS = new ConcurrentHashMap<>();
   private static final Map<String, OriginalMethod> ORIGINALS = new ConcurrentHashMap<>();
   private static final ThreadLocal<InvocationContext> CURRENT = new ThreadLocal<>();

   private MLuaMixinCallbackRegistry() {
   }

   public static String scriptScope(String scriptPath) {
      if (scriptPath == null || scriptPath.isBlank()) return "unscoped";
      String normalizedPath = scriptPath.replace('\\', '/');
      boolean strippedScheme;
      do {
         strippedScheme = false;
         for (String scheme : new String[]{"jar:", "union:", "zip:", "file:"}) {
            if (normalizedPath.startsWith(scheme)) {
               normalizedPath = normalizedPath.substring(scheme.length());
               strippedScheme = true;
               break;
            }
         }
      } while (strippedScheme);
      while (normalizedPath.startsWith("//")) normalizedPath = normalizedPath.substring(1);
      normalizedPath = normalizedPath.replace(".jar/", ".jar!/");
      try {
         return Path.of(normalizedPath).toAbsolutePath().normalize().toString().replace('\\', '/');
      } catch (InvalidPathException ignored) {
         return normalizedPath;
      }
   }

   public static String callbackId(String scope, String targetClass, String method,
                                   String descriptor, String type, String detail) {
      return (scope == null || scope.isBlank() ? "unscoped" : scope) + ":"
         + targetClass + "#" + method + (descriptor == null ? "" : descriptor)
         + ":" + type + ":" + detail;
   }

   static void register(String id, LuaValue callback) {
      if (id == null || id.isBlank()) throw new IllegalArgumentException("Mixin callback ID must be non-empty");
      if (callback == null || !callback.isfunction()) throw new IllegalArgumentException("Mixin callback must be a function");
      LuaValue existing = CALLBACKS.putIfAbsent(id, callback);
      if (existing != null && existing != callback) {
         throw new IllegalStateException("Mixin callback ID '" + id + "' is already registered to another callback");
      }
   }

   static LuaValue get(String id) {
      return CALLBACKS.get(id);
   }

   static Map<String, LuaValue> snapshot() {
      return Map.copyOf(CALLBACKS);
   }

   public static void registerOriginal(String id, String owner, String method, String descriptor) {
      ORIGINALS.put(id, new OriginalMethod(owner, method, descriptor));
   }

   public static void unregisterOriginal(String id) {
      if (id != null) ORIGINALS.remove(id);
   }

   public static int unregisterScope(String scope) {
      if (scope == null || scope.isBlank()) return 0;
      String prefix = scope + ":";
      java.util.Set<String> ids = new java.util.HashSet<>();
      CALLBACKS.keySet().removeIf(id -> {
         if (!id.startsWith(prefix)) return false;
         ids.add(id);
         return true;
      });
      ORIGINALS.keySet().removeIf(id -> id.startsWith(prefix));
      return ids.size();
   }

   public static LuaValue callOriginalLua() {
      InvocationContext context = CURRENT.get();
      if (context == null) {
         throw new IllegalStateException("[MLua] Mixin:CallOriginal() is only valid inside a mixin callback");
      }
      Object result = invokeOriginal(context);
      return result == null ? LuaValue.NIL : toLuaValue(result);
   }

   public static void invoke(String id, Object self) {
      invokeMulti(id, self);
   }

   public static void invokeArgs(String id, Object self, Object[] args) {
      LuaValue fn = requireCallback(id);
      try {
         LuaValue[] luaArgs = new LuaValue[args.length + 1];
         luaArgs[0] = CoerceJavaToLua.coerce(self);
         for (int i = 0; i < args.length; i++) luaArgs[i + 1] = toLuaValue(args[i]);
         InvocationContext previous = pushContext(id, self, args);
         try {
            fn.invoke(luaArgs);
         } finally {
            restoreContext(previous);
         }
      } catch (Exception failure) {
         throw callbackFailure(id, "invokeArgs", failure);
      }
   }

   public static int invokeInt(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (!result.isnumber()) throw callbackFailure(id, "invokeInt", new IllegalStateException("Expected a numeric return value"));
      return result.toint();
   }

   public static long invokeLong(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (!result.isnumber()) throw callbackFailure(id, "invokeLong", new IllegalStateException("Expected a numeric return value"));
      return result.tolong();
   }

   public static float invokeFloat(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (!result.isnumber()) throw callbackFailure(id, "invokeFloat", new IllegalStateException("Expected a numeric return value"));
      return (float) result.todouble();
   }

   public static double invokeDouble(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (!result.isnumber()) throw callbackFailure(id, "invokeDouble", new IllegalStateException("Expected a numeric return value"));
      return result.todouble();
   }

   public static boolean invokeBoolean(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (!result.isboolean()) throw callbackFailure(id, "invokeBoolean", new IllegalStateException("Expected a boolean return value"));
      return result.toboolean();
   }

   public static Object invokeObj(String id, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      return result.isnil() ? null : result;
   }

   public static Object invokeObject(String id, Class<?> returnType, Object self, Object... args) {
      LuaValue result = invokeMulti(id, self, args).arg1();
      if (result.isnil()) return null;
      try {
         return CoerceLuaToJava.coerce(result, returnType);
      } catch (RuntimeException failure) {
         throw callbackFailure(id, "invokeObject for " + returnType.getName(), failure);
      }
   }

   public static Varargs invokeMulti(String id, Object self, Object... args) {
      LuaValue fn = requireCallback(id);
      try {
         LuaValue[] luaArgs = new LuaValue[args.length + 1];
         luaArgs[0] = CoerceJavaToLua.coerce(self);
         for (int i = 0; i < args.length; ++i) luaArgs[i + 1] = toLuaValue(args[i]);
         InvocationContext previous = pushContext(id, self, args);
         try {
            return fn.invoke(luaArgs);
         } finally {
            restoreContext(previous);
         }
      } catch (Exception failure) {
         throw callbackFailure(id, "invokeMulti", failure);
      }
   }

   private static LuaValue requireCallback(String id) {
      LuaValue callback = CALLBACKS.get(id);
      if (callback == null) {
         throw new IllegalStateException("[MLua] No callback registered for mixin '" + id
            + "'; registered ids: " + CALLBACKS.keySet());
      }
      return callback;
   }

   private static IllegalStateException callbackFailure(String id, String operation, Exception failure) {
      LOGGER.error("Mixin callback '{}' {} failed", id, operation, failure);
      return new IllegalStateException("[MLua] Mixin callback '" + id + "' " + operation + " failed", failure);
   }

   public static boolean hasCallback(String id) {
      return CALLBACKS.containsKey(id);
   }

   public static void clear() {
      CALLBACKS.clear();
      ORIGINALS.clear();
      CURRENT.remove();
   }

   private static InvocationContext pushContext(String id, Object self, Object[] args) {
      InvocationContext previous = CURRENT.get();
      CURRENT.set(new InvocationContext(id, self, args));
      return previous;
   }

   private static void restoreContext(InvocationContext previous) {
      if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
   }

   private static Object invokeOriginal(InvocationContext context) {
      OriginalMethod original = ORIGINALS.get(context.id());
      if (original == null) {
         throw new IllegalStateException("[MLua] No original implementation registered for mixin " + context.id());
      }
      try {
         String ownerName = original.owner().replace('/', '.');
         Class<?> owner = context.self() != null
            ? Class.forName(ownerName, false, context.self().getClass().getClassLoader())
            : Class.forName(ownerName);
         for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(original.method()) && MLuaClassViewer.descriptor(method).equals(original.descriptor())) {
               method.setAccessible(true);
               return method.invoke(context.self(), context.args());
            }
         }
         throw new NoSuchMethodException(original.method() + original.descriptor());
      } catch (InvocationTargetException exception) {
         Throwable cause = exception.getCause() == null ? exception : exception.getCause();
         throw new IllegalStateException("[MLua] Original method " + original.method()
            + " failed during Mixin:CallOriginal()", cause);
      } catch (Exception exception) {
         throw new IllegalStateException("[MLua] Failed to call original mixin method " + original.method(), exception);
      }
   }

   private record OriginalMethod(String owner, String method, String descriptor) { }
   private record InvocationContext(String id, Object self, Object[] args) { }

   private static LuaValue toLuaValue(Object value) {
      if (value == null) {
         return LuaValue.NIL;
      } else if (value instanceof Boolean bool) {
         return LuaValue.valueOf(bool);
      } else if (value instanceof Integer integer) {
         return LuaValue.valueOf(integer);
      } else if (value instanceof Long longValue) {
         return LuaValue.valueOf(longValue);
      } else if (value instanceof Float floatValue) {
         return LuaValue.valueOf((double)floatValue);
      } else if (value instanceof Double doubleValue) {
         return LuaValue.valueOf(doubleValue);
      } else if (value instanceof String string) {
         return LuaValue.valueOf(string);
      } else {
         return CoerceJavaToLua.coerce(value);
      }
   }
}
