package dev.mlua.common.mixin;

import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

public final class MLuaMixinBridge {
   public static final Object NO_RESULT = new Object();
   public static final Object CANCEL_CALL = new Object();

   private MLuaMixinBridge() {
   }

   public static void notify(String id, Object self, Object... args) {
      MLuaMixinCallbackRegistry.invokeMulti(id, self, args);
   }

   public static boolean allow(String id, Object self, Object... args) {
      Varargs result = MLuaMixinCallbackRegistry.invokeMulti(id, self, args);
      if (result.narg() == 0 || result.arg1().isnil()) return true;
      if (!result.arg1().isboolean()) {
         throw new IllegalStateException("[MLua] Mixin callback '" + id + "' must return a boolean or nil");
      }
      return result.arg1().toboolean();
   }

   public static Object override(String id, Class<?> returnType, Object self, Object... args) {
      Varargs result = MLuaMixinCallbackRegistry.invokeMulti(id, self, args);
      if (result.narg() == 0) {
         return NO_RESULT;
      }

      LuaValue value = result.arg1();
      if (value.isnil()) {
         return NO_RESULT;
      }

      return coerce(value, returnType);
   }

   /** Returns NO_RESULT to execute the original call; any other result replaces it. */
   public static Object redirect(String id, Class<?> returnType, Object self, Object... args) {
      Varargs result = MLuaMixinCallbackRegistry.invokeMulti(id, self, args);
      if (result.narg() == 0 || result.arg1().isnil()) return NO_RESULT;
      if (returnType == void.class) return CANCEL_CALL;
      return coerce(result.arg1(), returnType);
   }

   private static Object coerce(LuaValue value, Class<?> returnType) {
      Class<?> targetType = returnType.isPrimitive() ? box(returnType) : returnType;
      return CoerceLuaToJava.coerce(value, targetType);
   }

   private static Class<?> box(Class<?> type) {
      if (type == boolean.class) {
         return Boolean.class;
      } else if (type == byte.class) {
         return Byte.class;
      } else if (type == char.class) {
         return Character.class;
      } else if (type == short.class) {
         return Short.class;
      } else if (type == int.class) {
         return Integer.class;
      } else if (type == long.class) {
         return Long.class;
      } else if (type == float.class) {
         return Float.class;
      } else if (type == double.class) {
         return Double.class;
      } else {
         return type;
      }
   }
}
