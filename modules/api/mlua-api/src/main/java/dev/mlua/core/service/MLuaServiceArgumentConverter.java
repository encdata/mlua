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
         int minimumArguments = plan.minimumArgumentCount();
         if (suppliedCount < minimumArguments || suppliedCount > parameterCount) {
            throw argumentCountError(method, minimumArguments, parameterCount, suppliedCount);
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

      int fixedArgumentCount = plan.minimumArgumentCount();
      if (suppliedCount < fixedArgumentCount) {
         throw minimumArgumentCountError(method, fixedArgumentCount, suppliedCount);
      }
      MLuaLuaCoercions.LuaValueValidator validator = null;
      for (int index = 0; index < fixedArgumentCount; index++) {
         validator = validateArgument(validator, plan.genericParameterTypes()[index], args.arg(index + 1 + offset));
      }
      Type arrayType = plan.genericParameterTypes()[fixedArgumentCount];
      Type componentType = plan.varArgComponentType();
      int varArgCount = suppliedCount - fixedArgumentCount;
      for (int index = 0; index < varArgCount; index++) {
         validator = validateArgument(validator, componentType,
            args.arg(fixedArgumentCount + index + 1 + offset));
      }
      for (int index = 0; index < fixedArgumentCount; index++) {
         converted[index] = service.toJavaValue(args.arg(index + 1 + offset), plan.genericParameterTypes()[index]);
      }
      Class<?> componentClass = plan.parameterTypes()[fixedArgumentCount].getComponentType();
      Object packed = Array.newInstance(componentClass, varArgCount);
      boolean suppliedPackedArray = false;
      if (varArgCount == 1 && args.arg(fixedArgumentCount + 1 + offset).istable()) {
         try {
            Object suppliedArray = service.toJavaValue(args.arg(fixedArgumentCount + 1 + offset), arrayType);
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
         for (int index = 0; index < varArgCount; index++) {
            Array.set(packed, index,
               service.toJavaValue(args.arg(fixedArgumentCount + index + 1 + offset), componentType));
         }
      }
      converted[fixedArgumentCount] = packed;
   }

   private static MLuaLuaCoercions.LuaValueValidator validateArgument(
         MLuaLuaCoercions.LuaValueValidator validator, Type targetType, LuaValue value) {
      if (targetType instanceof Class<?> targetClass && LuaValue.class.isAssignableFrom(targetClass)) return validator;
      if (value == null || !value.istable()) return validator;
      if (validator == null) validator = new MLuaLuaCoercions.LuaValueValidator();
      validator.validate(value);
      return validator;
   }

   private static IllegalArgumentException argumentCountError(Method method, int minimum, int maximum, int supplied) {
      String requirement = minimum == maximum ? Integer.toString(minimum) : minimum + " to " + maximum;
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects "
         + requirement + " argument(s), got " + supplied);
   }

   private static IllegalArgumentException minimumArgumentCountError(Method method, int minimum, int supplied) {
      return new IllegalArgumentException("Java service method '" + method.getName() + "' expects at least "
         + minimum + " argument(s), got " + supplied);
   }
}
