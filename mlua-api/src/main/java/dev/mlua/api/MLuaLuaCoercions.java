package dev.mlua.api;

import java.util.ArrayList;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Collection;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public final class MLuaLuaCoercions {
   private static final int MAX_LUA_TABLE_DEPTH = 64;
   private static final Map<Class<?>, Adapter<?>> ADAPTERS = new ConcurrentHashMap<>();

   private MLuaLuaCoercions() {
   }

   /** Converts one addon-owned Java value type to and from Lua values using exact type matches. */
   public interface Adapter<T> {
      Class<T> type();
      T fromLua(LuaValue value);
      LuaValue toLua(T value);
   }

   /** Removes an adapter only if it is still the registration owned by this handle. */
   public interface AdapterRegistration extends AutoCloseable {
      @Override
      void close();
   }

   /** Registers one exact Java type adapter for reflective service arguments and results. */
   public static <T> AdapterRegistration registerAdapter(Adapter<T> adapter) {
      if (adapter == null) throw new NullPointerException("adapter");
      Class<T> type = adapter.type();
      if (type == null) throw new NullPointerException("adapter.type");
      if (type == Object.class || type.isPrimitive() || type.isArray()
         || type.isInterface() || Modifier.isAbstract(type.getModifiers())
         || LuaValue.class.isAssignableFrom(type) || Number.class.isAssignableFrom(type)
         || CharSequence.class.isAssignableFrom(type) || type == Boolean.class
         || type == Character.class || type.isEnum() || Optional.class.isAssignableFrom(type)
         || Map.class.isAssignableFrom(type) || Iterable.class.isAssignableFrom(type)) {
         throw new IllegalArgumentException("Lua adapters require a concrete addon value type outside MLua's built-in scalar, Lua, and collection types");
      }
      Adapter<?> existing = ADAPTERS.putIfAbsent(type, adapter);
      if (existing != null) throw new IllegalStateException("A Lua adapter is already registered for " + type.getName());
      return () -> ADAPTERS.remove(type, adapter);
   }

   public static Map<String, Object> mapFromLua(LuaTable table) {
      if (table == null) throw new NullPointerException("table");
      return mapFromLua(table, new IdentityHashMap<>(), 0);
   }

   public static Object javaValue(LuaValue value) {
      return javaValue(value, new IdentityHashMap<>(), 0);
   }

   /** Rejects cyclic or excessively nested tables before reflective argument conversion begins. */
   public static void validateLuaValue(LuaValue value) {
      validateLuaValue(value, new IdentityHashMap<>(), 0);
   }

   private static void validateLuaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> active, int depth) {
      if (value == null || !value.istable()) return;
      LuaTable table = value.checktable();
      enterTable(table, active, depth);
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            validateLuaValue(key, active, depth + 1);
            validateLuaValue(next.arg(2), active, depth + 1);
         }
      } finally {
         active.remove(table);
      }
   }

   private static Object javaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> active, int depth) {
      if (!value.istable()) {
         if (value.isboolean()) {
            return value.toboolean();
         } else if (value.isnumber()) {
            return value.todouble();
         } else {
            return value.isstring() ? value.tojstring() : null;
         }
      } else {
         LuaTable table = value.checktable();
         enterTable(table, active, depth);
         try {
            if (isDenseArray(table)) {
               ArrayList<Object> list = new ArrayList<>();
               for (int i = 1; ; i++) {
                  LuaValue item = table.rawget(i);
                  if (item.isnil()) break;
                  list.add(javaValue(item, active, depth + 1));
               }
               return list;
            }
            return mapValuesFromLua(table, active, depth + 1);
         } finally {
            active.remove(table);
         }
      }
   }

   private static Map<String, Object> mapFromLua(LuaTable table,
         IdentityHashMap<LuaTable, Boolean> active, int depth) {
      enterTable(table, active, depth);
      try {
         Map<String, Object> values = new LinkedHashMap<>();
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return values;
            if (!key.isstring()) {
               throw new IllegalArgumentException("Lua table for a Java map must use string keys; got " + key.typename());
            }
            values.put(key.tojstring(), javaValue(next.arg(2), active, depth + 1));
         }
      } finally {
         active.remove(table);
      }
   }

   private static void enterTable(LuaTable table, IdentityHashMap<LuaTable, Boolean> active, int depth) {
      if (depth >= MAX_LUA_TABLE_DEPTH) {
         throw new IllegalArgumentException("Lua table nesting exceeds the maximum depth of " + MAX_LUA_TABLE_DEPTH);
      }
      if (active.put(table, Boolean.TRUE) != null) {
         throw new IllegalArgumentException("Cyclic Lua table cannot be converted to a Java value");
      }
   }

   private static boolean isDenseArray(LuaTable table) {
      int entryCount = 0;
      int largestIndex = 0;
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs entry = table.next(key);
         key = entry.arg1();
         if (key.isnil()) break;
         if (!key.isnumber()) return false;
         double numericKey = key.todouble();
         if (!Double.isFinite(numericKey) || numericKey < 1 || numericKey > Integer.MAX_VALUE
            || numericKey != Math.rint(numericKey)) return false;
         int index = key.toint();
         entryCount++;
         largestIndex = Math.max(largestIndex, index);
      }
      return entryCount == largestIndex;
   }

   /**
    * Converts a Lua argument to a declared Java parameter type. A Lua sequence table
    * converts to an array or collection; declare {@code List<T>} or {@code Map<K,V>}
    * to preserve nested element types. For {@code Object}, dense positive integer
    * keys are treated as a Java list and other tables as maps (an empty table is a list).
    */
   public static Object javaValue(LuaValue value, Class<?> targetType) {
      if (targetType == null) throw new NullPointerException("targetType");
      Object proxiedJavaObject = proxiedJavaObject(value);
      if (proxiedJavaObject != null) return javaValue(proxiedJavaObject, (Type) targetType);
      if (targetType == Optional.class) {
         return value == null || value.isnil()
            ? Optional.empty() : Optional.ofNullable(javaValue(value, Object.class));
      }
      if (value == null || value.isnil()) {
         if (targetType.isPrimitive()) throw conversionError(value, targetType);
         return null;
      }
      if (targetType == Object.class) return value.isuserdata() ? value.touserdata() : javaValue(value);
      if (targetType.isInstance(value)) return value;
      if (value.isuserdata(targetType)) return value.touserdata(targetType);
      Adapter<?> adapter = ADAPTERS.get(targetType);
      if (adapter != null) return fromLuaWithAdapter(value, targetType, adapter);
      if (targetType == String.class) {
         if (value.isstring()) return value.tojstring();
         throw conversionError(value, targetType);
      }
      if (targetType == boolean.class || targetType == Boolean.class) {
         if (value.isboolean()) return value.toboolean();
         throw conversionError(value, targetType);
      }
      if (targetType == char.class || targetType == Character.class) {
         if (value.isstring() && value.tojstring().length() == 1) return value.tojstring().charAt(0);
         throw conversionError(value, targetType);
      }
      if (targetType == Number.class && value.isnumber()) return value.todouble();
      if (Number.class.isAssignableFrom(box(targetType)) && value.isnumber()) {
         return numberValue(value.todouble(), box(targetType), value);
      }
      if (targetType.isEnum() && value.isstring()) {
         String name = value.tojstring();
         for (Object constant : targetType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) return constant;
         }
         throw new IllegalArgumentException("Unknown " + targetType.getSimpleName() + " value: " + name);
      }
      if (value.istable() && Map.class.isAssignableFrom(targetType)) {
         Object converted = mapValuesFromLua(value.checktable());
         return mapForTarget(targetType, (Map<?, ?>) converted);
      }
      if (value.istable() && (Iterable.class.isAssignableFrom(targetType) || targetType.isArray())) {
         Class<?> componentType = targetType.isArray() ? targetType.getComponentType() : Object.class;
         List<Object> converted = listFromLua(value.checktable(), componentType);
         if (targetType.isArray()) {
            Object array = Array.newInstance(targetType.getComponentType(), converted.size());
            for (int i = 0; i < converted.size(); i++) {
               Array.set(array, i, converted.get(i));
            }
            return array;
         }
         Collection<?> collection = collectionForTarget(targetType, converted);
         if (collection != null) return collection;
      }
      throw conversionError(value, targetType);
   }

   /** Preserves generic collection element types when converting reflective service arguments. */
   public static Object javaValue(LuaValue value, Type targetType) {
      Object proxiedJavaObject = proxiedJavaObject(value);
      if (proxiedJavaObject != null) return javaValue(proxiedJavaObject, targetType);
      if (targetType instanceof Class<?> classType) return javaValue(value, classType);
      if (targetType instanceof GenericArrayType genericArray) {
         if (value == null || value.isnil()) return null;
         if (!value.istable()) throw conversionError(value, rawClass(genericArray));
         Type componentType = genericArray.getGenericComponentType();
         List<Object> converted = listFromLua(value.checktable(), componentType);
         Class<?> componentClass = rawClass(componentType);
         Object array = Array.newInstance(componentClass, converted.size());
         for (int i = 0; i < converted.size(); i++) Array.set(array, i, converted.get(i));
         return array;
      }
      if (!(targetType instanceof ParameterizedType parameterized)
         || !(parameterized.getRawType() instanceof Class<?> rawType)) {
         return javaValue(value, Object.class);
      }
      if (Optional.class.isAssignableFrom(rawType)) {
         return value == null || value.isnil()
            ? Optional.empty() : Optional.ofNullable(javaValue(value, typeArgument(parameterized, 0)));
      }
      if (value == null || value.isnil()) return javaValue(value, rawType);
      if (Iterable.class.isAssignableFrom(rawType) && value.istable()) {
         Type elementType = typeArgument(parameterized, 0);
         List<Object> converted = listFromLua(value.checktable(), elementType);
         Collection<?> collection = collectionForTarget(rawType, converted);
         if (collection != null) return collection;
      }
      if (Map.class.isAssignableFrom(rawType) && value.istable()) {
         Type keyType = typeArgument(parameterized, 0);
         Type valueType = typeArgument(parameterized, 1);
         Map<Object, Object> converted = mapFromLua(value.checktable(), keyType, valueType);
         return mapForTarget(rawType, converted);
      }
      return javaValue(value, rawType);
   }

   private static Object javaValue(Object source, Type targetType) {
      Class<?> targetClass = rawClass(targetType);
      if (Optional.class.isAssignableFrom(targetClass)) {
         if (source instanceof Optional<?> optional) {
            return optional.isPresent()
               ? Optional.ofNullable(javaValue(optional.get(), typeArgument(targetType, 0)))
               : Optional.empty();
         }
         return source == null ? Optional.empty() : Optional.ofNullable(javaValue(source, typeArgument(targetType, 0)));
      }
      if (source == null) return null;
      if (Map.class.isAssignableFrom(targetClass) && source instanceof Map<?, ?> sourceMap) {
         Map<Object, Object> converted = new LinkedHashMap<>();
         Type keyType = typeArgument(targetType, 0);
         Type valueType = typeArgument(targetType, 1);
         sourceMap.forEach((key, value) -> converted.put(javaValue(key, keyType), javaValue(value, valueType)));
         try {
            return mapForTarget(targetClass, converted);
         } catch (IllegalArgumentException unsupportedTarget) {
            if (targetClass.isInstance(sourceMap)) return sourceMap;
            throw unsupportedTarget;
         }
      }
      if (targetClass.isArray()) {
         List<Object> sourceValues = javaSequence(source, targetClass.getComponentType());
         Type componentType = targetType instanceof GenericArrayType genericArray
            ? genericArray.getGenericComponentType() : targetClass.getComponentType();
         Object array = Array.newInstance(targetClass.getComponentType(), sourceValues.size());
         for (int index = 0; index < sourceValues.size(); index++) {
            Array.set(array, index, javaValue(sourceValues.get(index), componentType));
         }
         return array;
      }
      if (Iterable.class.isAssignableFrom(targetClass)
         && (source instanceof Iterable<?> || source.getClass().isArray())) {
         List<Object> converted = new ArrayList<>();
         for (Object item : javaSequence(source, typeArgument(targetType, 0))) {
            converted.add(javaValue(item, typeArgument(targetType, 0)));
         }
         if (Set.class.isAssignableFrom(targetClass)) {
            Set<Object> values = new LinkedHashSet<>(converted);
            if (targetClass.isInstance(values)) return values;
         }
         if (targetClass.isInstance(converted)) return converted;
         if (Collection.class.isAssignableFrom(targetClass)
            && !targetClass.isInterface() && !Modifier.isAbstract(targetClass.getModifiers())) {
            try {
               @SuppressWarnings("unchecked")
               Collection<Object> result = (Collection<Object>) targetClass.getConstructor().newInstance();
               result.addAll(converted);
               return result;
            } catch (ReflectiveOperationException | ClassCastException ignored) {
               // Report the unsupported target type below.
            }
         }
         if (targetClass.isInstance(source)) return source;
         throw new IllegalArgumentException("Cannot convert Java iterable to " + targetClass.getTypeName());
      }
      if (targetClass.isInstance(source) || box(targetClass).isInstance(source)) return source;
      return javaValue(CoerceJavaToLua.coerce(source), targetClass);
   }

   private static List<Object> javaSequence(Object source, Type elementType) {
      List<Object> values = new ArrayList<>();
      if (source instanceof Iterable<?> iterable) {
         iterable.forEach(values::add);
      } else if (source != null && source.getClass().isArray()) {
         for (int index = 0; index < Array.getLength(source); index++) values.add(Array.get(source, index));
      } else {
         throw new IllegalArgumentException("Cannot convert Java value to a sequence of " + rawClass(elementType).getTypeName());
      }
      return values;
   }

   private static Object proxiedJavaObject(LuaValue value) {
      if (!value.istable()) return null;
      LuaValue proxy = value.rawget("__javaObject");
      return proxy.isuserdata() ? proxy.touserdata() : null;
   }

   private static Class<?> rawClass(Type type) {
      if (type instanceof Class<?> classType) return classType;
      if (type instanceof ParameterizedType parameterized
         && parameterized.getRawType() instanceof Class<?> classType) return classType;
      if (type instanceof GenericArrayType genericArray) {
         return Array.newInstance(rawClass(genericArray.getGenericComponentType()), 0).getClass();
      }
      return Object.class;
   }

   private static Map<?, ?> mapForTarget(Class<?> targetType, Map<?, ?> values) {
      if (targetType.isInstance(values)) return values;
      if (!targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
         try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> result = (Map<Object, Object>) targetType.getConstructor().newInstance();
            result.putAll(values);
            return result;
         } catch (ReflectiveOperationException | ClassCastException ignored) {
            // Report a type-focused error below rather than leaking reflection details.
         }
      }
      throw new IllegalArgumentException("Cannot convert Lua table to Java map type " + targetType.getTypeName());
   }

   private static Collection<?> collectionForTarget(Class<?> targetType, Collection<?> values) {
      if (targetType.isInstance(values)) return values;
      if (Set.class.isAssignableFrom(targetType)) {
         Set<Object> set = new LinkedHashSet<>(values);
         if (targetType.isInstance(set)) return set;
         set = new HashSet<>(values);
         if (targetType.isInstance(set)) return set;
      }
      if (Queue.class.isAssignableFrom(targetType) || Deque.class.isAssignableFrom(targetType)) {
         Collection<Object> queue = new LinkedList<>(values);
         if (targetType.isInstance(queue)) return queue;
      }
      if (!targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())
         && Collection.class.isAssignableFrom(targetType)) {
         try {
            @SuppressWarnings("unchecked")
            Collection<Object> result = (Collection<Object>) targetType.getConstructor().newInstance();
            result.addAll(values);
            return result;
         } catch (ReflectiveOperationException | ClassCastException ignored) {
            // The target needs a supported constructor or collection interface.
         }
      }
      return null;
   }

   /**
    * Converts Java values returned by reflective services. Java arrays and
    * {@link Iterable} values become 1-based Lua sequence tables, maps become Lua
    * tables with converted keys, and nested values are converted recursively.
    */
   public static LuaValue toLuaValue(Object value) {
      return toLuaValue(value, new IdentityHashMap<>());
   }

   private static LuaValue toLuaValue(Object value, IdentityHashMap<Object, Boolean> active) {
      if (value == null) {
         return LuaValue.NIL;
      } else if (value instanceof LuaValue luaValue) {
         return luaValue;
      } else if (hasAdapter(value.getClass())) {
         return toLuaRegisteredAdapter(value);
      } else if (value instanceof Boolean bool) {
         return LuaValue.valueOf(bool);
      } else if (value instanceof Integer integer) {
         return LuaValue.valueOf(integer);
      } else if (value instanceof Long longValue) {
         return LuaValue.valueOf(longValue);
      } else if (value instanceof Short shortValue) {
         return LuaValue.valueOf(shortValue.intValue());
      } else if (value instanceof Byte byteValue) {
         return LuaValue.valueOf(byteValue.intValue());
      } else if (value instanceof Float floatValue) {
         return LuaValue.valueOf((double)floatValue);
      } else if (value instanceof Double doubleValue) {
         return LuaValue.valueOf(doubleValue);
      } else if (value instanceof String string) {
         return LuaValue.valueOf(string);
      } else if (value instanceof Enum<?> enumValue) {
         return LuaValue.valueOf(enumValue.name());
      } else if (value instanceof Optional<?> optional) {
         return optional.isPresent() ? toLuaValue(optional.get(), active) : LuaValue.NIL;
      } else if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
         if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic Java collection cannot be converted to a Lua table");
         }
         try {
            LuaTable table = new LuaTable();
            if (value instanceof Map<?, ?> map) {
               map.forEach((key, entry) -> {
                  LuaValue luaKey = toLuaValue(key, active);
                  if (luaKey.isnil()) throw new IllegalArgumentException("Java map keys cannot be null in Lua tables");
                  table.set(luaKey, toLuaValue(entry, active));
               });
            } else if (value instanceof Iterable<?> iterable) {
               int index = 1;
               for (Object entry : iterable) table.set(index++, toLuaValue(entry, active));
            } else {
               for (int index = 0; index < Array.getLength(value); index++) {
                  table.set(index + 1, toLuaValue(Array.get(value, index), active));
               }
            }
            return table;
         } finally {
            active.remove(value);
         }
      } else {
         throw new IllegalArgumentException("No Lua conversion is registered for Java type "
            + value.getClass().getName() + "; register an MLuaLuaCoercions adapter or return a supported value");
      }
   }

   @SuppressWarnings("unchecked")
   private static LuaValue toLuaWithAdapter(Object value, Adapter<?> adapter) {
      LuaValue converted = ((Adapter<Object>) adapter).toLua(value);
      if (converted == null) throw new IllegalStateException("Lua adapter returned null for " + value.getClass().getName());
      return converted;
   }

   private static boolean hasAdapter(Class<?> type) {
      return ADAPTERS.containsKey(type);
   }

   private static LuaValue toLuaRegisteredAdapter(Object value) {
      Adapter<?> adapter = ADAPTERS.get(value.getClass());
      if (adapter == null) {
         throw new IllegalStateException("Lua adapter for Java type " + value.getClass().getName()
            + " was removed while converting a service result");
      }
      return toLuaWithAdapter(value, adapter);
   }

   @SuppressWarnings("unchecked")
   private static Object fromLuaWithAdapter(LuaValue value, Class<?> targetType, Adapter<?> adapter) {
      Object converted = ((Adapter<Object>) adapter).fromLua(value);
      if (converted != null && !targetType.isInstance(converted)) {
         throw new IllegalStateException("Lua adapter for " + targetType.getName()
            + " returned incompatible value " + converted.getClass().getName());
      }
      return converted;
   }

   private static Object numberValue(double value, Class<?> targetType, LuaValue source) {
      if (targetType == Double.class) return value;
      if (targetType == Float.class) {
         if (Double.isFinite(value) && Math.abs(value) > Float.MAX_VALUE) throw conversionError(source, targetType);
         return (float) value;
      }
      if (!Double.isFinite(value)) throw conversionError(source, targetType);
      if (value != Math.rint(value)) throw conversionError(source, targetType);
      if (targetType == Long.class && value >= Long.MIN_VALUE && value < 0x1.0p63) return (long) value;
      if (targetType == Integer.class && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
      if (targetType == Short.class && value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) return (short) value;
      if (targetType == Byte.class && value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) return (byte) value;
      throw conversionError(source, targetType);
   }

   private static Class<?> box(Class<?> type) {
      if (!type.isPrimitive()) return type;
      if (type == int.class) return Integer.class;
      if (type == long.class) return Long.class;
      if (type == short.class) return Short.class;
      if (type == byte.class) return Byte.class;
      if (type == float.class) return Float.class;
      if (type == double.class) return Double.class;
      if (type == boolean.class) return Boolean.class;
      if (type == char.class) return Character.class;
      return type;
   }

   private static List<Object> listFromLua(LuaTable table, Class<?> componentType) {
      return listFromLua(table, (Type) componentType);
   }

   private static List<Object> listFromLua(LuaTable table, Type componentType) {
      List<Object> values = new ArrayList<>();
      for (int index = 1; ; index++) {
         LuaValue value = table.rawget(index);
         if (value.isnil()) break;
         values.add(javaValue(value, componentType));
      }
      LuaValue key = LuaValue.NIL;
      int entryCount = 0;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         if (!key.isnumber() || !Double.isFinite(key.todouble()) || key.todouble() < 1
            || key.todouble() > Integer.MAX_VALUE || key.todouble() != Math.rint(key.todouble())) {
            throw new IllegalArgumentException("Lua table for a Java list must use consecutive positive integer keys");
         }
         entryCount++;
      }
      if (entryCount != values.size()) {
         throw new IllegalArgumentException("Lua table for a Java list must not contain gaps");
      }
      return values;
   }

   private static Map<Object, Object> mapFromLua(LuaTable table, Type keyType, Type valueType) {
      Map<Object, Object> values = new LinkedHashMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) return values;
         values.put(javaValue(key, keyType), javaValue(next.arg(2), valueType));
      }
   }

   private static Map<Object, Object> mapValuesFromLua(LuaTable table,
         IdentityHashMap<LuaTable, Boolean> active, int depth) {
      Map<Object, Object> values = new LinkedHashMap<>();
      LuaValue key = LuaValue.NIL;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) return values;
         values.put(javaValue(key, active, depth), javaValue(next.arg(2), active, depth));
      }
   }

   private static Type typeArgument(Type type, int index) {
      if (!(type instanceof ParameterizedType parameterized)
         || index >= parameterized.getActualTypeArguments().length) return Object.class;
      Type argument = parameterized.getActualTypeArguments()[index];
      if (argument instanceof WildcardType wildcard && wildcard.getUpperBounds().length > 0) {
         return wildcard.getUpperBounds()[0];
      }
      return argument;
   }

   private static IllegalArgumentException conversionError(LuaValue value, Class<?> targetType) {
      return new IllegalArgumentException("Cannot convert Lua " + (value == null ? "nil" : value.typename())
         + " to Java " + targetType.getTypeName());
   }
}
