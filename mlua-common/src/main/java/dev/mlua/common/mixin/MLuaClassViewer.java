package dev.mlua.common.mixin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

/** Reflection-backed class inspection and Lua class reference generation. */
public final class MLuaClassViewer {
   private MLuaClassViewer() {
   }

   public static LuaTable inspect(Class<?> type) {
      LuaTable result = new LuaTable();
      result.set("Name", type.getName());
      result.set("SimpleName", type.getSimpleName());
      result.set("Modifiers", Modifier.toString(type.getModifiers()));
      result.set("Superclass", type.getSuperclass() == null ? LuaValue.NIL : LuaValue.valueOf(type.getSuperclass().getName()));

      LuaTable interfaces = new LuaTable();
      int index = 1;
      for (Class<?> iface : type.getInterfaces()) {
         interfaces.set(index++, iface.getName());
      }
      result.set("Interfaces", interfaces);

      LuaTable fields = new LuaTable();
      index = 1;
      for (Field field : sortedFields(type)) {
         LuaTable entry = new LuaTable();
         entry.set("Name", field.getName());
         entry.set("Type", field.getType().getName());
         entry.set("Modifiers", Modifier.toString(field.getModifiers()));
         entry.set("Static", LuaValue.valueOf(Modifier.isStatic(field.getModifiers())));
         entry.set("Final", LuaValue.valueOf(Modifier.isFinal(field.getModifiers())));
         fields.set(index++, entry);
      }
      result.set("Fields", fields);

      LuaTable methods = new LuaTable();
      index = 1;
      for (Method method : sortedMethods(type)) {
         LuaTable entry = new LuaTable();
         entry.set("Name", method.getName());
         entry.set("Descriptor", descriptor(method));
         entry.set("ReturnType", method.getReturnType().getName());
         entry.set("Modifiers", Modifier.toString(method.getModifiers()));
         entry.set("Static", LuaValue.valueOf(Modifier.isStatic(method.getModifiers())));
         entry.set("ParameterCount", method.getParameterCount());
         LuaTable parameters = new LuaTable();
         for (int i = 0; i < method.getParameterCount(); i++) {
            parameters.set(i + 1, method.getParameterTypes()[i].getName());
         }
         entry.set("Parameters", parameters);
         methods.set(index++, entry);
      }
      result.set("Methods", methods);

      LuaTable constructors = new LuaTable();
      index = 1;
      for (Constructor<?> constructor : type.getConstructors()) {
         constructors.set(index++, constructorDescriptor(constructor));
      }
      result.set("Constructors", constructors);
      return result;
   }

   public static String renderLua(Class<?> type) {
      StringBuilder out = new StringBuilder();
      String identifier = luaIdentifier(type.getSimpleName());
      out.append("-- Java class: ").append(type.getName()).append('\n');
      if (type.getSuperclass() != null) {
         out.append("-- extends ").append(type.getSuperclass().getName()).append('\n');
      }
      out.append("local Java = Minecraft:GetService(\"Java\")\n");
      out.append("local ").append(identifier).append(" = Java:LoadClass(\"")
         .append(type.getName()).append("\")\n\n");
      for (Method method : sortedMethods(type)) {
         out.append("-- ").append(Modifier.toString(method.getModifiers())).append(' ')
            .append(method.getReturnType().getName()).append(' ').append(method.getName())
            .append(' ').append(descriptor(method)).append('\n');
      }
      out.append("\nreturn ").append(identifier).append('\n');
      return out.toString();
   }

   public static String descriptor(Method method) {
      StringBuilder result = new StringBuilder("(");
      for (Class<?> parameter : method.getParameterTypes()) {
         result.append(typeDescriptor(parameter));
      }
      return result.append(')').append(typeDescriptor(method.getReturnType())).toString();
   }

   private static String constructorDescriptor(Constructor<?> constructor) {
      StringBuilder result = new StringBuilder("(");
      for (Class<?> parameter : constructor.getParameterTypes()) {
         result.append(typeDescriptor(parameter));
      }
      return result.append(')').append('V').toString();
   }

   private static String typeDescriptor(Class<?> type) {
      if (type == void.class) return "V";
      if (type == boolean.class) return "Z";
      if (type == byte.class) return "B";
      if (type == char.class) return "C";
      if (type == short.class) return "S";
      if (type == int.class) return "I";
      if (type == long.class) return "J";
      if (type == float.class) return "F";
      if (type == double.class) return "D";
      if (type.isArray()) return type.getName().replace('.', '/');
      return "L" + type.getName().replace('.', '/') + ";";
   }

   private static Method[] sortedMethods(Class<?> type) {
      Set<Method> methods = new LinkedHashSet<>(Arrays.asList(type.getMethods()));
      methods.addAll(Arrays.asList(type.getDeclaredMethods()));
      return methods.stream().sorted(Comparator.comparing(Method::getName).thenComparing(MLuaClassViewer::descriptor)).toArray(Method[]::new);
   }

   private static Field[] sortedFields(Class<?> type) {
      Set<Field> fields = new LinkedHashSet<>(Arrays.asList(type.getFields()));
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      return fields.stream().sorted(Comparator.comparing(Field::getName)).toArray(Field[]::new);
   }

   private static String luaIdentifier(String name) {
      String identifier = name.replaceAll("[^A-Za-z0-9_]", "_");
      return identifier.isEmpty() || Character.isDigit(identifier.charAt(0)) ? "Class_" + identifier : identifier;
   }
}
