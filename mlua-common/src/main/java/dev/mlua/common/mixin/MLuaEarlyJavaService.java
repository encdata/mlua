package dev.mlua.common.mixin;

import java.util.function.Function;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

/** Java class-inspection subset exposed while mixin scripts run before game services exist. */
public final class MLuaEarlyJavaService {
   private MLuaEarlyJavaService() { }

   public static LuaTable create() {
      LuaTable table = new LuaTable();
      LuaValue inspect = function(args -> MLuaClassViewer.inspect(resolveClass(args, table, 1)));
      LuaValue view = function(args -> LuaValue.valueOf(MLuaClassViewer.renderLua(resolveClass(args, table, 1))));
      table.set("Inspect", inspect);
      table.set("ViewClass", view);
      table.set("ClassViewer", view);
      table.set("GetMethods", function(args -> MLuaClassViewer.inspect(resolveClass(args, table, 1)).get("Methods")));
      table.set("GetFields", function(args -> MLuaClassViewer.inspect(resolveClass(args, table, 1)).get("Fields")));
      return table;
   }

   private static Class<?> resolveClass(Varargs args, LuaTable service, int logicalIndex) {
      int argumentIndex = args.arg1() == service ? logicalIndex + 1 : logicalIndex;
      String canonicalName = args.checkjstring(argumentIndex);
      if (canonicalName.endsWith("[]")) {
         Class<?> component = resolveClassName(canonicalName.substring(0, canonicalName.length() - 2));
         return java.lang.reflect.Array.newInstance(component, 0).getClass();
      }
      return resolveClassName(canonicalName);
   }

   private static Class<?> resolveClassName(String canonicalName) {
      Class<?> primitive = switch (canonicalName) {
         case "boolean" -> boolean.class;
         case "byte" -> byte.class;
         case "char" -> char.class;
         case "short" -> short.class;
         case "int" -> int.class;
         case "long" -> long.class;
         case "float" -> float.class;
         case "double" -> double.class;
         default -> null;
      };
      if (primitive != null) return primitive;
      String runtimeName = MLuaMappingResolvers.current().mapClassName(canonicalName);
      ClassLoader[] loaders = {
         Thread.currentThread().getContextClassLoader(),
         MLuaEarlyJavaService.class.getClassLoader(),
         ClassLoader.getSystemClassLoader()
      };
      for (String name : new String[]{runtimeName, canonicalName}) {
         for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
               return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
         }
      }
      throw new LuaError("[MLua] Java class not found: " + canonicalName);
   }

   private static LuaValue function(Function<Varargs, LuaValue> action) {
      return new VarArgFunction() {
         @Override
         public Varargs invoke(Varargs args) {
            try {
               return action.apply(args);
            } catch (LuaError error) {
               throw error;
            } catch (Exception error) {
               throw new LuaError(new RuntimeException("[MLua] Early Java inspection failed", error));
            }
         }
      };
   }
}
