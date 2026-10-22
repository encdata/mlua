package dev.mlua.core.service;

import dev.mlua.api.MLuaLuaCoercions;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;

/** Converts and validates Lua arguments for a pre-scanned reflective service method. */
final class MLuaServiceArgumentConverter {
   private MLuaServiceArgumentConverter() { }

   static void convertArguments(MLuaReflectiveService service,
                               MLuaReflectiveServiceCatalog.DescribedMethod plan,
                               Varargs args, int offset, Object[] converted) {
      Method method = plan.method();
      int parameterCount = plan.parameterTypes().length;
      int suppliedCount = Math.max(0, args.narg() - offset);
      if (!plan.varArgs()) {
         int minimumCount = plan.minimumArgumentCount();
         if (suppliedCount < minimumCount || suppliedCount > parameterCount) {
            throw argumentCountError(method, minimumCount, parameterCount, suppliedCount);
         }
         MLuaLuaCoercions.LuaValueValidator validator = null;
         for (int index = 0; index < suppliedCount; index++) {
            validator = validateArgument(validator, plan.genericParameterTypes()[index], args.arg(index + 1 + offset));
         }
         for (int index = 0; index < parameterCount; index++) {
            LuaValue value = index < suppliedCount ? args.arg(index + 1 + offset) : LuaValue.NIL;
            converted[index] = service.toJavaValue(value, plan.genericParameterTypes()[index]);
         }
         return;
      }

      int fixedCount = plan.minimumArgumentCount();
      if (suppliedCount < fixedCount) {
         throw argumentCountError(method, fixedCount, suppliedCount, true);
      }
      MLuaLuaCoercions.LuaValueValidator validator = null;
      for (int index = 0; index < fixedCount; index++) {
         validator = validateArgument(validator, plan.genericParameterTypes()[index], args.arg(index + 1 + offset));
      }
      Type arrayType = plan.genericParameterTypes()[fixedCount];
      Type componentType = plan.varArgComponentType();
      int remaining = Math.max(0, args.narg() - offset - fixedCount);
      for (int index = 0; index < remaining; index++) {
         validator = validateArgument(validator, componentType, args.arg(fixedCount + index + 1 + offset));
      }
      for (int index = 0; index < fixedCount; index++) {
         converted[index] = service.toJavaValue(args.arg(index + 1 + offset), plan.genericParameterTypes()[index]);
      }
      Class<?> componentClass = plan.parameterTypes()[fixedCount].getComponentType();
      Object packed = Array.newInstance(componentClass, remaining);
      boolean suppliedPackedArray = false;
      if (remaining == 1 && args.arg(fixedCount + 1 + offset).istable()) {
         try {
            Object suppliedArray = service.toJavaValue(args.arg(fixedCount + 1 + offset), arrayType);
            if (suppliedArray != null) {
               packed = suppliedArray;
               suppliedPackedArray = true;
            }
         } catch (IllegalArgumentException notAnArray) {
            // A single Lua table may be one vararg value. Prefer the Java array
            // form when it converts cleanly; otherwise try it as one element.
         }
      }
      if (!suppliedPackedArray) {
         for (int index = 0; index < remaining; index++) {
            Array.set(packed, index, service.toJavaValue(args.arg(fixedCount + index + 1 + offset), componentType));
         }
      }
      converted[fixedCount] = packed;
   }

   private static MLuaLuaCoercions.LuaValueValidator validateArgument(
         MLuaLuaCoercions.LuaValueValidator validator, Type targetType, LuaValue value) {
      if (targetType instanceof Class<?> targetClass && LuaValue.class.isAssignableFrom(targetClass)) return validator;
      if (value == null || !value.istable()) return validator;
      if (validator == null) validator = new MLuaLuaCoercions.LuaValueValidator();
      validator.validate(value);
      return validator;
   }

   private static IllegalArgumentException argumentCountError(Method method, int expected, int supplied, boolean minimum) {
      String requirement = minimum ? "at least " + expected : Integer.toString(expected);
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects "
         + requirement + " argument(s), got " + supplied);
   }

   private static IllegalArgumentException argumentCountError(Method method, int minimum, int maximum, int supplied) {
      String requirement = minimum == maximum ? Integer.toString(minimum) : minimum + " to " + maximum;
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects "
         + requirement + " argument(s), got " + supplied);
   }


}
