package dev.mlua.api;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
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
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public final class MLuaLuaCoercions {
   /** Shared nesting limit for Lua/Java collection conversions across the API. */
   public static final int MAX_CONVERSION_DEPTH = 64;
   private static final long MAX_EXACT_LUA_INTEGER = 9_007_199_254_740_991L;
   /** Maximum number of table entries accepted when a Lua value is copied into Java. */
   public static final int MAX_CONVERSION_ENTRIES = 16_384;
   /** Maximum number of addon-provided exact-type converters held by the shared runtime. */
   public static final int MAX_ADAPTERS = 1_024;
   private static final Map<Class<?>, Adapter<?>> ADAPTERS = new ConcurrentHashMap<>();
   private static final LuaValue JAVA_NULL = LuaValue.userdataOf(JavaNull.INSTANCE);

   /** Sentinel used to preserve Java nulls in Lua tables, where assigning nil removes an entry. */
   public enum JavaNull { INSTANCE }

   private MLuaLuaCoercions() {
   }

   /** Returns the shared Lua value exposed as {@code Java.Null}. */
   public static LuaValue javaNullValue() {
      return JAVA_NULL;
   }

   /** Returns whether a Lua value is the shared Java-null sentinel. */
   public static boolean isJavaNull(LuaValue value) {
      return value != null && value.isuserdata(JavaNull.class);
   }

   /** Converts a Java long only when LuaJ's double-backed numbers can represent it exactly. */
   public static LuaValue toLuaLong(long value) {
      if (value < -MAX_EXACT_LUA_INTEGER || value > MAX_EXACT_LUA_INTEGER) {
         throw new IllegalArgumentException("Java long " + value
            + " is outside Lua's exact integer range (" + -MAX_EXACT_LUA_INTEGER + ".."
            + MAX_EXACT_LUA_INTEGER + "); convert it to a string before exposing it as a Lua number");
      }
      return LuaValue.valueOf(value);
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
   public static synchronized <T> AdapterRegistration registerAdapter(Adapter<T> adapter) {
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
      if (ADAPTERS.containsKey(type)) {
         throw new IllegalStateException("A Lua adapter is already registered for " + type.getName());
      }
      if (ADAPTERS.size() >= MAX_ADAPTERS) {
         throw new IllegalStateException("MLua already has the maximum of " + MAX_ADAPTERS
            + " Lua conversion adapters registered");
      }
      ADAPTERS.put(type, adapter);
      return () -> {
         synchronized (MLuaLuaCoercions.class) {
            if (ADAPTERS.get(type) == adapter) {
               ADAPTERS.remove(type);
            }
         }
      };
   }

   public static Map<String, Object> mapFromLua(LuaTable table) {
      if (table == null) throw new NullPointerException("table");
      validateLuaValue(table);
      return mapFromLua(table, new IdentityHashMap<>(), 0);
   }

   public static Object javaValue(LuaValue value) {
      validateLuaValue(value);
      if (isJavaNull(value)) return null;
      Object scopedValue = scopedJavaValue(value);
      if (scopedValue != null) return scopedValue;
      return javaValue(value, new IdentityHashMap<>(), 0);
   }

   /** Rejects cyclic, oversized, or excessively nested tables before Java conversion begins. */
   public static void validateLuaValue(LuaValue value) {
      if (value == null || !value.istable()) return;
      new LuaValueValidator().validate(value);
   }

   /** Applies the conversion limit across all values in one reflective method call. */
   public static void validateLuaValues(Iterable<? extends LuaValue> values) {
      if (values == null) throw new NullPointerException("values");
      LuaValueValidator validator = new LuaValueValidator();
      for (LuaValue value : values) validator.validate(value);
   }

   /** Reusable validation state for all Lua values passed to one Java API call. */
   public static final class LuaValueValidator {
      private final IdentityHashMap<LuaTable, Boolean> activeTables = new IdentityHashMap<>();
      private int entryCount;

      public void validate(LuaValue value) {
         MLuaLuaCoercions.validateLuaValue(value, this, 0);
      }
   }

   private static void validateLuaValue(LuaValue value, LuaValueValidator validator, int depth) {
      if (value == null || !value.istable()) return;
      LuaTable table = value.checktable();
      enterTable(table, validator.activeTables, depth);
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            if (++validator.entryCount > MAX_CONVERSION_ENTRIES) {
               throw new IllegalArgumentException("Lua value exceeds the maximum conversion size of "
                  + MAX_CONVERSION_ENTRIES + " table entries");
            }
            validateLuaValue(key, validator, depth + 1);
            validateLuaValue(next.arg(2), validator, depth + 1);
         }
      } finally {
         validator.activeTables.remove(table);
      }
   }

   private static Object javaValue(LuaValue value, IdentityHashMap<LuaTable, Boolean> active, int depth) {
      if (value == null || value.isnil() || isJavaNull(value)) return null;
      Object scopedValue = scopedJavaValue(value);
      if (scopedValue != null) return scopedValue;
      Object proxiedJavaObject = proxiedJavaObject(value);
      if (proxiedJavaObject != null) return proxiedJavaObject;
      if (value.type() == LuaValue.TBOOLEAN) return value.toboolean();
      if (value.type() == LuaValue.TNUMBER) return value.todouble();
      if (value.type() == LuaValue.TSTRING) return value.tojstring();
      if (value.type() == LuaValue.TUSERDATA) return value.touserdata();
      if (value.istable()) {
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
      throw new IllegalArgumentException("Cannot convert Lua " + value.typename() + " to a Java value");
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
            if (key.type() != LuaValue.TSTRING) {
               throw new IllegalArgumentException("Lua table for a Java map must use string keys; got " + key.typename());
            }
            values.put(key.tojstring(), javaValue(next.arg(2), active, depth + 1));
         }
      } finally {
         active.remove(table);
      }
   }

   private static void enterTable(LuaTable table, IdentityHashMap<LuaTable, Boolean> active, int depth) {
      if (depth >= MAX_CONVERSION_DEPTH) {
         throw new IllegalArgumentException("Lua table nesting exceeds the maximum depth of " + MAX_CONVERSION_DEPTH);
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
         if (key.type() != LuaValue.TNUMBER) return false;
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
    * A Java {@code null} reference and {@code Java.Null} both behave like Lua nil:
    * reference parameters receive {@code null}, Optional parameters receive empty,
    * and primitive parameters reject the value.
    */
   public static Object javaValue(LuaValue value, Class<?> targetType) {
      if (targetType == null) throw new NullPointerException("targetType");
      if (value == null || isJavaNull(value)) value = LuaValue.NIL;
      // Preserve Lua values when Java explicitly asks for one. A Java object proxy
      // is also a Lua table, but its methods and marker fields are not table data.
      if (value != null && LuaValue.class.isAssignableFrom(targetType) && targetType.isInstance(value)) {
         return value;
      }
      Object scopedValue = scopedJavaValue(value);
      if (scopedValue != null) return javaValue(scopedValue, (Type) targetType);
      Object proxiedJavaObject = proxiedJavaObject(value);
      if (proxiedJavaObject != null) return javaValue(proxiedJavaObject, (Type) targetType);
      if (!LuaValue.class.isAssignableFrom(targetType)) validateLuaValue(value);
      if (targetType == Optional.class) {
         return value == null || value.isnil()
            ? Optional.empty() : Optional.ofNullable(javaValue(value, Object.class));
      }
      if (value == null || value.isnil()) {
         if (targetType.isPrimitive()) throw conversionError(value, targetType);
         return null;
      }
      if (targetType == Object.class) return value.isuserdata() ? value.touserdata() : javaValue(value);
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
      if (targetType == null) throw new NullPointerException("targetType");
      if (targetType instanceof Class<?> classType) return javaValue(value, classType);
      Type resolvedType = resolveBoundType(targetType);
      if (resolvedType != targetType) return javaValue(value, resolvedType);
      if (value == null || isJavaNull(value)) value = LuaValue.NIL;
      Object scopedValue = scopedJavaValue(value);
      if (scopedValue != null) return javaValue(scopedValue, targetType);
      Object proxiedJavaObject = proxiedJavaObject(value);
      if (proxiedJavaObject != null) return javaValue(proxiedJavaObject, targetType);
      validateLuaValue(value);
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
      if (source == null) {
         if (targetClass.isPrimitive()) {
            throw new IllegalArgumentException("Java null cannot be converted to primitive type "
               + targetClass.getTypeName() + "; use a reference type or supply a non-null value");
         }
         return null;
      }
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

   private static Object scopedJavaValue(LuaValue value) {
      if (value == null || !value.isuserdata()) return null;
      Object userdata = value.touserdata();
      return userdata instanceof MLuaScopedJavaReference scoped ? scoped.unwrap() : null;
   }

   private static Class<?> rawClass(Type type) {
      Type resolved = resolveBoundType(type);
      if (resolved != type) return rawClass(resolved);
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
      Map<Object, Object> result = null;
      try {
         if (ConcurrentNavigableMap.class.isAssignableFrom(targetType)
            && targetType.isAssignableFrom(ConcurrentSkipListMap.class)) {
            result = new ConcurrentSkipListMap<>(values);
         } else if ((NavigableMap.class.isAssignableFrom(targetType) || SortedMap.class.isAssignableFrom(targetType))
            && targetType.isAssignableFrom(TreeMap.class)) {
            result = new TreeMap<>(values);
         } else if (ConcurrentMap.class.isAssignableFrom(targetType)
            && targetType.isAssignableFrom(ConcurrentHashMap.class)) {
            result = new ConcurrentHashMap<>(values);
         } else if (targetType.isAssignableFrom(LinkedHashMap.class)) {
            result = new LinkedHashMap<>(values);
         }
      } catch (ClassCastException incompatibleKeys) {
         throw new IllegalArgumentException("Java sorted map type " + targetType.getTypeName()
            + " requires mutually comparable keys", incompatibleKeys);
      } catch (NullPointerException nullEntry) {
         throw new IllegalArgumentException("Java map type " + targetType.getTypeName()
            + " does not accept null keys or values", nullEntry);
      }
      if (result != null) {
         if (result.size() != values.size()) {
            throw new IllegalArgumentException("Java map type " + targetType.getTypeName()
               + " collapsed distinct converted keys");
         }
         return result;
      }
      if (!targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
         try {
            @SuppressWarnings("unchecked")
            Map<Object, Object> concrete = (Map<Object, Object>) targetType.getConstructor().newInstance();
            concrete.putAll(values);
            if (concrete.size() != values.size()) {
               throw new IllegalArgumentException("Java map type " + targetType.getTypeName()
                  + " collapsed distinct converted keys");
            }
            return concrete;
         } catch (ClassCastException incompatibleKeys) {
            throw new IllegalArgumentException("Java map type " + targetType.getTypeName()
               + " cannot accept or order the converted keys", incompatibleKeys);
         } catch (NullPointerException nullEntry) {
            throw new IllegalArgumentException("Java map type " + targetType.getTypeName()
               + " does not accept null keys or values", nullEntry);
         } catch (ReflectiveOperationException ignored) {
            // Report a type-focused error below rather than leaking reflection details.
         }
      }
      throw new IllegalArgumentException("Cannot convert Lua table to Java map type " + targetType.getTypeName());
   }

   private static Collection<?> collectionForTarget(Class<?> targetType, Collection<?> values) {
      if (targetType.isInstance(values)) return values;
      if (NavigableSet.class.isAssignableFrom(targetType) || SortedSet.class.isAssignableFrom(targetType)) {
         if (targetType == ConcurrentSkipListSet.class) {
            try {
               return new ConcurrentSkipListSet<>(values);
            } catch (ClassCastException incompatibleValues) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " requires mutually comparable values", incompatibleValues);
            } catch (NullPointerException nullValue) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " does not accept null elements", nullValue);
            }
         }
         if (targetType.isAssignableFrom(TreeSet.class)) {
            try {
               return new TreeSet<>(values);
            } catch (ClassCastException incompatibleValues) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " requires mutually comparable values", incompatibleValues);
            } catch (NullPointerException nullValue) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " does not accept null elements", nullValue);
            }
         }
      }
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
         } catch (ClassCastException incompatibleValues) {
            if (NavigableSet.class.isAssignableFrom(targetType) || SortedSet.class.isAssignableFrom(targetType)) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " requires mutually comparable values", incompatibleValues);
            }
            // Report the unsupported collection type below rather than leaking implementation details.
         } catch (NullPointerException nullValue) {
            if (NavigableSet.class.isAssignableFrom(targetType) || SortedSet.class.isAssignableFrom(targetType)) {
               throw new IllegalArgumentException("Java sorted collection " + targetType.getTypeName()
                  + " does not accept null elements", nullValue);
            }
            // Report the unsupported collection type below rather than leaking implementation details.
         } catch (ReflectiveOperationException ignored) {
            // The target needs a supported constructor or collection interface.
         }
      }
      return null;
   }

   /**
    * Converts Java values returned by reflective services. Java arrays and
    * {@link Iterable} values become 1-based Lua sequence tables, maps become Lua
    * tables with converted keys, and nested values are converted recursively.
    * Nested Java nulls become {@code Java.Null}; a top-level Java null becomes Lua nil.
    * A {@link Long} or {@link java.math.BigInteger} inside LuaJ's exact integer range becomes a Lua number;
    * wider integers remain exact Java userdata. A {@link BigDecimal} becomes a Lua number only when its
    * canonical decimal round trip is unchanged; other {@link Number} implementations follow the same rule.
    * Values that would lose precision remain exact Java userdata.
    * Collection and direct Lua-table output is limited to {@link #MAX_CONVERSION_ENTRIES} entries and
    * {@value #MAX_CONVERSION_DEPTH} nested tables.
    */
   public static LuaValue toLuaValue(Object value) {
      return toLuaValue(value, new IdentityHashMap<>(), 0, new int[] {0});
   }

   private static LuaValue toLuaValue(Object value, IdentityHashMap<Object, Boolean> active,
         int depth, int[] entryCount) {
      if (value == null) {
         return depth == 0 ? LuaValue.NIL : JAVA_NULL;
      } else if (value instanceof LuaValue luaValue) {
         validateLuaOutput(luaValue, active, depth, entryCount);
         return depth > 0 && luaValue.isnil() ? JAVA_NULL : luaValue;
      } else if (value instanceof MLuaScopedJavaReference) {
         // Preserve the lifetime guard as Lua userdata. Unwrap only when a later
         // Lua-to-Java call crosses back through this shared conversion boundary.
         return LuaValue.userdataOf(value);
      } else if (hasAdapter(value.getClass())) {
         LuaValue adapted = toLuaRegisteredAdapter(value);
         validateLuaOutput(adapted, active, depth, entryCount);
         return depth > 0 && adapted.isnil() ? JAVA_NULL : adapted;
      } else if (value instanceof Boolean bool) {
         return LuaValue.valueOf(bool);
      } else if (value instanceof Integer integer) {
         return LuaValue.valueOf(integer);
      } else if (value instanceof Long longValue) {
         try {
            return toLuaLong(longValue);
         } catch (IllegalArgumentException outsideLuaIntegerRange) {
            // Preserve the exact value as Java userdata instead of failing a whole
            // reflective service call or silently rounding it through LuaJ's double.
            return LuaValue.userdataOf(longValue);
         }
      } else if (value instanceof java.math.BigInteger integer) {
         java.math.BigInteger exactLimit = java.math.BigInteger.valueOf(MAX_EXACT_LUA_INTEGER);
         if (integer.compareTo(exactLimit.negate()) >= 0 && integer.compareTo(exactLimit) <= 0) {
            return LuaValue.valueOf(integer.doubleValue());
         }
         return LuaValue.userdataOf(integer);
      } else if (value instanceof BigDecimal decimal) {
         double converted = decimal.doubleValue();
         BigDecimal roundTrip = BigDecimal.valueOf(converted);
         if (Double.isFinite(converted) && roundTrip.compareTo(decimal) == 0) {
            return LuaValue.valueOf(converted);
         }
         return LuaValue.userdataOf(decimal);
      } else if (value instanceof Short shortValue) {
         return LuaValue.valueOf(shortValue.intValue());
      } else if (value instanceof Byte byteValue) {
         return LuaValue.valueOf(byteValue.intValue());
      } else if (value instanceof Float floatValue) {
         return LuaValue.valueOf((double)floatValue);
      } else if (value instanceof Double doubleValue) {
         return LuaValue.valueOf(doubleValue);
      } else if (value instanceof Number number) {
         try {
            double converted = number.doubleValue();
            BigDecimal exactLimit = BigDecimal.valueOf(MAX_EXACT_LUA_INTEGER);
            BigDecimal decimal = new BigDecimal(number.toString());
            boolean integral = decimal.stripTrailingZeros().scale() <= 0;
            boolean integralOutOfRange = integral
               && (decimal.compareTo(exactLimit) > 0 || decimal.compareTo(exactLimit.negate()) < 0);
            if (Double.isFinite(converted)
                  && !integralOutOfRange
                  && decimal.compareTo(BigDecimal.valueOf(converted)) == 0) {
               return LuaValue.valueOf(converted);
            }
         } catch (RuntimeException ignored) {
            // Preserve custom Number implementations whose text form is not decimal.
         }
         return LuaValue.userdataOf(number);
      } else if (value instanceof String string) {
         return LuaValue.valueOf(string);
      } else if (value instanceof Character character) {
         return LuaValue.valueOf(character.toString());
      } else if (value instanceof Enum<?> enumValue) {
         return LuaValue.valueOf(enumValue.name());
      } else if (value instanceof Optional<?> optional) {
         return optional.isPresent() ? toLuaValue(optional.get(), active, depth, entryCount)
            : depth == 0 ? LuaValue.NIL : JAVA_NULL;
      } else if (value instanceof Map<?, ?> || value instanceof Iterable<?> || value.getClass().isArray()) {
         if (depth >= MAX_CONVERSION_DEPTH) {
            throw new IllegalArgumentException("Java collection nesting exceeds the maximum depth of "
               + MAX_CONVERSION_DEPTH);
         }
         if (active.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("Cyclic Java collection cannot be converted to a Lua table");
         }
         try {
            LuaTable table = new LuaTable();
            if (value instanceof Map<?, ?> map) {
               map.forEach((key, entry) -> {
                  consumeEntry(entryCount);
                  LuaValue luaKey = toLuaMapKey(key);
                  if (!table.rawget(luaKey).isnil()) {
                     throw new IllegalArgumentException("Java map keys collapse to the same Lua key: " + key);
                  }
                  table.set(luaKey, toLuaValue(entry, active, depth + 1, entryCount));
               });
            } else if (value instanceof Iterable<?> iterable) {
               int index = 1;
               for (Object entry : iterable) {
                  consumeEntry(entryCount);
                  table.set(index++, toLuaValue(entry, active, depth + 1, entryCount));
               }
            } else {
               int length = Array.getLength(value);
               requireConversionEntries((long) entryCount[0] + length);
               for (int index = 0; index < length; index++) {
                  consumeEntry(entryCount);
                  table.set(index + 1, toLuaValue(Array.get(value, index), active, depth + 1, entryCount));
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

   /** Applies the same structural limits to Lua tables returned directly or by Java adapters. */
   private static void validateLuaOutput(LuaValue value, IdentityHashMap<Object, Boolean> active,
                                         int depth, int[] entryCount) {
      if (value == null || !value.istable()) return;
      if (depth >= MAX_CONVERSION_DEPTH) {
         throw new IllegalArgumentException("Lua table nesting exceeds the maximum depth of "
            + MAX_CONVERSION_DEPTH);
      }
      LuaTable table = value.checktable();
      if (active.put(table, Boolean.TRUE) != null) {
         throw new IllegalArgumentException("Cyclic Lua table cannot be returned as a Java service result");
      }
      try {
         LuaValue key = LuaValue.NIL;
         while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) return;
            consumeEntry(entryCount);
            validateLuaOutput(key, active, depth + 1, entryCount);
            validateLuaOutput(next.arg(2), active, depth + 1, entryCount);
         }
      } finally {
         active.remove(table);
      }
   }

   /** Converts a Java map key only when its type and value can be represented in a Lua table. */
   public static LuaValue toLuaMapKey(Object key) {
      if (key == null) throw new IllegalArgumentException("Java map keys cannot be null in Lua tables");
      if (key instanceof String string) return LuaValue.valueOf(string);
      if (key instanceof Number number) {
         double converted = number.doubleValue();
         if (!Double.isFinite(converted)) {
            throw new IllegalArgumentException("Java map keys must be finite Lua numbers");
         }
         if (!(number instanceof Float || number instanceof Double)) {
            try {
               if (new BigDecimal(number.toString()).compareTo(BigDecimal.valueOf(converted)) != 0) {
                  throw new IllegalArgumentException("Java numeric map key " + number
                     + " cannot be represented exactly as a Lua number");
               }
            } catch (NumberFormatException invalidNumber) {
               throw new IllegalArgumentException("Java numeric map key " + number
                  + " cannot be represented as a Lua number", invalidNumber);
            }
         }
         return LuaValue.valueOf(converted);
      }
      if (key instanceof Boolean bool) return LuaValue.valueOf(bool);
      if (key instanceof Character character) return LuaValue.valueOf(character.toString());
      throw new IllegalArgumentException("Java map key type " + key.getClass().getName()
         + " cannot be preserved in a Lua table; use String, Number, Boolean, or Character keys");
   }

   private static void consumeEntry(int[] entryCount) {
      requireConversionEntries(++entryCount[0]);
   }

   private static void requireConversionEntries(int count) {
      requireConversionEntries((long) count);
   }

   private static void requireConversionEntries(long count) {
      if (count > MAX_CONVERSION_ENTRIES) {
         throw new IllegalArgumentException("Java value exceeds the maximum conversion size of "
            + MAX_CONVERSION_ENTRIES + " table entries");
      }
   }

   @SuppressWarnings("unchecked")
   private static LuaValue toLuaWithAdapter(Object value, Adapter<?> adapter) {
      LuaValue converted = ((Adapter<Object>) adapter).toLua(value);
      if (converted == null) throw new IllegalStateException("Lua adapter returned null for " + value.getClass().getName());
      return converted;
   }

   /** Returns whether this exact Java type has an addon conversion adapter. */
   public static boolean hasAdapter(Class<?> type) {
      return type != null && ADAPTERS.containsKey(type);
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
      LuaValue key = LuaValue.NIL;
      int entryCount = 0;
      int largestIndex = 0;
      while (true) {
         Varargs next = table.next(key);
         key = next.arg1();
         if (key.isnil()) break;
         if (!key.isnumber() || !Double.isFinite(key.todouble()) || key.todouble() < 1
            || key.todouble() > MAX_CONVERSION_ENTRIES || key.todouble() != Math.rint(key.todouble())) {
            throw new IllegalArgumentException("Lua table for a Java list must use consecutive positive integer keys"
               + " within the " + MAX_CONVERSION_ENTRIES + " entry conversion limit");
         }
         entryCount++;
         largestIndex = Math.max(largestIndex, key.toint());
      }
      if (entryCount != largestIndex) {
         throw new IllegalArgumentException("Lua table for a Java list must not contain gaps");
      }

      List<Object> values = new ArrayList<>(entryCount);
      for (int index = 1; index <= largestIndex; index++) {
         values.add(javaValue(table.rawget(index), componentType));
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
         putConvertedMapEntry(values, javaValue(key, keyType), javaValue(next.arg(2), valueType));
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
         putConvertedMapEntry(values, javaValue(key, active, depth),
            javaValue(next.arg(2), active, depth));
      }
   }

   private static void putConvertedMapEntry(Map<Object, Object> target, Object key, Object value) {
      if (target.containsKey(key)) {
         throw new IllegalArgumentException("Lua map keys collapse to the same Java key after conversion: " + key);
      }
      target.put(key, value);
   }

   private static Map<Object, Object> mapValuesFromLua(LuaTable table) {
      IdentityHashMap<LuaTable, Boolean> active = new IdentityHashMap<>();
      enterTable(table, active, 0);
      try {
         return mapValuesFromLua(table, active, 1);
      } finally {
         active.remove(table);
      }
   }

   private static Type typeArgument(Type type, int index) {
      if (!(type instanceof ParameterizedType parameterized)
         || index >= parameterized.getActualTypeArguments().length) return Object.class;
      return resolveBoundType(parameterized.getActualTypeArguments()[index]);
   }

   /** Resolves generic bounds to the narrowest declared type that Lua may safely supply. */
   private static Type resolveBoundType(Type type) {
      if (type instanceof WildcardType wildcard) {
         Type[] lowerBounds = wildcard.getLowerBounds();
         if (lowerBounds.length > 0) return lowerBounds[0];
         Type[] upperBounds = wildcard.getUpperBounds();
         if (upperBounds.length > 0 && upperBounds[0] != Object.class) return upperBounds[0];
      } else if (type instanceof TypeVariable<?> variable) {
         Type[] bounds = variable.getBounds();
         if (bounds.length > 0 && bounds[0] != Object.class) return bounds[0];
      }
      return type;
   }

   private static IllegalArgumentException conversionError(LuaValue value, Class<?> targetType) {
      return new IllegalArgumentException("Cannot convert Lua " + (value == null ? "nil" : value.typename())
         + " to Java " + targetType.getTypeName());
   }
}
