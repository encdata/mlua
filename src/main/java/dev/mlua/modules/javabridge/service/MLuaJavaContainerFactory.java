package dev.mlua.modules.javabridge.service;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.luaj.vm2.LuaError;

/** Builds Java container types accepted by the Lua-to-Java conversion rules. */
final class MLuaJavaContainerFactory {
    private MLuaJavaContainerFactory() {}

    static Map<Object, Object> newMap(Class<?> targetType, Map<Object, Object> values) {
        try {
            if (targetType.isInstance(values)) return values;
            Map<Object, Object> result = null;
            if (ConcurrentNavigableMap.class.isAssignableFrom(targetType)
                && targetType.isAssignableFrom(ConcurrentSkipListMap.class)) {
                result = new ConcurrentSkipListMap<>(values);
            } else if ((NavigableMap.class.isAssignableFrom(targetType) || SortedMap.class.isAssignableFrom(targetType))
                && targetType.isAssignableFrom(java.util.TreeMap.class)) {
                result = new java.util.TreeMap<>(values);
            } else if (ConcurrentMap.class.isAssignableFrom(targetType)
                && targetType.isAssignableFrom(ConcurrentHashMap.class)) {
                result = new ConcurrentHashMap<>(values);
            } else if (targetType.isAssignableFrom(LinkedHashMap.class)) {
                result = new LinkedHashMap<>(values);
            } else if (!targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> map = (Map<Object, Object>) targetType.getConstructor().newInstance();
                map.putAll(values);
                result = map;
            }
            if (result != null && result.size() != values.size()) {
                throw new LuaError("[MLua] Java map type " + targetType.getName()
                    + " collapsed distinct converted keys; refusing to discard Lua table entries");
            }
            return result;
        } catch (ClassCastException incompatibleKeys) {
            throw new LuaError("[MLua] Java map type " + targetType.getName()
                + " cannot order or accept the Lua table keys.");
        } catch (NullPointerException nullEntry) {
            throw new LuaError("[MLua] Java map type " + targetType.getName()
                + " does not accept null keys or values.");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    static void putConvertedMapEntry(Map<Object, Object> target, Object key, Object value) {
        if (target.containsKey(key)) {
            throw new LuaError("[MLua] Java map conversion produced the same key more than once ('"
                + String.valueOf(key) + "'); refusing to overwrite an entry");
        }
        target.put(key, value);
    }

    static boolean supportsCollectionType(Class<?> type) {
        if (!Iterable.class.isAssignableFrom(type)) return false;
        if (type.isAssignableFrom(ArrayList.class)
            || (NavigableSet.class.isAssignableFrom(type) || SortedSet.class.isAssignableFrom(type))
                && type.isAssignableFrom(TreeSet.class)
            || Set.class.isAssignableFrom(type) && type.isAssignableFrom(LinkedHashSet.class)
            || (Queue.class.isAssignableFrom(type) || java.util.Deque.class.isAssignableFrom(type))
                && type.isAssignableFrom(LinkedList.class)) return true;
        return Collection.class.isAssignableFrom(type)
            && !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
            && hasPublicNoArgumentConstructor(type);
    }

    static boolean supportsMapType(Class<?> type) {
        return type.isAssignableFrom(LinkedHashMap.class)
            || (ConcurrentNavigableMap.class.isAssignableFrom(type)
                && type.isAssignableFrom(ConcurrentSkipListMap.class))
            || ((NavigableMap.class.isAssignableFrom(type) || SortedMap.class.isAssignableFrom(type))
                && type.isAssignableFrom(java.util.TreeMap.class))
            || (ConcurrentMap.class.isAssignableFrom(type)
                && type.isAssignableFrom(ConcurrentHashMap.class))
            || !type.isInterface() && !Modifier.isAbstract(type.getModifiers())
                && hasPublicNoArgumentConstructor(type);
    }

    static Collection<Object> newCollection(Class<?> targetType, List<Object> values) {
        if (targetType.isAssignableFrom(ArrayList.class)) return new ArrayList<>(values);
        if (targetType == ConcurrentSkipListSet.class) {
            try {
                return new ConcurrentSkipListSet<>(values);
            } catch (ClassCastException incompatibleValues) {
                throw new LuaError("[MLua] Java sorted collection " + targetType.getName()
                    + " requires mutually comparable values");
            } catch (NullPointerException nullValue) {
                throw new LuaError("[MLua] Java sorted collection " + targetType.getName()
                    + " does not accept null elements");
            }
        }
        if ((NavigableSet.class.isAssignableFrom(targetType) || SortedSet.class.isAssignableFrom(targetType))
            && targetType.isAssignableFrom(TreeSet.class)) {
            try {
                return new TreeSet<>(values);
            } catch (ClassCastException incompatibleValues) {
                throw new LuaError("[MLua] Java sorted collection " + targetType.getName()
                    + " requires mutually comparable values");
            } catch (NullPointerException nullValue) {
                throw new LuaError("[MLua] Java sorted collection " + targetType.getName()
                    + " does not accept null elements");
            }
        }
        if (Set.class.isAssignableFrom(targetType)
            && targetType.isAssignableFrom(LinkedHashSet.class)) return new LinkedHashSet<>(values);
        if ((Queue.class.isAssignableFrom(targetType) || java.util.Deque.class.isAssignableFrom(targetType))
            && targetType.isAssignableFrom(LinkedList.class)) return new LinkedList<>(values);
        if (Collection.class.isAssignableFrom(targetType)
            && !targetType.isInterface() && !Modifier.isAbstract(targetType.getModifiers())) {
            try {
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) targetType.getConstructor().newInstance();
                collection.addAll(values);
                return collection;
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean hasPublicNoArgumentConstructor(Class<?> type) {
        try {
            type.getConstructor();
            return true;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }
}
